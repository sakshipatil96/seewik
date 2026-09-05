package com.seewik.api;

import com.google.cloud.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReportFollowUpService {
    static final String SCHEMA_VERSION = "report-follow-up-v0.1";
    static final Duration INITIAL_WAIT = Duration.ofDays(7);
    static final Duration UNSURE_WAIT = Duration.ofDays(3);
    private static final Set<String> ACTIONS = Set.of("UNRESOLVED", "UNSURE", "ESCALATION_SENT");
    private static final Set<String> CHANNELS = Set.of("NMC_FOLLOW_UP", "DISTRICT_JOINT_COMMISSIONER", "DMA_DESK_6");
    private static final Set<String> ACTIVE_STATUSES = Set.of("FILED", "OVERDUE", "REOPENED");

    private final ReportFollowUpGateway gateway;
    private final Clock clock;

    @Autowired
    public ReportFollowUpService(ReportFollowUpGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public FollowUpSummary get(String ownerUid, String reportId) {
        return summarize(reportId, gateway.load(reportId, ownerUid), clock.instant());
    }

    public RecordResponse record(String ownerUid, String reportId, FollowUpRequest request) {
        ValidatedRequest clean = validate(request);
        Instant now = clock.instant();
        ReportFollowUpGateway.ReportBundle bundle = gateway.load(reportId, ownerUid);
        FollowUpSummary before = summarize(reportId, bundle, now);
        if (("UNRESOLVED".equals(clean.action()) || "UNSURE".equals(clean.action())) && !before.promptDue()) {
            throw new FollowUpException("FOLLOW_UP_NOT_DUE", "The server-calculated follow-up date has not been reached");
        }
        if ("ESCALATION_SENT".equals(clean.action()) && !before.escalationAvailable()) {
            throw new FollowUpException("ESCALATION_NOT_AVAILABLE", "Record an unresolved response before escalating");
        }

        String eventId = "followup_" + sha256(ownerUid + "|" + reportId + "|" + clean.idempotencyKey()).substring(0, 32);
        String fingerprint = sha256(clean.action() + "|" + clean.channelId() + "|" + clean.language() + "|" + before.cycleNumber());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("reportId", reportId);
        event.put("ownerUid", ownerUid);
        event.put("action", clean.action());
        event.put("cycleNumber", before.cycleNumber());
        event.put("recurrence", before.recurrence());
        event.put("occurredAt", Date.from(now));
        event.put("pointsAwarded", 0);
        event.put("verificationBasis", "CITIZEN_ATTESTATION");
        event.put("schemaVersion", SCHEMA_VERSION);
        event.put("requestFingerprint", fingerprint);
        putIfPresent(event, "routeId", string(bundle.report(), "routeId"));
        putIfPresent(event, "packVersion", string(bundle.report(), "packVersion"));
        putIfPresent(event, "routeSnapshotHash", string(bundle.report(), "routeSnapshotHash"));
        putIfPresent(event, "channelId", clean.channelId());
        putIfPresent(event, "language", clean.language());
        if ("UNSURE".equals(clean.action())) event.put("nextPromptAt", Date.from(now.plus(UNSURE_WAIT)));

        boolean replay = gateway.append(reportId, ownerUid, eventId, fingerprint, event);
        FollowUpSummary after = summarize(reportId, gateway.load(reportId, ownerUid), now);
        return new RecordResponse("FOLLOW_UP_RECORDED", replay, after);
    }

    private static FollowUpSummary summarize(
            String reportId,
            ReportFollowUpGateway.ReportBundle bundle,
            Instant now) {
        Cycle cycle = cycle(bundle.report(), bundle.lifecycleEvents());
        List<EventResponse> events = bundle.followUpEvents().stream()
                .filter(event -> integer(event.get("cycleNumber"), 1) == cycle.number())
                .sorted(Comparator.comparing(event -> instant(event.get("occurredAt")), Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(ReportFollowUpService::eventResponse)
                .toList();
        String reportStatus = string(bundle.report(), "status");
        Instant dueAt = cycle.anchorAt() == null ? null : cycle.anchorAt().plus(INITIAL_WAIT);
        String state = "NOT_DUE";
        boolean promptDue = false;
        boolean escalationAvailable = false;
        Instant nextPromptAt = null;

        EventResponse answer = events.stream()
                .filter(event -> "UNRESOLVED".equals(event.action()) || "UNSURE".equals(event.action()))
                .reduce((first, second) -> second).orElse(null);
        if (!ACTIVE_STATUSES.contains(reportStatus)) {
            state = "CLOSED";
        } else if (answer != null && "UNRESOLVED".equals(answer.action())) {
            state = "UNRESOLVED";
            escalationAvailable = true;
        } else if (answer != null && "UNSURE".equals(answer.action())) {
            nextPromptAt = parseInstant(answer.nextPromptAt());
            promptDue = nextPromptAt == null || !now.isBefore(nextPromptAt);
            state = promptDue ? "DUE" : "SNOOZED";
        } else if (cycle.rejectedClaim()) {
            state = "DUE";
            promptDue = true;
            dueAt = cycle.anchorAt();
        } else if (dueAt == null) {
            state = "UNAVAILABLE";
        } else {
            promptDue = !now.isBefore(dueAt);
            state = promptDue ? "DUE" : "NOT_DUE";
        }

        return new FollowUpSummary(
                "FOLLOW_UP_READY",
                reportId,
                state,
                promptDue,
                escalationAvailable,
                cycle.number(),
                cycle.number() > 1,
                iso(cycle.anchorAt()),
                iso(dueAt),
                iso(nextPromptAt),
                string(bundle.report(), "routeId"),
                string(bundle.report(), "packVersion"),
                string(bundle.report(), "routeSnapshotHash"),
                events,
                SCHEMA_VERSION);
    }

    private static Cycle cycle(Map<String, Object> report, List<Map<String, Object>> lifecycleEvents) {
        Instant anchor = instant(report.get("filedAt"));
        int number = 1;
        boolean rejectedClaim = false;
        List<Map<String, Object>> ordered = lifecycleEvents.stream()
                .sorted(Comparator.comparing(event -> instant(event.get("occurredAt")), Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        for (Map<String, Object> event : ordered) {
            String from = string(event, "fromStatus");
            String to = string(event, "toStatus");
            if ("VERIFIED_FIXED".equals(from) && "REOPENED".equals(to)) {
                number++;
                anchor = instant(event.get("occurredAt"));
                rejectedClaim = false;
            } else if ("CLAIMED_FIXED".equals(from) && "REOPENED".equals(to)) {
                rejectedClaim = true;
                anchor = instant(event.get("occurredAt"));
            } else if (!"REOPENED".equals(to)) {
                rejectedClaim = false;
            }
        }
        return new Cycle(number, anchor, rejectedClaim);
    }

    private static EventResponse eventResponse(Map<String, Object> event) {
        return new EventResponse(
                string(event, "eventId"),
                string(event, "action"),
                integer(event.get("cycleNumber"), 1),
                Boolean.TRUE.equals(event.get("recurrence")),
                string(event, "channelId"),
                string(event, "language"),
                iso(instant(event.get("occurredAt"))),
                iso(instant(event.get("nextPromptAt"))),
                integer(event.get("pointsAwarded"), 0));
    }

    private static ValidatedRequest validate(FollowUpRequest request) {
        if (request == null) throw new FollowUpException("EMPTY_REQUEST", "A follow-up action is required");
        String action = required(request.action(), "INVALID_ACTION", 40).toUpperCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) throw new FollowUpException("INVALID_ACTION", "The follow-up action is unsupported");
        String key = required(request.idempotencyKey(), "IDEMPOTENCY_KEY_REQUIRED", 160);
        String channel = optional(request.channelId(), 80);
        String language = optional(request.language(), 8);
        if ("ESCALATION_SENT".equals(action)) {
            if (channel == null || !CHANNELS.contains(channel)) {
                throw new FollowUpException("INVALID_ESCALATION_CHANNEL", "The escalation channel is unsupported");
            }
            if (language == null || !(language.equals("EN") || language.equals("MR"))) {
                throw new FollowUpException("INVALID_LANGUAGE", "Escalation language must be English or Marathi");
            }
        } else if (channel != null || language != null) {
            throw new FollowUpException("ESCALATION_FIELDS_NOT_ALLOWED", "Escalation fields belong only to sent confirmation");
        }
        return new ValidatedRequest(action, key, channel, language);
    }

    private static String required(String value, String code, int maxLength) {
        String clean = optional(value, maxLength);
        if (clean == null) throw new FollowUpException(code, "A required follow-up field is missing");
        return clean;
    }

    private static String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String clean = value.strip();
        if (clean.length() > maxLength) throw new FollowUpException("FOLLOW_UP_FIELD_TOO_LONG", "A follow-up field is too long");
        return clean;
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    private static String string(Map<String, Object> value, String key) {
        Object result = value.get(key);
        return result == null ? null : result.toString();
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toDate().toInstant();
        if (value instanceof Date date) return date.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof String text) return parseInstant(text);
        return null;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException ignored) {
            return null;
        }
    }

    private static String iso(Instant value) {
        return value == null ? null : value.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ValidatedRequest(String action, String idempotencyKey, String channelId, String language) {}
    private record Cycle(int number, Instant anchorAt, boolean rejectedClaim) {}

    public record FollowUpRequest(String action, String idempotencyKey, String channelId, String language) {}

    public record EventResponse(
            String eventId,
            String action,
            int cycleNumber,
            boolean recurrence,
            String channelId,
            String language,
            String occurredAt,
            String nextPromptAt,
            int pointsAwarded) {}

    public record FollowUpSummary(
            String status,
            String reportId,
            String state,
            boolean promptDue,
            boolean escalationAvailable,
            int cycleNumber,
            boolean recurrence,
            String anchorAt,
            String followUpDueAt,
            String nextPromptAt,
            String routeId,
            String packVersion,
            String routeSnapshotHash,
            List<EventResponse> events,
            String schemaVersion) {}

    public record RecordResponse(String status, boolean idempotentReplay, FollowUpSummary summary) {}

    public static final class FollowUpException extends RuntimeException {
        private final String code;

        public FollowUpException(String code, String message) {
            super(message);
            this.code = code;
        }

        public FollowUpException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
