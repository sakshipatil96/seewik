package com.seewik.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InitiativeService {
    static final String SCHEMA_VERSION = "initiative-v0.2";
    static final String ATTENDANCE_SCHEMA_VERSION = "initiative-attendance-v0.1";
    static final String LEDGER_SCHEMA_VERSION = "points-ledger-v0.3";
    static final String REWARD_POLICY_VERSION = "reward-policy-v0.2";
    static final int CODE_ATTENDANCE_POINTS = 20;
    static final int ORGANISER_COMPLETION_POINTS = 40;
    static final Duration CODE_WINDOW = Duration.ofHours(3);
    static final Duration SELF_ATTENDANCE_WINDOW = Duration.ofDays(7);
    static final double DEFAULT_RADIUS_KM = 5.0;
    static final double MAX_RADIUS_KM = 25.0;
    private static final Set<String> CATEGORIES = Set.of(
            "CLEANUP", "PLANTATION", "DONATION", "COMMUNITY_FITNESS", "OTHER_CIVIC_ACTIVITY");

    private final InitiativeGateway gateway;
    private final Clock clock;
    private final AttendanceCodeService attendanceCodes;

    @Autowired
    public InitiativeService(
            InitiativeGateway gateway,
            @Value("${seewik.attendance-code-secret:}") String attendanceCodeSecret) {
        this(gateway, Clock.systemUTC(), new AttendanceCodeService(attendanceCodeSecret));
    }

    InitiativeService(InitiativeGateway gateway) {
        this(gateway, Clock.systemUTC());
    }

    InitiativeService(InitiativeGateway gateway, Clock clock) {
        this(gateway, clock, new AttendanceCodeService("unit-test-attendance-secret-32-bytes-minimum"));
    }

    InitiativeService(InitiativeGateway gateway, Clock clock, AttendanceCodeService attendanceCodes) {
        this.gateway = gateway;
        this.clock = clock;
        this.attendanceCodes = attendanceCodes;
    }

    public InitiativeView create(String ownerUid, CreateRequest request) {
        ValidatedCreate input = validateCreate(request);
        Instant now = clock.instant();
        String initiativeId = "init_" + UUID.randomUUID().toString().replace("-", "");
        String eventId = "evt_" + hash(initiativeId + ":INITIATIVE_CREATED");
        String participationId = participationId(initiativeId, ownerUid);
        String ledgerEntryId = "pts_" + hash(initiativeId + ":INITIATIVE_CREATED:" + ownerUid);

        Map<String, Object> initiative = new LinkedHashMap<>();
        initiative.put("initiativeId", initiativeId);
        initiative.put("ownerUid", ownerUid);
        initiative.put("title", input.title());
        initiative.put("category", input.category());
        initiative.put("description", input.description());
        initiative.put("startAt", input.startAt().toString());
        initiative.put("placeName", input.placeName());
        initiative.put("latitude", input.latitude());
        initiative.put("longitude", input.longitude());
        initiative.put("needs", input.needs());
        initiative.put("status", "PUBLISHED");
        initiative.put("participantCount", 1);
        initiative.put("createdAt", now.toString());
        initiative.put("updatedAt", now.toString());
        initiative.put("schemaVersion", SCHEMA_VERSION);

        Map<String, Object> event = event(eventId, initiativeId, "INITIATIVE_CREATED", ownerUid, now);
        Map<String, Object> participation = participation(
                participationId, initiativeId, ownerUid, "ORGANISER", now);
        Map<String, Object> ledger = ledger(
                ledgerEntryId, initiativeId, eventId, ownerUid, "INITIATIVE_CREATED", now);
        Map<String, Object> saved = gateway.create(
                ownerUid, initiativeId, initiative, event, participation, ledger);
        return view(saved, 0.0, new InitiativeGateway.CitizenInitiative(
                saved, "ORGANISER", participation, 0, 0, 0));
    }

    public DiscoveryResponse discover(DiscoveryRequest request) {
        double latitude = validLatitude(request == null ? null : request.latitude());
        double longitude = validLongitude(request == null ? null : request.longitude());
        double radiusKm = request == null || request.radiusKm() == null
                ? DEFAULT_RADIUS_KM
                : request.radiusKm();
        if (!Double.isFinite(radiusKm) || radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new InitiativeException("INVALID_RADIUS", "Choose a discovery radius from 0 to 25 km");
        }
        Instant now = clock.instant();
        List<InitiativeView> nearby = new ArrayList<>();
        for (InitiativeGateway.CitizenInitiative item : gateway.listPublished(request.ownerUid())) {
            Map<String, Object> initiative = item.initiative();
            Instant startAt = instant(initiative.get("startAt"));
            if (startAt == null || now.isAfter(startAt.plus(CODE_WINDOW))) continue;
            String status = String.valueOf(initiative.get("status"));
            if (!"PUBLISHED".equals(status) && !"COMPLETED".equals(status)) continue;
            double distanceKm = haversineKm(
                    latitude,
                    longitude,
                    number(initiative.get("latitude")),
                    number(initiative.get("longitude")));
            if (distanceKm <= radiusKm) nearby.add(view(initiative, distanceKm, item));
        }
        nearby.sort(Comparator.comparingDouble(InitiativeView::distanceKm)
                .thenComparing(InitiativeView::startAt));
        return new DiscoveryResponse("NEARBY_INITIATIVES", radiusKm, nearby.size(), List.copyOf(nearby));
    }

    public JoinResponse join(String ownerUid, String initiativeId) {
        String cleanId = clean(initiativeId, 80, "INVALID_INITIATIVE_ID", "Initiative ID is required");
        InitiativeGateway.JoinResult result = gateway.join(ownerUid, cleanId, clock.instant());
        return new JoinResponse(
                result.alreadyJoined() ? "ALREADY_JOINED" : "JOINED",
                cleanId,
                integer(result.initiative().get("participantCount")),
                result.alreadyJoined());
    }

    public MyInitiativesResponse mine(String ownerUid) {
        List<InitiativeView> activities = gateway.listForCitizen(ownerUid).stream()
                .map(item -> view(item.initiative(), 0.0, item))
                .sorted(Comparator.comparing(InitiativeView::startAt).reversed())
                .toList();
        return new MyInitiativesResponse("MY_INITIATIVES", activities.size(), activities);
    }

    public TransitionResponse cancel(String ownerUid, String initiativeId, CancelRequest request) {
        String cleanId = clean(initiativeId, 80, "INVALID_INITIATIVE_ID", "Initiative ID is required");
        String reason = clean(
                request == null ? null : request.reason(),
                300,
                "CANCELLATION_REASON_REQUIRED",
                "Add a short cancellation reason");
        return transition(ownerUid, cleanId, "CANCELLED", reason);
    }

    public TransitionResponse complete(String ownerUid, String initiativeId) {
        String cleanId = clean(initiativeId, 80, "INVALID_INITIATIVE_ID", "Initiative ID is required");
        return transition(ownerUid, cleanId, "COMPLETED", "");
    }

    private TransitionResponse transition(
            String ownerUid, String initiativeId, String targetStatus, String cancellationReason) {
        InitiativeGateway.TransitionResult result = gateway.transition(
                ownerUid, initiativeId, targetStatus, cancellationReason, clock.instant());
        return new TransitionResponse(
                result.idempotentReplay() ? "TRANSITION_ALREADY_RECORDED" : "TRANSITION_RECORDED",
                initiativeId,
                targetStatus,
                result.idempotentReplay(),
                result.pointsAwarded());
    }

    public AttendanceCodeResponse attendanceCode(String ownerUid, String initiativeId) {
        String cleanId = clean(initiativeId, 80, "INVALID_INITIATIVE_ID", "Initiative ID is required");
        InitiativeGateway.AttendanceContext context = gateway.attendanceContext(ownerUid, cleanId);
        if (!"ORGANISER".equals(context.participation().get("role"))) {
            throw new InitiativeException("INITIATIVE_FORBIDDEN", "Only the organiser can view this attendance code");
        }
        Instant now = clock.instant();
        AttendanceWindow window = codeWindow(context.initiative());
        if ("CANCELLED".equals(context.initiative().get("status"))) {
            throw new InitiativeException("ATTENDANCE_UNAVAILABLE", "Attendance is unavailable for a cancelled activity");
        }
        if (now.isBefore(window.startAt()) || now.isAfter(window.endsAt())) {
            throw new InitiativeException("ATTENDANCE_CODE_WINDOW_CLOSED", "The organiser code is outside its three-hour window");
        }
        String code = attendanceCodes.codeFor(cleanId, now);
        Instant slotEndsAt = Instant.ofEpochSecond((attendanceCodes.slot(now) + 1) * AttendanceCodeService.SLOT_SECONDS);
        Instant rotatesAt = slotEndsAt.isBefore(window.endsAt()) ? slotEndsAt : window.endsAt();
        return new AttendanceCodeResponse(
                "ATTENDANCE_CODE_ACTIVE",
                cleanId,
                code,
                rotatesAt.toString(),
                window.endsAt().toString(),
                ATTENDANCE_SCHEMA_VERSION);
    }

    public AttendanceResponse selfAttend(String ownerUid, String initiativeId) {
        String cleanId = clean(initiativeId, 80, "INVALID_INITIATIVE_ID", "Initiative ID is required");
        return attendanceResponse(gateway.recordSelfAttendance(ownerUid, cleanId, clock.instant()));
    }

    public AttendanceResponse codeAttend(String ownerUid, String initiativeId, AttendanceCodeRequest request) {
        String cleanId = clean(initiativeId, 80, "INVALID_INITIATIVE_ID", "Initiative ID is required");
        String submitted = request == null || request.code() == null ? "" : request.code().strip();
        Instant now = clock.instant();
        InitiativeGateway.AttendanceResult result = gateway.recordCodeAttendance(
                ownerUid,
                cleanId,
                now,
                attendanceCodes.slot(now),
                attendanceCodes.matches(cleanId, now, submitted));
        if ("ATTENDANCE_CODE_INVALID".equals(result.status())) {
            throw new InitiativeException("ATTENDANCE_CODE_INVALID", "The attendance code is incorrect");
        }
        if ("ATTENDANCE_RATE_LIMITED".equals(result.status())) {
            throw new InitiativeException("ATTENDANCE_RATE_LIMITED", "Too many incorrect codes; wait for the next code");
        }
        return attendanceResponse(result);
    }

    private static AttendanceResponse attendanceResponse(InitiativeGateway.AttendanceResult result) {
        Map<String, Object> participation = result.participation();
        return new AttendanceResponse(
                result.idempotentReplay() ? "ATTENDANCE_ALREADY_RECORDED" : "ATTENDANCE_RECORDED",
                String.valueOf(result.initiative().get("initiativeId")),
                String.valueOf(participation.getOrDefault("attendanceStatus", "")),
                String.valueOf(participation.getOrDefault("attendanceBasis", "")),
                String.valueOf(participation.getOrDefault("attendanceReportedAt", "")),
                result.joinerCount(),
                result.selfAttendanceCount(),
                result.codeAttendanceCount(),
                result.idempotentReplay(),
                result.participantPointsAwarded(),
                result.organiserPointsAwarded(),
                ATTENDANCE_SCHEMA_VERSION,
                REWARD_POLICY_VERSION);
    }

    static Map<String, Object> event(
            String eventId, String initiativeId, String eventType, String ownerUid, Instant occurredAt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("initiativeId", initiativeId);
        event.put("eventType", eventType);
        event.put("actorUidHash", hash(ownerUid));
        event.put("occurredAt", occurredAt.toString());
        event.put("schemaVersion", SCHEMA_VERSION);
        return event;
    }

    static Map<String, Object> attendanceEvent(
            String eventId,
            String initiativeId,
            String eventType,
            String ownerUid,
            String attendanceBasis,
            int pointsAwarded,
            Instant occurredAt) {
        Map<String, Object> event = event(eventId, initiativeId, eventType, ownerUid, occurredAt);
        event.put("attendanceStatus", "I_ATTENDED");
        event.put("attendanceBasis", attendanceBasis);
        event.put("pointsAwarded", pointsAwarded);
        event.put("schemaVersion", ATTENDANCE_SCHEMA_VERSION);
        return event;
    }

    static Map<String, Object> participation(
            String participationId,
            String initiativeId,
            String ownerUid,
            String role,
            Instant joinedAt) {
        Map<String, Object> participation = new LinkedHashMap<>();
        participation.put("participationId", participationId);
        participation.put("initiativeId", initiativeId);
        participation.put("ownerUid", ownerUid);
        participation.put("role", role);
        participation.put("status", "JOINED");
        participation.put("joinedAt", joinedAt.toString());
        participation.put("schemaVersion", SCHEMA_VERSION);
        return participation;
    }

    static Map<String, Object> ledger(
            String ledgerEntryId,
            String initiativeId,
            String eventId,
            String ownerUid,
            String eventType,
            Instant occurredAt) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ledgerEntryId", ledgerEntryId);
        entry.put("ownerUid", ownerUid);
        entry.put("sourceType", "INITIATIVE");
        entry.put("sourceId", initiativeId);
        entry.put("triggerEventId", eventId);
        entry.put("triggeringEventId", eventId);
        entry.put("triggeringEvent", eventType);
        entry.put("reason", eventType);
        entry.put("basePoints", 0);
        entry.put("weight", 0.0);
        entry.put("awardedPoints", 0);
        entry.put("pointsAwarded", 0);
        entry.put("policyStatus", "RECORDED_NOT_REWARDED");
        entry.put("occurredAt", occurredAt.toString());
        entry.put("schemaVersion", LEDGER_SCHEMA_VERSION);
        entry.put("rewardPolicyVersion", REWARD_POLICY_VERSION);
        return entry;
    }

    static Map<String, Object> rewardedLedger(
            String ledgerEntryId,
            String initiativeId,
            String eventId,
            String ownerUid,
            String eventType,
            int points,
            Instant occurredAt) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ledgerEntryId", ledgerEntryId);
        entry.put("ownerUid", ownerUid);
        entry.put("sourceType", "INITIATIVE");
        entry.put("sourceId", initiativeId);
        entry.put("triggerEventId", eventId);
        entry.put("triggeringEventId", eventId);
        entry.put("triggeringEvent", eventType);
        entry.put("reason", eventType);
        entry.put("basePoints", points);
        entry.put("weight", 1.0);
        entry.put("awardedPoints", points);
        entry.put("pointsAwarded", points);
        entry.put("policyStatus", "AWARDED");
        entry.put("occurredAt", occurredAt.toString());
        entry.put("schemaVersion", LEDGER_SCHEMA_VERSION);
        entry.put("rewardPolicyVersion", REWARD_POLICY_VERSION);
        return entry;
    }

    static String participationId(String initiativeId, String ownerUid) {
        return "part_" + hash(initiativeId + ":" + ownerUid);
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ValidatedCreate validateCreate(CreateRequest request) {
        if (request == null) throw new InitiativeException("EMPTY_REQUEST", "Activity details are required");
        String title = clean(request.title(), 100, "INVALID_TITLE", "Add an activity title");
        String category = clean(request.category(), 40, "INVALID_CATEGORY", "Choose an activity category")
                .toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) {
            throw new InitiativeException("INVALID_CATEGORY", "Choose a supported activity category");
        }
        String description = clean(request.description(), 1200, "INVALID_DESCRIPTION", "Describe the activity");
        String placeName = clean(request.placeName(), 200, "INVALID_PLACE", "Add a public meeting place");
        String needs = cleanOptional(request.needs(), 500);
        Instant startAt;
        try {
            startAt = Instant.parse(request.startAt());
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new InitiativeException("INVALID_START_TIME", "Use a valid future activity date and time");
        }
        Instant now = clock.instant();
        if (!startAt.isAfter(now) || startAt.isAfter(now.plusSeconds(366L * 24 * 60 * 60))) {
            throw new InitiativeException("INVALID_START_TIME", "Choose a future date within one year");
        }
        return new ValidatedCreate(
                title,
                category,
                description,
                startAt,
                placeName,
                validLatitude(request.latitude()),
                validLongitude(request.longitude()),
                needs);
    }

    private static String clean(String value, int max, String code, String message) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.isEmpty() || cleaned.length() > max) throw new InitiativeException(code, message);
        return cleaned;
    }

    private static String cleanOptional(String value, int max) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.length() > max) throw new InitiativeException("INVALID_NEEDS", "Activity needs are too long");
        return cleaned;
    }

    private static double validLatitude(Double value) {
        if (value == null || !Double.isFinite(value) || value < -90 || value > 90) {
            throw new InitiativeException("INVALID_COORDINATES", "Valid activity coordinates are required");
        }
        return value;
    }

    private static double validLongitude(Double value) {
        if (value == null || !Double.isFinite(value) || value < -180 || value > 180) {
            throw new InitiativeException("INVALID_COORDINATES", "Valid activity coordinates are required");
        }
        return value;
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static Instant instant(Object value) {
        try {
            return value == null ? null : Instant.parse(value.toString());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        if (!Double.isFinite(lat2) || !Double.isFinite(lon2)) return Double.POSITIVE_INFINITY;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private InitiativeView view(
            Map<String, Object> data,
            double distanceKm,
            InitiativeGateway.CitizenInitiative citizen) {
        String role = citizen.role();
        Map<String, Object> participation = citizen.participation();
        Instant now = clock.instant();
        Instant startAt = instant(data.get("startAt"));
        Instant codeEndsAt = startAt == null ? null : startAt.plus(CODE_WINDOW);
        Instant completedAt = instant(data.get("completedAt"));
        String attendanceBasis = String.valueOf(participation.getOrDefault("attendanceBasis", ""));
        boolean participant = "PARTICIPANT".equals(role);
        boolean noAttendance = attendanceBasis.isBlank();
        boolean codeWindowOpen = startAt != null && !now.isBefore(startAt) && !now.isAfter(codeEndsAt)
                && !"CANCELLED".equals(data.get("status"));
        boolean selfWindowOpen = completedAt != null
                && !now.isBefore(completedAt)
                && !now.isAfter(completedAt.plus(SELF_ATTENDANCE_WINDOW));
        boolean showSelfAttendance = codeEndsAt != null && now.isAfter(codeEndsAt);
        return new InitiativeView(
                String.valueOf(data.get("initiativeId")),
                String.valueOf(data.get("title")),
                String.valueOf(data.get("category")),
                String.valueOf(data.get("description")),
                String.valueOf(data.get("startAt")),
                String.valueOf(data.get("placeName")),
                String.valueOf(data.getOrDefault("needs", "")),
                String.valueOf(data.get("status")),
                String.valueOf(data.getOrDefault("cancellationReason", "")),
                integer(data.get("participantCount")),
                Math.round(distanceKm * 100.0) / 100.0,
                role != null && !role.isBlank(),
                role == null ? "" : role,
                "ORGANISER".equals(role),
                citizen.joinerCount(),
                citizen.selfAttendanceCount(),
                citizen.codeAttendanceCount(),
                String.valueOf(participation.getOrDefault("attendanceStatus", "")),
                attendanceBasis,
                String.valueOf(participation.getOrDefault("attendanceReportedAt", "")),
                codeEndsAt == null ? "" : codeEndsAt.toString(),
                participant && noAttendance && codeWindowOpen,
                participant && noAttendance && selfWindowOpen && showSelfAttendance,
                "ORGANISER".equals(role) && codeWindowOpen,
                String.valueOf(data.get("schemaVersion")));
    }

    private static AttendanceWindow codeWindow(Map<String, Object> initiative) {
        Instant startAt = instant(initiative.get("startAt"));
        if (startAt == null) {
            throw new InitiativeException("ATTENDANCE_UNAVAILABLE", "The activity time could not be verified");
        }
        return new AttendanceWindow(startAt, startAt.plus(CODE_WINDOW));
    }

    private record ValidatedCreate(
            String title,
            String category,
            String description,
            Instant startAt,
            String placeName,
            double latitude,
            double longitude,
            String needs) {}

    public record CreateRequest(
            String title,
            String category,
            String description,
            String startAt,
            String placeName,
            Double latitude,
            Double longitude,
            String needs) {}

    public record DiscoveryRequest(String ownerUid, Double latitude, Double longitude, Double radiusKm) {
        public DiscoveryRequest(Double latitude, Double longitude, Double radiusKm) {
            this(null, latitude, longitude, radiusKm);
        }

        DiscoveryRequest withOwnerUid(String value) {
            return new DiscoveryRequest(value, latitude, longitude, radiusKm);
        }
    }

    public record CancelRequest(String reason) {}

    public record AttendanceCodeRequest(String code) {}

    public record InitiativeView(
            String initiativeId,
            String title,
            String category,
            String description,
            String startAt,
            String placeName,
            String needs,
            String status,
            String cancellationReason,
            int participantCount,
            double distanceKm,
            boolean joined,
            String role,
            boolean canManage,
            int joinerCount,
            int selfAttendanceCount,
            int codeAttendanceCount,
            String attendanceStatus,
            String attendanceBasis,
            String attendanceReportedAt,
            String codeWindowEndsAt,
            boolean canUseOrganiserCode,
            boolean canSelfAttend,
            boolean canViewAttendanceCode,
            String schemaVersion) {}

    public record DiscoveryResponse(
            String status, double radiusKm, int count, List<InitiativeView> initiatives) {}

    public record JoinResponse(
            String status, String initiativeId, int participantCount, boolean idempotentReplay) {}

    public record MyInitiativesResponse(String status, int count, List<InitiativeView> initiatives) {}

    public record TransitionResponse(
            String status,
            String initiativeId,
            String initiativeStatus,
            boolean idempotentReplay,
            int pointsAwarded) {}

    public record AttendanceCodeResponse(
            String status,
            String initiativeId,
            String code,
            String rotatesAt,
            String codeWindowEndsAt,
            String schemaVersion) {}

    public record AttendanceResponse(
            String status,
            String initiativeId,
            String attendanceStatus,
            String attendanceBasis,
            String attendanceReportedAt,
            int joinerCount,
            int selfAttendanceCount,
            int codeAttendanceCount,
            boolean idempotentReplay,
            int participantPointsAwarded,
            int organiserPointsAwarded,
            String schemaVersion,
            String rewardPolicyVersion) {}

    private record AttendanceWindow(Instant startAt, Instant endsAt) {}

    public static final class InitiativeException extends RuntimeException {
        private final String code;

        public InitiativeException(String code, String message) {
            super(message);
            this.code = code;
        }

        public InitiativeException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
