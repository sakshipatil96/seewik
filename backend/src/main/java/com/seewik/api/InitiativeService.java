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
    static final String INITIATIVE_RECORD_SCHEMA_VERSION = "initiative-v0.4";
    static final String MEETING_POINT_SCHEMA_VERSION = "initiative-meeting-point-v0.1";
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
            "CLEANUP", "PLANTATION", "DONATION", "COMMUNITY_FITNESS",
            "BIRTHDAY_DONATION", "PLANTATION_DRIVE", "AWARENESS_SESSION", "COMMUNITY_YOGA",
            "MEDITATION_WORKSHOP", "HEALTH_ACTIVITY", "BOOK_SUPPLY_DRIVE", "OTHER_CIVIC_ACTIVITY");
    private static final Set<String> PARTICIPATION_MODES = Set.of("OPEN", "CAPPED", "APPROVAL_REQUIRED");

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
        String initiativeId = input.clientRequestId().isBlank()
                ? "init_" + UUID.randomUUID().toString().replace("-", "")
                : "init_" + hash(ownerUid + ":" + input.clientRequestId());
        String eventId = "evt_" + hash(initiativeId + ":INITIATIVE_CREATED");
        String participationId = participationId(initiativeId, ownerUid);
        String ledgerEntryId = "pts_" + hash(initiativeId + ":INITIATIVE_CREATED:" + ownerUid);

        Map<String, Object> initiative = new LinkedHashMap<>();
        initiative.put("initiativeId", initiativeId);
        initiative.put("ownerUid", ownerUid);
        initiative.put("title", input.title());
        initiative.put("category", input.category());
        initiative.put("description", input.description());
        initiative.put("publicOrganiserName", input.publicOrganiserName());
        initiative.put("publicOrganiserNameConfirmed", true);
        initiative.put("startAt", input.startAt().toString());
        initiative.put("endAt", input.endAt().toString());
        initiative.put("placeName", input.placeName());
        initiative.put("latitude", input.latitude());
        initiative.put("longitude", input.longitude());
        Map<String, Object> meetingPoint = new LinkedHashMap<>();
        meetingPoint.put("label", input.placeName());
        meetingPoint.put("latitude", input.latitude());
        meetingPoint.put("longitude", input.longitude());
        meetingPoint.put("schemaVersion", MEETING_POINT_SCHEMA_VERSION);
        initiative.put("meetingPoint", meetingPoint);
        initiative.put("capacity", input.capacity());
        initiative.put("neededItems", input.neededItems());
        initiative.put("organiserMessage", input.organiserMessage());
        initiative.put("participationMode", input.participationMode());
        initiative.put("needs", String.join(", ", input.neededItems()));
        initiative.put("status", "PUBLISHED");
        initiative.put("participantCount", 1);
        initiative.put("createdAt", now.toString());
        initiative.put("updatedAt", now.toString());
        initiative.put("schemaVersion", INITIATIVE_RECORD_SCHEMA_VERSION);

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
        nearby.sort(Comparator.comparing(InitiativeView::startAt));
        return new DiscoveryResponse("NEARBY_INITIATIVES", radiusKm, nearby.size(), List.copyOf(nearby));
    }

    public InitiativeView detail(String ownerUid, String initiativeId) {
        String cleanId = clean(initiativeId, 80, "INVALID_INITIATIVE_ID", "Initiative ID is required");
        return gateway.listPublished(ownerUid).stream()
                .filter(item -> cleanId.equals(String.valueOf(item.initiative().get("initiativeId"))))
                .findFirst()
                .map(item -> view(item.initiative(), 0.0, item))
                .orElseThrow(() -> new InitiativeException("INITIATIVE_NOT_FOUND", "Activity was not found"));
    }

    public JoinResponse join(String ownerUid, String initiativeId) {
        String cleanId = clean(initiativeId, 80, "INVALID_INITIATIVE_ID", "Initiative ID is required");
        InitiativeGateway.JoinResult result = gateway.join(ownerUid, cleanId, clock.instant());
        return new JoinResponse(
                result.approvalRequested() ? "APPROVAL_REQUESTED"
                        : result.alreadyJoined() ? "ALREADY_JOINED" : "JOINED",
                cleanId,
                integer(result.initiative().get("participantCount")),
                result.alreadyJoined());
    }

    public JoinRequestsResponse joinRequests(String ownerUid, String initiativeId) {
        String cleanId = clean(initiativeId, 80, "INVALID_INITIATIVE_ID", "Initiative ID is required");
        List<JoinRequestView> requests = gateway.listJoinRequests(ownerUid, cleanId).stream()
                .map(request -> new JoinRequestView(request.requestId(), request.requestedAt()))
                .toList();
        return new JoinRequestsResponse("JOIN_REQUESTS", cleanId, requests.size(), requests);
    }

    public ReviewJoinRequestResponse reviewJoinRequest(
            String ownerUid, String initiativeId, String requestId, boolean approved) {
        String cleanId = clean(initiativeId, 80, "INVALID_INITIATIVE_ID", "Initiative ID is required");
        String cleanRequestId = clean(requestId, 100, "INVALID_JOIN_REQUEST_ID", "Join request ID is required");
        InitiativeGateway.ReviewJoinRequestResult result = gateway.reviewJoinRequest(
                ownerUid, cleanId, cleanRequestId, approved, clock.instant());
        return new ReviewJoinRequestResponse(
                approved ? "JOIN_REQUEST_APPROVED" : "JOIN_REQUEST_DECLINED",
                cleanId,
                cleanRequestId,
                result.participantCount(),
                result.idempotentReplay());
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
        String publicOrganiserName = clean(
                request.publicOrganiserName(), 60, "INVALID_ORGANISER_NAME", "Add the public organiser name");
        String placeName = clean(request.placeName(), 200, "INVALID_PLACE", "Add a public meeting place");
        List<String> neededItems = cleanItems(request.neededItems());
        String organiserMessage = cleanOptional(
                request.organiserMessage(), 500, "INVALID_ORGANISER_MESSAGE", "The organiser message is too long");
        String participationMode = cleanOptional(
                request.participationMode(), 30, "INVALID_PARTICIPATION_MODE", "Choose who can participate")
                .toUpperCase(Locale.ROOT);
        if (participationMode.isBlank()) participationMode = "OPEN";
        if (!PARTICIPATION_MODES.contains(participationMode)) {
            throw new InitiativeException("INVALID_PARTICIPATION_MODE", "Choose a supported participation option");
        }
        String clientRequestId = cleanOptional(
                request.clientRequestId(), 100, "INVALID_CLIENT_REQUEST_ID", "The retry identifier is too long");
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
        Instant endAt;
        try {
            endAt = Instant.parse(request.endAt());
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new InitiativeException("INVALID_END_TIME", "Use a valid activity end time");
        }
        if (!endAt.isAfter(startAt)) {
            throw new InitiativeException("INVALID_END_TIME", "The end time must be after the start time");
        }
        Integer capacity = request.capacity();
        if ("CAPPED".equals(participationMode) && capacity == null) {
            throw new InitiativeException("INVALID_CAPACITY", "Add the maximum number of participants");
        }
        if (capacity != null && (capacity < 1 || capacity > 500)) {
            throw new InitiativeException("INVALID_CAPACITY", "Participant capacity must be from 1 to 500");
        }
        if (!"CAPPED".equals(participationMode)) capacity = null;
        return new ValidatedCreate(
                title,
                category,
                description,
                publicOrganiserName,
                startAt,
                endAt,
                placeName,
                validLatitude(request.latitude()),
                validLongitude(request.longitude()),
                capacity,
                neededItems,
                organiserMessage,
                participationMode,
                clientRequestId);
    }

    private static String clean(String value, int max, String code, String message) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.isEmpty() || cleaned.length() > max) throw new InitiativeException(code, message);
        return cleaned;
    }

    private static String cleanOptional(String value, int max, String code, String message) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.length() > max) throw new InitiativeException(code, message);
        return cleaned;
    }

    private static List<String> cleanItems(List<String> values) {
        if (values == null) return List.of();
        if (values.size() > 8) {
            throw new InitiativeException("INVALID_NEEDED_ITEMS", "Add no more than eight needed items");
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String item = clean(value, 80, "INVALID_NEEDED_ITEMS", "Each needed item must contain text");
            if (!result.contains(item)) result.add(item);
        }
        return List.copyOf(result);
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
        Instant endAt = instant(data.get("endAt"));
        if (endAt == null) endAt = startAt;
        Instant codeEndsAt = startAt == null ? null : startAt.plus(CODE_WINDOW);
        Instant completedAt = instant(data.get("completedAt"));
        String attendanceBasis = String.valueOf(participation.getOrDefault("attendanceBasis", ""));
        boolean participant = "PARTICIPANT".equals(role);
        boolean approvalRequested = "REQUESTED".equals(role);
        boolean noAttendance = attendanceBasis.isBlank();
        boolean codeWindowOpen = startAt != null && !now.isBefore(startAt) && !now.isAfter(codeEndsAt)
                && !"CANCELLED".equals(data.get("status"));
        boolean selfWindowOpen = completedAt != null
                && !now.isBefore(completedAt)
                && !now.isAfter(completedAt.plus(SELF_ATTENDANCE_WINDOW));
        boolean showSelfAttendance = codeEndsAt != null && now.isAfter(codeEndsAt);
        MeetingPoint meetingPoint = readMeetingPoint(data);
        int storedParticipantCount = integer(data.get("participantCount"));
        int storedJoinerCount = Math.max(0, storedParticipantCount - 1);
        Integer capacity = data.get("capacity") instanceof Number number && number.intValue() > 0
                ? number.intValue()
                : null;
        boolean countVisible = participant || "ORGANISER".equals(role)
                || capacity != null && storedJoinerCount * 100 >= capacity * 60;
        boolean full = capacity != null && storedJoinerCount >= capacity;
        return new InitiativeView(
                String.valueOf(data.get("initiativeId")),
                String.valueOf(data.get("title")),
                String.valueOf(data.get("category")),
                String.valueOf(data.get("description")),
                text(data.get("publicOrganiserName")),
                String.valueOf(data.get("startAt")),
                endAt == null ? "" : endAt.toString(),
                meetingPoint.label(),
                meetingPoint.mapsUrl(),
                meetingPoint.schemaVersion(),
                meetingPoint.legacy(),
                String.valueOf(data.getOrDefault("needs", "")),
                storedItems(data),
                text(data.get("organiserMessage")),
                capacity,
                text(data.get("participationMode")).isBlank() ? (capacity == null ? "OPEN" : "CAPPED") : text(data.get("participationMode")),
                String.valueOf(data.get("status")),
                String.valueOf(data.getOrDefault("cancellationReason", "")),
                countVisible ? storedParticipantCount : null,
                Math.round(distanceKm * 100.0) / 100.0,
                participant || "ORGANISER".equals(role),
                approvalRequested,
                role == null ? "" : role,
                "ORGANISER".equals(role),
                countVisible ? storedJoinerCount : null,
                countVisible,
                full,
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

    private static List<String> storedItems(Map<String, Object> data) {
        Object stored = data.get("neededItems");
        if (stored instanceof List<?> values) {
            List<String> result = new ArrayList<>();
            for (Object value : values) {
                String item = text(value).strip();
                if (!item.isBlank()) result.add(item);
            }
            return List.copyOf(result);
        }
        String legacy = text(data.get("needs")).strip();
        return legacy.isBlank() ? List.of() : List.of(legacy);
    }

    private static String defaultEndAt(String startAt) {
        try {
            return Instant.parse(startAt).plus(Duration.ofHours(2)).toString();
        } catch (DateTimeParseException | NullPointerException exception) {
            return startAt;
        }
    }

    private static MeetingPoint readMeetingPoint(Map<String, Object> data) {
        Object stored = data.get("meetingPoint");
        if (stored instanceof Map<?, ?> value) {
            String label = text(value.get("label"));
            double latitude = number(value.get("latitude"));
            double longitude = number(value.get("longitude"));
            String schemaVersion = text(value.get("schemaVersion"));
            if (!label.isBlank() && validCoordinatePair(latitude, longitude)) {
                return new MeetingPoint(label, mapsUrl(latitude, longitude), schemaVersion, false);
            }
        }
        String label = text(data.get("placeName"));
        double latitude = number(data.get("latitude"));
        double longitude = number(data.get("longitude"));
        return new MeetingPoint(
                label,
                validCoordinatePair(latitude, longitude) ? mapsUrl(latitude, longitude) : "",
                "",
                true);
    }

    private static boolean validCoordinatePair(double latitude, double longitude) {
        return Double.isFinite(latitude)
                && latitude >= -90
                && latitude <= 90
                && Double.isFinite(longitude)
                && longitude >= -180
                && longitude <= 180;
    }

    private static String mapsUrl(double latitude, double longitude) {
        return String.format(
                Locale.ROOT,
                "https://www.google.com/maps/search/?api=1&query=%.6f%%2C%.6f",
                latitude,
                longitude);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
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
            String publicOrganiserName,
            Instant startAt,
            Instant endAt,
            String placeName,
            double latitude,
            double longitude,
            Integer capacity,
            List<String> neededItems,
            String organiserMessage,
            String participationMode,
            String clientRequestId) {}

    public record CreateRequest(
            String title,
            String category,
            String description,
            String startAt,
            String endAt,
            String publicOrganiserName,
            boolean publicOrganiserNameConfirmed,
            String placeName,
            Double latitude,
            Double longitude,
            Integer capacity,
            List<String> neededItems,
            String organiserMessage,
            String participationMode,
            String clientRequestId) {
        public CreateRequest(
                String title,
                String category,
                String description,
                String startAt,
                String placeName,
                Double latitude,
                Double longitude,
                String needs,
                String clientRequestId) {
            this(title, category, description, startAt, defaultEndAt(startAt), "A citizen organiser", true,
                    placeName, latitude, longitude, null,
                    needs == null || needs.isBlank() ? List.of() : List.of(needs), "", "OPEN", clientRequestId);
        }

        public CreateRequest(
                String title,
                String category,
                String description,
                String startAt,
                String placeName,
                Double latitude,
                Double longitude,
                String needs) {
            this(title, category, description, startAt, placeName, latitude, longitude, needs, null);
        }
    }

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
            String publicOrganiserName,
            String startAt,
            String endAt,
            String placeName,
            String mapsUrl,
            String meetingPointSchemaVersion,
            boolean legacyMeetingPoint,
            String needs,
            List<String> neededItems,
            String organiserMessage,
            Integer capacity,
            String participationMode,
            String status,
            String cancellationReason,
            Integer participantCount,
            double distanceKm,
            boolean joined,
            boolean joinRequestedByMe,
            String role,
            boolean canManage,
            Integer joinerCount,
            boolean joiningCountVisible,
            boolean full,
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

    private record MeetingPoint(
            String label,
            String mapsUrl,
            String schemaVersion,
            boolean legacy) {}

    public record DiscoveryResponse(
            String status, double radiusKm, int count, List<InitiativeView> initiatives) {}

    public record JoinResponse(
            String status, String initiativeId, int participantCount, boolean idempotentReplay) {}

    public record JoinRequestView(String requestId, String requestedAt) {}

    public record JoinRequestsResponse(
            String status, String initiativeId, int count, List<JoinRequestView> requests) {}

    public record ReviewJoinRequestResponse(
            String status,
            String initiativeId,
            String requestId,
            int participantCount,
            boolean idempotentReplay) {}

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
