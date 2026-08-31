package com.seewik.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RecognitionService {
    static final String CONSENT_SCHEMA_VERSION = "recognition-consent-v0.1";
    static final String RECOGNITION_SCHEMA_VERSION = "monthly-recognition-v0.1";
    static final String ABUSE_REPORT_SCHEMA_VERSION = "recognition-abuse-report-v0.1";
    static final ZoneId RECOGNITION_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int DISPLAY_NAME_MIN = 2;
    private static final int DISPLAY_NAME_MAX = 60;
    private static final int REPORT_DETAILS_MAX = 300;
    private static final Map<String, Integer> ACTIVE_REWARDS = Map.of(
            "REPORT_FILED", 5,
            "INITIATIVE_ATTENDANCE_ORGANISER_CODE_ATTESTED", 20,
            "INITIATIVE_ORGANISER_COMPLETED_REWARDED", 40,
            "FIX_VERIFIED", 60);
    private static final Set<String> REPORT_REASONS = Set.of("IMPERSONATION", "OFFICIAL_TITLE", "OTHER");
    private static final List<String> RESERVED_TITLES = List.of(
            "nagar parishad officer",
            "municipal officer",
            "municipality officer",
            "chief officer",
            "district collector",
            "government officer",
            "seewik official",
            "नगर परिषद अधिकारी",
            "नगरपालिका अधिकारी",
            "मुख्याधिकारी",
            "जिल्हाधिकारी",
            "जिला कलेक्टर",
            "सरकारी अधिकारी");

    private final RecognitionGateway gateway;
    private final CitizenProfileService profiles;
    private final Clock clock;
    private final Set<String> excludedOwnerUids;

    @Autowired
    public RecognitionService(
            RecognitionGateway gateway,
            CitizenProfileService profiles,
            @Value("${seewik.recognition.excluded-owner-uids:}") String excludedOwnerUids) {
        this(gateway, profiles, Clock.systemUTC(), parseExclusions(excludedOwnerUids));
    }

    RecognitionService(
            RecognitionGateway gateway,
            CitizenProfileService profiles,
            Clock clock,
            Set<String> excludedOwnerUids) {
        this.gateway = gateway;
        this.profiles = profiles;
        this.clock = clock;
        this.excludedOwnerUids = Set.copyOf(excludedOwnerUids);
    }

    public PublicPanelResponse publicPanel() {
        Selection selection = buildCurrentSelection();
        RecognitionGateway.MonthSnapshot snapshot = new RecognitionGateway.MonthSnapshot(
                selection.boundary().monthKey(),
                selection.boundary().start(),
                selection.boundary().endExclusive(),
                selection.selected(),
                selection.candidateCount(),
                selection.contentHash(),
                clock.instant(),
                RECOGNITION_SCHEMA_VERSION,
                InitiativeService.REWARD_POLICY_VERSION);
        gateway.saveMonthSnapshotIfChanged(snapshot);
        List<String> names = selection.selected().stream()
                .map(RecognitionGateway.SelectedCitizen::publicDisplayName)
                .toList();
        return new PublicPanelResponse(
                "RECOGNITION_READY",
                selection.boundary().monthKey(),
                selection.boundary().label(),
                names,
                names.isEmpty()
                        ? "No citizens have opted in and qualified for public recognition this month."
                        : "A Seewik thank-you for recorded civic contributions.",
                RECOGNITION_SCHEMA_VERSION);
    }

    public RecognitionSettingsResponse settings(String ownerUid) {
        CitizenProfileService.PrivateProfileResponse profile = profiles.get(ownerUid);
        RecognitionGateway.Consent consent = gateway.findConsent(ownerUid);
        String displayName = consent == null || consent.publicDisplayName().isBlank()
                ? profile.privateGoogleName()
                : consent.publicDisplayName();
        String status = consent == null ? "PRIVATE" : consent.status();
        return new RecognitionSettingsResponse(
                "RECOGNITION_SETTINGS_READY",
                displayName,
                status,
                "OPTED_IN".equals(status),
                CONSENT_SCHEMA_VERSION);
    }

    public RecognitionSettingsResponse updateSettings(String ownerUid, RecognitionSettingsRequest request) {
        if (request == null || request.recognitionActive() == null) {
            throw invalid("RECOGNITION_CHOICE_REQUIRED", "Choose whether your public recognition is active");
        }
        RecognitionGateway.Consent existing = gateway.findConsent(ownerUid);
        String fallback = existing == null ? profiles.get(ownerUid).privateGoogleName() : existing.publicDisplayName();
        boolean activate = request.recognitionActive();
        String requestedName = request.publicDisplayName() == null ? fallback : request.publicDisplayName();
        String displayName = validateDisplayName(!activate && existing != null
                ? existing.publicDisplayName()
                : requestedName);
        String currentStatus = existing == null ? "PRIVATE" : existing.status();
        String nextStatus = activate ? "OPTED_IN" : "OPTED_IN".equals(currentStatus) ? "WITHDRAWN" : currentStatus;
        if (!activate && !Set.of("PRIVATE", "WITHDRAWN").contains(nextStatus)) nextStatus = "PRIVATE";

        boolean unchanged = existing != null
                && displayName.equals(existing.publicDisplayName())
                && nextStatus.equals(existing.status());
        if (unchanged) return settings(ownerUid);

        Instant now = clock.instant();
        Instant consentedAt = activate && !"OPTED_IN".equals(currentStatus)
                ? now
                : existing == null ? null : existing.consentedAt();
        Instant withdrawnAt = !activate && "OPTED_IN".equals(currentStatus)
                ? now
                : activate ? null : existing == null ? null : existing.withdrawnAt();
        String eventType = eventType(currentStatus, nextStatus, existing, displayName);
        RecognitionGateway.Consent updated = new RecognitionGateway.Consent(
                ownerUid,
                displayName,
                normalizedName(displayName),
                nextStatus,
                consentedAt,
                withdrawnAt,
                now,
                CONSENT_SCHEMA_VERSION);
        RecognitionGateway.ConsentEvent event = new RecognitionGateway.ConsentEvent(
                "recognition-consent-" + UUID.randomUUID(),
                ownerUid,
                eventType,
                hash(displayName),
                now,
                CONSENT_SCHEMA_VERSION);
        gateway.saveConsent(updated, event);
        if ("OPTED_IN".equals(updated.status())) monitorCollision(updated);
        return new RecognitionSettingsResponse(
                "RECOGNITION_SETTINGS_SAVED",
                updated.publicDisplayName(),
                updated.status(),
                "OPTED_IN".equals(updated.status()),
                CONSENT_SCHEMA_VERSION);
    }

    public PrivatePointsResponse privatePoints(String ownerUid) {
        MonthBoundary boundary = currentBoundary();
        List<Map<String, Object>> entries = gateway.ownerLedgerEntries(ownerUid);
        List<PrivateAward> lifetime = deduplicatePrivateAwards(entries, ownerUid);
        List<ValidAward> current = validActiveAwards(entries, boundary).stream()
                .filter(entry -> ownerUid.equals(entry.ownerUid()))
                .toList();

        Map<String, List<PrivateAward>> lifetimeByReason = lifetime.stream()
                .collect(Collectors.groupingBy(PrivateAward::reason, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<ValidAward>> currentByReason = current.stream()
                .collect(Collectors.groupingBy(ValidAward::reason, LinkedHashMap::new, Collectors.toList()));
        List<ContributionBreakdown> breakdown = ACTIVE_REWARDS.keySet().stream().sorted()
                .map(reason -> new ContributionBreakdown(
                        reason,
                        lifetimeByReason.getOrDefault(reason, List.of()).stream().mapToInt(PrivateAward::points).sum(),
                        currentByReason.getOrDefault(reason, List.of()).stream().mapToInt(ValidAward::points).sum(),
                        lifetimeByReason.getOrDefault(reason, List.of()).size()))
                .filter(item -> item.lifetimePoints() > 0 || item.currentMonthPoints() > 0)
                .toList();
        return new PrivatePointsResponse(
                "PRIVATE_POINTS_READY",
                lifetime.stream().mapToInt(PrivateAward::points).sum(),
                current.stream().mapToInt(ValidAward::points).sum(),
                boundary.label(),
                breakdown,
                InitiativeService.LEDGER_SCHEMA_VERSION,
                InitiativeService.REWARD_POLICY_VERSION);
    }

    public AbuseReportResponse reportDisplayedName(String reporterUid, AbuseReportRequest request) {
        if (request == null || request.targetPosition() == null
                || request.targetPosition() < 0 || request.targetPosition() > 2) {
            throw invalid("RECOGNITION_TARGET_INVALID", "Choose a displayed name to report");
        }
        String reason = request.reason() == null ? "" : request.reason().strip().toUpperCase(Locale.ROOT);
        if (!REPORT_REASONS.contains(reason)) {
            throw invalid("RECOGNITION_REPORT_REASON_INVALID", "Choose a valid reason for the report");
        }
        String details = request.details() == null ? "" : request.details().strip();
        if (details.length() > REPORT_DETAILS_MAX) {
            throw invalid("RECOGNITION_REPORT_TOO_LONG", "The report details are too long");
        }
        Selection selection = buildCurrentSelection();
        if (request.targetPosition() >= selection.selected().size()) {
            throw new RecognitionException("RECOGNITION_TARGET_UNAVAILABLE", "That displayed name is no longer available");
        }
        RecognitionGateway.SelectedCitizen target = selection.selected().get(request.targetPosition());
        if (request.targetDisplayName() == null
                || !target.publicDisplayName().equals(request.targetDisplayName())) {
            throw new RecognitionException("RECOGNITION_TARGET_UNAVAILABLE", "That displayed name is no longer available");
        }
        Instant now = clock.instant();
        String reportId = "recognition-report-" + UUID.randomUUID();
        gateway.recordAbuseReport(new RecognitionGateway.AbuseReport(
                reportId,
                hash(reporterUid),
                hash(target.ownerUid()),
                hash(target.publicDisplayName()),
                selection.boundary().monthKey(),
                request.targetPosition(),
                reason,
                details,
                now,
                ABUSE_REPORT_SCHEMA_VERSION));
        return new AbuseReportResponse(
                "RECOGNITION_REPORT_RECORDED",
                "Thank you. Seewik recorded this concern for review.",
                ABUSE_REPORT_SCHEMA_VERSION);
    }

    Selection buildCurrentSelection() {
        MonthBoundary boundary = currentBoundary();
        List<ValidAward> awards = validActiveAwards(gateway.awardedLedgerEntries(), boundary);
        Map<String, RecognitionGateway.Consent> consents = gateway.activeConsents().stream()
                .filter(consent -> "OPTED_IN".equals(consent.status()))
                .filter(consent -> CONSENT_SCHEMA_VERSION.equals(consent.schemaVersion()))
                .collect(Collectors.toMap(
                        RecognitionGateway.Consent::ownerUid,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        Map<String, Integer> totals = new LinkedHashMap<>();
        awards.forEach(award -> totals.merge(award.ownerUid(), award.points(), Integer::sum));

        List<RecognitionGateway.SelectedCitizen> candidates = totals.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && consents.containsKey(entry.getKey()))
                .map(entry -> new RecognitionGateway.SelectedCitizen(
                        entry.getKey(),
                        consents.get(entry.getKey()).publicDisplayName(),
                        entry.getValue()))
                .sorted(Comparator.comparingInt(RecognitionGateway.SelectedCitizen::monthlyPoints).reversed()
                        .thenComparing(item -> normalizedName(item.publicDisplayName()))
                        .thenComparing(RecognitionGateway.SelectedCitizen::publicDisplayName)
                        .thenComparing(item -> hash(item.ownerUid())))
                .toList();
        List<RecognitionGateway.SelectedCitizen> selected = candidates.stream().limit(3).toList();
        String canonical = boundary.monthKey() + "|"
                + awards.stream().map(ValidAward::canonical).sorted().collect(Collectors.joining("|")) + "|"
                + consents.values().stream()
                        .map(consent -> consent.ownerUid() + ":" + consent.status() + ":" + consent.publicDisplayName())
                        .sorted().collect(Collectors.joining("|")) + "|"
                + selected.stream().map(item -> item.ownerUid() + ":" + item.publicDisplayName() + ":" + item.monthlyPoints())
                        .collect(Collectors.joining("|"));
        return new Selection(boundary, selected, candidates.size(), hash(canonical));
    }

    List<ValidAward> validActiveAwards(List<Map<String, Object>> entries, MonthBoundary boundary) {
        Map<String, ValidAward> deduplicated = new LinkedHashMap<>();
        entries.stream()
                .map(entry -> activeAward(entry, boundary))
                .filter(item -> item != null)
                .sorted(Comparator.comparing(ValidAward::ledgerEntryId))
                .forEach(item -> deduplicated.putIfAbsent(item.businessKey(), item));
        return List.copyOf(deduplicated.values());
    }

    private ValidAward activeAward(Map<String, Object> entry, MonthBoundary boundary) {
        if (!"AWARDED".equals(string(entry.get("policyStatus")))) return null;
        if (!InitiativeService.LEDGER_SCHEMA_VERSION.equals(string(entry.get("schemaVersion")))) return null;
        if (!InitiativeService.REWARD_POLICY_VERSION.equals(string(entry.get("rewardPolicyVersion")))) return null;
        if (Boolean.TRUE.equals(entry.get("demoMode"))) return null;
        String ownerUid = string(entry.get("ownerUid"));
        if (ownerUid.isBlank() || excludedOwnerUids.contains(ownerUid)) return null;
        String reason = string(entry.get("reason"));
        Integer expected = ACTIVE_REWARDS.get(reason);
        int points = integer(entry.get("awardedPoints"));
        if (expected == null || points != expected) return null;
        Instant occurredAt = instant(entry.get("occurredAt"));
        if (occurredAt == null || occurredAt.isBefore(boundary.start()) || !occurredAt.isBefore(boundary.endExclusive())) {
            return null;
        }
        String sourceId = string(entry.get("sourceId"));
        String ledgerEntryId = string(entry.get("ledgerEntryId"));
        if (sourceId.isBlank() || ledgerEntryId.isBlank()) return null;
        return new ValidAward(ownerUid, ledgerEntryId, sourceId, reason, points, occurredAt);
    }

    private List<PrivateAward> deduplicatePrivateAwards(List<Map<String, Object>> entries, String ownerUid) {
        Map<String, PrivateAward> deduplicated = new LinkedHashMap<>();
        entries.stream()
                .filter(entry -> ownerUid.equals(string(entry.get("ownerUid"))))
                .filter(entry -> "AWARDED".equals(string(entry.get("policyStatus"))))
                .filter(entry -> !Boolean.TRUE.equals(entry.get("demoMode")))
                .map(entry -> {
                    String reason = string(entry.get("reason"));
                    String sourceId = string(entry.get("sourceId"));
                    String ledgerEntryId = string(entry.get("ledgerEntryId"));
                    int points = integer(entry.get("awardedPoints"));
                    if (reason.isBlank() || sourceId.isBlank() || ledgerEntryId.isBlank() || points <= 0) return null;
                    return new PrivateAward(ledgerEntryId, sourceId, reason, points);
                })
                .filter(item -> item != null)
                .sorted(Comparator.comparing(PrivateAward::ledgerEntryId))
                .forEach(item -> deduplicated.putIfAbsent(item.businessKey(), item));
        return List.copyOf(deduplicated.values());
    }

    private void monitorCollision(RecognitionGateway.Consent consent) {
        List<String> collisions = gateway.collidingOwnerUids(
                consent.normalizedDisplayName(), consent.ownerUid());
        if (collisions.isEmpty()) return;
        Instant now = clock.instant();
        gateway.recordNameCollision(new RecognitionGateway.NameCollisionEvent(
                "recognition-collision-" + UUID.randomUUID(),
                hash(consent.ownerUid()),
                collisions.stream().map(RecognitionService::hash).toList(),
                hash(consent.normalizedDisplayName()),
                now,
                CONSENT_SCHEMA_VERSION));
    }

    private MonthBoundary currentBoundary() {
        return monthBoundary(YearMonth.from(clock.instant().atZone(RECOGNITION_ZONE)));
    }

    static MonthBoundary monthBoundary(YearMonth month) {
        Instant start = month.atDay(1).atStartOfDay(RECOGNITION_ZONE).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(RECOGNITION_ZONE).toInstant();
        String label = month.format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH));
        return new MonthBoundary(month.toString(), label, start, end);
    }

    static String validateDisplayName(String value) {
        String cleaned = value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip().replaceAll("\\s+", " ");
        int length = cleaned.codePointCount(0, cleaned.length());
        if (length < DISPLAY_NAME_MIN || length > DISPLAY_NAME_MAX) {
            throw invalid("DISPLAY_NAME_LENGTH", "Use a public display name between 2 and 60 characters");
        }
        if (!cleaned.matches("^[\\p{L}\\p{M}][\\p{L}\\p{M}\\p{N} .'-]*$")) {
            throw invalid("DISPLAY_NAME_CHARACTERS", "Use letters, spaces and ordinary name punctuation only");
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        long digits = cleaned.codePoints().filter(Character::isDigit).count();
        boolean contact = lower.contains("http://") || lower.contains("https://") || lower.contains("www.")
                || lower.matches(".*\\.(com|in|org|net|me)(/.*)?$")
                || lower.contains("@") || digits >= 7
                || lower.contains("whatsapp") || lower.contains("contact me") || lower.contains("call me");
        if (contact) {
            throw invalid("DISPLAY_NAME_CONTACT_DETAILS", "Public display names cannot contain contact details or links");
        }
        String comparable = lower.replaceAll("\\s+", " ");
        if (RESERVED_TITLES.stream().anyMatch(comparable::contains)) {
            throw invalid("DISPLAY_NAME_RESERVED_TITLE", "Public display names cannot claim an official title");
        }
        return cleaned;
    }

    static String normalizedName(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{M}\\p{N}]", "");
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String eventType(
            String currentStatus,
            String nextStatus,
            RecognitionGateway.Consent existing,
            String displayName) {
        if (!"OPTED_IN".equals(currentStatus) && "OPTED_IN".equals(nextStatus)) return "RECOGNITION_OPTED_IN";
        if ("OPTED_IN".equals(currentStatus) && !"OPTED_IN".equals(nextStatus)) return "RECOGNITION_WITHDRAWN";
        if (existing == null || !displayName.equals(existing.publicDisplayName())) return "PUBLIC_DISPLAY_NAME_UPDATED";
        return "RECOGNITION_SETTINGS_CONFIRMED";
    }

    private static Set<String> parseExclusions(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Instant instant(Object value) {
        if (value instanceof com.google.cloud.Timestamp timestamp) return timestamp.toDate().toInstant();
        if (value instanceof Date date) return date.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value == null) return null;
        try {
            return Instant.parse(value.toString());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static RecognitionException invalid(String code, String message) {
        return new RecognitionException(code, message);
    }

    record MonthBoundary(String monthKey, String label, Instant start, Instant endExclusive) {}

    record Selection(
            MonthBoundary boundary,
            List<RecognitionGateway.SelectedCitizen> selected,
            int candidateCount,
            String contentHash) {}

    record ValidAward(
            String ownerUid,
            String ledgerEntryId,
            String sourceId,
            String reason,
            int points,
            Instant occurredAt) {
        String businessKey() {
            return ownerUid + "|" + sourceId + "|" + reason;
        }

        String canonical() {
            return ledgerEntryId + ":" + businessKey() + ":" + points + ":" + occurredAt;
        }
    }

    private record PrivateAward(String ledgerEntryId, String sourceId, String reason, int points) {
        String businessKey() {
            return sourceId + "|" + reason;
        }
    }

    public record PublicPanelResponse(
            String status,
            String monthKey,
            String monthLabel,
            List<String> names,
            String message,
            String schemaVersion) {}

    public record RecognitionSettingsRequest(String publicDisplayName, Boolean recognitionActive) {}

    public record RecognitionSettingsResponse(
            String status,
            String publicDisplayName,
            String recognitionStatus,
            boolean recognitionActive,
            String schemaVersion) {}

    public record ContributionBreakdown(
            String contributionType,
            int lifetimePoints,
            int currentMonthPoints,
            int lifetimeAwards) {}

    public record PrivatePointsResponse(
            String status,
            int lifetimePoints,
            int currentMonthPoints,
            String monthLabel,
            List<ContributionBreakdown> breakdown,
            String ledgerSchemaVersion,
            String rewardPolicyVersion) {}

    public record AbuseReportRequest(
            Integer targetPosition,
            String targetDisplayName,
            String reason,
            String details) {}

    public record AbuseReportResponse(String status, String message, String schemaVersion) {}

    public static final class RecognitionException extends RuntimeException {
        private final String code;

        public RecognitionException(String code, String message) {
            super(message);
            this.code = code;
        }

        public RecognitionException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
