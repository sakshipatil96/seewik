package com.seewik.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReportLifecycleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportLifecycleService.class);
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 160;
    public static final int MAX_ACKNOWLEDGEMENT_ID_LENGTH = 200;
    public static final int MAX_EVIDENCE_REFERENCE_LENGTH = 500;
    public static final int MAX_NOTE_LENGTH = 1000;
    private final ReportLifecycleGateway gateway;
    private final CivicRouterService router;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final LifecycleAnalyticsPublisher analyticsPublisher;

    @Autowired
    public ReportLifecycleService(
            ReportLifecycleGateway gateway,
            CivicRouterService router,
            ObjectMapper objectMapper,
            LifecycleAnalyticsPublisher analyticsPublisher) {
        this(gateway, router, objectMapper, Clock.systemUTC(), analyticsPublisher);
    }

    ReportLifecycleService(
            ReportLifecycleGateway gateway,
            CivicRouterService router,
            ObjectMapper objectMapper,
            Clock clock) {
        this(gateway, router, objectMapper, clock, ignored -> {});
    }

    ReportLifecycleService(
            ReportLifecycleGateway gateway,
            CivicRouterService router,
            ObjectMapper objectMapper,
            Clock clock,
            LifecycleAnalyticsPublisher analyticsPublisher) {
        this.gateway = gateway;
        this.router = router;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.analyticsPublisher = analyticsPublisher;
    }

    public TransitionResponse transition(String ownerUid, String reportId, TransitionRequest request) {
        String cleanOwnerUid = required(ownerUid, "AUTHENTICATION_REQUIRED", "Authenticated user is required", 160);
        String cleanReportId = required(reportId, "INVALID_REPORT_ID", "Report ID is required", 160);
        ValidatedRequest validated = validate(request);
        String idempotencyKeyHash = sha256(validated.idempotencyKey());
        String requestFingerprint = fingerprint(cleanReportId, cleanOwnerUid, validated);
        String eventId = "evt_" + idempotencyKeyHash;
        Instant now = Instant.now(clock);
        TransitionResponse response = gateway.transact(
                cleanReportId,
                cleanOwnerUid,
                eventId,
                requestFingerprint,
                new TransitionAttempt(
                        validated.toStatus(),
                        validated.latitude(),
                        validated.longitude(),
                        validated.dedupeOverride()),
                (report, dedupe) -> plan(
                        cleanOwnerUid,
                        cleanReportId,
                        report,
                        dedupe,
                        validated,
                        eventId,
                        idempotencyKeyHash,
                        requestFingerprint,
                        now));
        try {
            analyticsPublisher.publishPending(response.analyticsOutboxId());
        } catch (RuntimeException exception) {
            LOGGER.warn("Lifecycle analytics delivery remains pending for {}", response.analyticsOutboxId(), exception);
        }
        return response;
    }

    TransitionPlan plan(
            String ownerUid,
            String reportId,
            Map<String, Object> report,
            ReportDedupeEvaluator.DedupeResult dedupe,
            ValidatedRequest request,
            String eventId,
            String idempotencyKeyHash,
            String requestFingerprint,
            Instant now) {
        if (report == null) throw new LifecycleException("REPORT_NOT_FOUND", "Report was not found");
        if (!ownerUid.equals(string(report, "ownerUid"))) {
            throw new LifecycleException("REPORT_FORBIDDEN", "The authenticated user does not own this report");
        }
        ReportLifecycleContract.ReportStatus from = parseStatus(string(report, "status"));
        ReportLifecycleContract.ReportStatus to = request.toStatus();
        if (!ReportLifecycleContract.allows(from, to)) {
            throw new LifecycleException(
                    "INVALID_TRANSITION", "Lifecycle transition is not allowed: " + from + " -> " + to);
        }
        ReportLifecycleContract.EventType eventType = ReportLifecycleContract.eventType(from, to);
        ReportLifecycleContract.VerificationBasis basis = basisFor(to, request.verificationBasis());
        validateEvidence(basis, request.evidenceReference());
        validateFilingFields(to, request);

        Map<String, Object> routeSnapshot;
        String routeSnapshotHash;
        if (from == ReportLifecycleContract.ReportStatus.DRAFT) {
            routeSnapshot = freezeRoute(report);
            routeSnapshotHash = hashJson(routeSnapshot);
        } else {
            routeSnapshot = nestedMap(report.get("routeSnapshot"));
            routeSnapshotHash = string(report, "routeSnapshotHash");
            if (routeSnapshot == null || routeSnapshotHash == null || routeSnapshotHash.isBlank()) {
                throw new LifecycleException(
                        "ROUTE_SNAPSHOT_MISSING", "A filed report must retain its immutable route snapshot");
            }
        }
        if (to == ReportLifecycleContract.ReportStatus.OVERDUE) {
            ensureOverdueEligible(routeSnapshot, now);
        }
        if (to == ReportLifecycleContract.ReportStatus.FILED) {
            validateFilingChannel(routeSnapshot, request.filingChannelId());
        }

        String packVersion = string(routeSnapshot, "packVersion");
        Date occurredAt = Date.from(now);
        Map<String, Object> dedupeEvaluation = null;
        if (to == ReportLifecycleContract.ReportStatus.FILED) {
            dedupeEvaluation = dedupeEvaluation(
                    report, ownerUid, reportId, idempotencyKeyHash, requestFingerprint, request, dedupe, occurredAt);
            if (dedupe.possibleDuplicate() && !request.dedupeOverride()) {
                Map<String, Object> dedupeOutbox = analyticsOutboxForDedupe(dedupeEvaluation, now);
                TransitionResponse blocked = new TransitionResponse(
                        "POSSIBLE_DUPLICATE",
                        null,
                        reportId,
                        from.name(),
                        from.name(),
                        null,
                        ReportLifecycleContract.VerificationBasis.NONE.name(),
                        ReportLifecycleContract.SCHEMA_VERSION,
                        packVersion,
                        routeSnapshotHash,
                        now.toString(),
                        false,
                        dedupe.disposition(),
                        dedupe.measuredDistanceMeters(),
                        0,
                        0.0,
                        string(dedupeOutbox, "outboxId"));
                return new TransitionPlan(Map.of(), null, null, dedupeEvaluation,
                        dedupeOutbox, blocked);
            }
        }

        int basePoints = 0;
        double pointsWeight = 0.0;
        int awardedPoints = 0;
        String pointsReason = null;
        if (to == ReportLifecycleContract.ReportStatus.FILED) {
            basePoints = 5;
            pointsWeight = dedupe != null && "OVERRIDDEN_POSSIBLE_DUPLICATE".equals(dedupe.disposition()) ? 0.0 : 1.0;
            awardedPoints = (int) Math.round(basePoints * pointsWeight);
            pointsReason = "REPORT_FILED";
        } else if (to == ReportLifecycleContract.ReportStatus.VERIFIED_FIXED
                && !Boolean.TRUE.equals(report.get("verifiedPointsAwarded"))) {
            basePoints = 60;
            pointsWeight = 1.0;
            awardedPoints = 60;
            pointsReason = "FIX_VERIFIED";
        }
        Map<String, Object> reportUpdates = new LinkedHashMap<>();
        reportUpdates.put("status", to.name());
        reportUpdates.put("updatedAt", occurredAt);
        reportUpdates.put("lifecycleSchemaVersion", ReportLifecycleContract.SCHEMA_VERSION);
        reportUpdates.put("latestLifecycleEventId", eventId);
        if (to == ReportLifecycleContract.ReportStatus.FILED) {
            reportUpdates.put("filedAt", occurredAt);
            reportUpdates.put("filingChannelId", request.filingChannelId());
            reportUpdates.put("acknowledgementId", request.acknowledgementId());
            reportUpdates.put("routeSnapshot", routeSnapshot);
            reportUpdates.put("routeSnapshotHash", routeSnapshotHash);
            reportUpdates.put("routeSnapshotSchemaVersion", ReportLifecycleContract.ROUTE_SNAPSHOT_SCHEMA_VERSION);
            reportUpdates.put("routeFactsFrozenAt", occurredAt);
            reportUpdates.put("overdueEligibility", "OVERDUE_UNKNOWN");
            reportUpdates.put("dedupeDisposition", dedupe.disposition());
            reportUpdates.put("dedupeMeasuredDistanceMeters", dedupe.measuredDistanceMeters());
            reportUpdates.put("dedupeThresholdMeters", dedupe.thresholdMeters());
            reportUpdates.put("dedupeHeuristicVersion", dedupe.heuristicVersion());
            reportUpdates.put("dedupeLatitude", request.latitude());
            reportUpdates.put("dedupeLongitude", request.longitude());
            reportUpdates.put("filedPointsAwarded", true);
        } else if (to == ReportLifecycleContract.ReportStatus.OVERDUE) {
            reportUpdates.put("overdueAt", occurredAt);
        } else if (to == ReportLifecycleContract.ReportStatus.CLAIMED_FIXED) {
            reportUpdates.put("claimedFixedAt", occurredAt);
        } else if (to == ReportLifecycleContract.ReportStatus.VERIFIED_FIXED) {
            reportUpdates.put("verifiedFixedAt", occurredAt);
            if (pointsReason != null) reportUpdates.put("verifiedPointsAwarded", true);
        } else if (to == ReportLifecycleContract.ReportStatus.REOPENED) {
            reportUpdates.put("reopenedAt", occurredAt);
            reportUpdates.put("reopenEventType", eventType.name());
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("reportId", reportId);
        event.put("ownerUid", ownerUid);
        event.put("fromStatus", from.name());
        event.put("toStatus", to.name());
        event.put("eventType", eventType.name());
        event.put("verificationBasis", basis.name());
        event.put("evidenceReference", request.evidenceReference());
        event.put("filingChannelId", request.filingChannelId());
        event.put("acknowledgementId", request.acknowledgementId());
        event.put("note", request.note());
        event.put("occurredAt", occurredAt);
        event.put("schemaVersion", ReportLifecycleContract.SCHEMA_VERSION);
        event.put("packVersion", packVersion);
        event.put("routeSnapshotHash", routeSnapshotHash);
        event.put("idempotencyKeyHash", idempotencyKeyHash);
        event.put("requestFingerprint", requestFingerprint);
        event.put("dedupeDisposition", dedupe == null ? null : dedupe.disposition());
        event.put("dedupeMeasuredDistanceMeters", dedupe == null ? null : dedupe.measuredDistanceMeters());
        event.put("pointsAwarded", awardedPoints);

        Map<String, Object> pointsEntry = pointsReason == null
                ? null
                : pointsEntry(ownerUid, reportId, eventId, eventType, pointsReason,
                        basePoints, pointsWeight, awardedPoints, occurredAt, packVersion, report);
        String pointsEntryId = pointsReason == null
                ? null
                : "pts_" + sha256(reportId + ":" + pointsReason);
        Map<String, Object> analyticsOutbox = analyticsOutboxForLifecycle(
                report, reportId, ownerUid, event, dedupe, awardedPoints, now);

        TransitionResponse response = new TransitionResponse(
                "TRANSITION_RECORDED",
                eventId,
                reportId,
                from.name(),
                to.name(),
                eventType.name(),
                basis.name(),
                ReportLifecycleContract.SCHEMA_VERSION,
                packVersion,
                routeSnapshotHash,
                now.toString(),
                false,
                dedupe == null ? null : dedupe.disposition(),
                dedupe == null ? null : dedupe.measuredDistanceMeters(),
                awardedPoints,
                pointsWeight,
                "analytics_" + eventId);
        return new TransitionPlan(
                Collections.unmodifiableMap(new LinkedHashMap<>(reportUpdates)),
                Collections.unmodifiableMap(new LinkedHashMap<>(event)),
                pointsEntry == null ? null : Collections.unmodifiableMap(pointsEntry),
                dedupeEvaluation == null ? null : Collections.unmodifiableMap(dedupeEvaluation),
                Collections.unmodifiableMap(analyticsOutbox),
                response);
    }

    private Map<String, Object> freezeRoute(Map<String, Object> report) {
        String issueType = string(report, "confirmedIssueType");
        String prabhagId = string(report, "prabhagId");
        String recordedRouteId = string(report, "routeId");
        String recordedAuthority = string(report, "authority");
        String recordedPackVersion = string(report, "packVersion");
        CivicRouterService.CivicRouteResponse route = router.route(new CivicRouterService.CivicRouteRequest(
                issueType, prabhagId, null, "SELF_REPORTED", false, null));
        if (!"SUPPORTED_ROUTE".equals(route.status())
                || !route.routeId().equals(recordedRouteId)
                || !route.authority().equals(recordedAuthority)
                || !route.packVersion().equals(recordedPackVersion)) {
            throw new LifecycleException(
                    "ROUTE_SNAPSHOT_MISMATCH", "The draft route no longer matches Civic Pack " + recordedPackVersion);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", ReportLifecycleContract.ROUTE_SNAPSHOT_SCHEMA_VERSION);
        snapshot.put("packVersion", route.packVersion());
        snapshot.put("routeId", route.routeId());
        snapshot.put("issueType", issueType);
        snapshot.put("prabhagId", route.prabhagId());
        snapshot.put("resolutionMethod", firstNonBlank(string(report, "resolutionMethod"), "UNKNOWN_LEGACY_DRAFT"));
        snapshot.put("authority", route.authority());
        snapshot.put("authorityLocalName", route.authorityLocalName());
        snapshot.put("department", departmentMap(route.department()));
        snapshot.put("officialChannels", officialChannelMaps(route.officialChannels()));
        snapshot.put("informationalLinks", informationalLinkMaps(route.informationalLinks()));
        snapshot.put("knownLimitations", limitationMaps(route.knownLimitations()));
        snapshot.put("sla", route.sla());
        snapshot.put("escalation", route.escalation());
        snapshot.put("verifiedDueAt", null);
        snapshot.put("overdueEligibility", "OVERDUE_UNKNOWN");
        snapshot.put("officialSource", sourceMap(route.officialSource()));
        snapshot.put("sourceStatus", route.sourceStatus());
        snapshot.put("reviewStatus", route.reviewStatus());
        return snapshot;
    }

    private static Map<String, Object> departmentMap(CivicRouterService.DepartmentDefinition department) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("departmentId", department.departmentId());
        map.put("displayName", department.displayName());
        map.put("localName", department.localName());
        map.put("status", department.status());
        map.put("basis", department.basis());
        map.put("sourceReference", sourceMap(department.sourceReference()));
        return map;
    }

    private static List<Map<String, Object>> officialChannelMaps(
            List<CivicRouterService.OfficialChannel> channels) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (CivicRouterService.OfficialChannel channel : channels) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("channelId", channel.channelId());
            map.put("type", channel.type());
            map.put("value", channel.value());
            map.put("label", channel.label());
            map.put("scopeNote", channel.scopeNote());
            map.put("sourceStatus", channel.sourceStatus());
            map.put("sourceReference", sourceMap(channel.sourceReference()));
            results.add(map);
        }
        return List.copyOf(results);
    }

    private static List<Map<String, Object>> informationalLinkMaps(
            List<CivicRouterService.InformationalLink> links) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (CivicRouterService.InformationalLink link : links) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("linkId", link.linkId());
            map.put("type", link.type());
            map.put("value", link.value());
            map.put("label", link.label());
            map.put("status", link.status());
            map.put("scopeNote", link.scopeNote());
            results.add(map);
        }
        return List.copyOf(results);
    }

    private static List<Map<String, Object>> limitationMaps(List<CivicRouterService.KnownLimitation> limitations) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (CivicRouterService.KnownLimitation limitation : limitations) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("code", limitation.code());
            map.put("citizenMessage", limitation.citizenMessage());
            map.put("routingImpact", limitation.routingImpact());
            map.put("requiresCitizenAttention", limitation.requiresCitizenAttention());
            results.add(map);
        }
        return List.copyOf(results);
    }

    private static Map<String, Object> sourceMap(CivicRouterService.OfficialSource source) {
        if (source == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", source.title());
        map.put("url", source.url());
        map.put("reference", source.reference());
        return map;
    }

    private static void validateFilingChannel(Map<String, Object> routeSnapshot, String filingChannelId) {
        if (filingChannelId == null) return;
        Object channelsValue = routeSnapshot.get("officialChannels");
        if (!(channelsValue instanceof List<?> channels)) {
            throw new LifecycleException("INVALID_FILING_CHANNEL", "The route has no official filing channels");
        }
        boolean found = channels.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .anyMatch(channel -> filingChannelId.equals(String.valueOf(channel.get("channelId"))));
        if (!found) {
            throw new LifecycleException(
                    "INVALID_FILING_CHANNEL", "The filing channel is not part of the frozen Civic Pack route");
        }
    }

    private static void ensureOverdueEligible(Map<String, Object> routeSnapshot, Instant now) {
        Object dueAtValue = routeSnapshot.get("verifiedDueAt");
        if (dueAtValue == null) {
            throw new LifecycleException(
                    "OVERDUE_NOT_ELIGIBLE", "No verified due date exists; overdue status remains unknown");
        }
        Instant dueAt = instant(dueAtValue);
        if (dueAt == null) {
            throw new LifecycleException("OVERDUE_NOT_ELIGIBLE", "The verified due date is invalid");
        }
        if (now.isBefore(dueAt)) {
            throw new LifecycleException("OVERDUE_NOT_REACHED", "The verified due date has not passed");
        }
    }

    private static Instant instant(Object value) {
        if (value instanceof com.google.cloud.Timestamp timestamp) return timestamp.toDate().toInstant();
        if (value instanceof Date date) return date.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof String string) {
            try {
                return Instant.parse(string);
            } catch (java.time.format.DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private static ReportLifecycleContract.VerificationBasis basisFor(
            ReportLifecycleContract.ReportStatus to,
            ReportLifecycleContract.VerificationBasis requested) {
        if (to == ReportLifecycleContract.ReportStatus.FILED) {
            if (requested != null
                    && requested != ReportLifecycleContract.VerificationBasis.NONE
                    && requested != ReportLifecycleContract.VerificationBasis.CITIZEN_ATTESTATION) {
                throw new LifecycleException(
                        "INVALID_VERIFICATION_BASIS", "Filing is recorded as a citizen attestation");
            }
            return ReportLifecycleContract.VerificationBasis.CITIZEN_ATTESTATION;
        }
        if (to == ReportLifecycleContract.ReportStatus.OVERDUE) {
            if (requested != null && requested != ReportLifecycleContract.VerificationBasis.NONE) {
                throw new LifecycleException(
                        "INVALID_VERIFICATION_BASIS", "Overdue status is derived only from a verified due date");
            }
            return ReportLifecycleContract.VerificationBasis.NONE;
        }
        if (requested == null || requested == ReportLifecycleContract.VerificationBasis.NONE) {
            throw new LifecycleException(
                    "VERIFICATION_BASIS_REQUIRED", "This lifecycle transition requires a verification basis");
        }
        return requested;
    }

    private static void validateEvidence(
            ReportLifecycleContract.VerificationBasis basis, String evidenceReference) {
        if ((basis == ReportLifecycleContract.VerificationBasis.CITIZEN_PHOTO
                        || basis == ReportLifecycleContract.VerificationBasis.MUNICIPAL_ACKNOWLEDGEMENT)
                && evidenceReference == null) {
            throw new LifecycleException(
                    "EVIDENCE_REFERENCE_REQUIRED", "The selected verification basis requires an evidence reference");
        }
    }

    private static void validateFilingFields(
            ReportLifecycleContract.ReportStatus to, ValidatedRequest request) {
        if (to != ReportLifecycleContract.ReportStatus.FILED
                && (request.filingChannelId() != null
                        || request.acknowledgementId() != null
                        || request.latitude() != null
                        || request.longitude() != null
                        || request.dedupeOverride())) {
            throw new LifecycleException(
                    "FILING_FIELDS_NOT_ALLOWED", "Filing and dedupe fields belong only to FILED");
        }
    }

    private static Map<String, Object> dedupeEvaluation(
            Map<String, Object> report,
            String ownerUid,
            String reportId,
            String idempotencyKeyHash,
            String requestFingerprint,
            ValidatedRequest request,
            ReportDedupeEvaluator.DedupeResult dedupe,
            Date occurredAt) {
        Map<String, Object> evaluation = new LinkedHashMap<>();
        evaluation.put("evaluationId", "dedupe_" + idempotencyKeyHash);
        evaluation.put("reportId", reportId);
        evaluation.put("ownerUid", ownerUid);
        evaluation.put("issueType", string(report, "confirmedIssueType"));
        evaluation.put("prabhagId", string(report, "prabhagId"));
        evaluation.put("candidateReportId", dedupe.candidateReportId());
        evaluation.put("disposition", dedupe.disposition());
        evaluation.put("measuredDistanceMeters", dedupe.measuredDistanceMeters());
        evaluation.put("thresholdMeters", dedupe.thresholdMeters());
        evaluation.put("heuristicVersion", dedupe.heuristicVersion());
        evaluation.put("overrideRequested", request.dedupeOverride());
        evaluation.put("latitudeProvided", request.latitude() != null);
        evaluation.put("occurredAt", occurredAt);
        evaluation.put("schemaVersion", "dedupe-evaluation-v0.1");
        evaluation.put("requestFingerprint", requestFingerprint);
        return evaluation;
    }

    private static Map<String, Object> pointsEntry(
            String ownerUid,
            String reportId,
            String eventId,
            ReportLifecycleContract.EventType eventType,
            String reason,
            int basePoints,
            double weight,
            int awardedPoints,
            Date occurredAt,
            String packVersion,
            Map<String, Object> report) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ledgerEntryId", "pts_" + sha256(reportId + ":" + reason));
        entry.put("ownerUid", ownerUid);
        entry.put("sourceType", "REPORT");
        entry.put("sourceId", reportId);
        entry.put("reportId", reportId);
        entry.put("triggerEventId", eventId);
        entry.put("triggeringEventId", eventId);
        entry.put("triggeringEvent", eventType.name());
        entry.put("reason", reason);
        entry.put("basePoints", basePoints);
        entry.put("weight", weight);
        entry.put("awardedPoints", awardedPoints);
        entry.put("pointsAwarded", awardedPoints);
        entry.put("policyStatus", awardedPoints > 0 ? "AWARDED" : "RECORDED_NOT_REWARDED");
        entry.put("occurredAt", occurredAt);
        entry.put("packVersion", packVersion);
        entry.put("lifecycleSchemaVersion", ReportLifecycleContract.SCHEMA_VERSION);
        entry.put("schemaVersion", InitiativeService.LEDGER_SCHEMA_VERSION);
        entry.put("rewardPolicyVersion", InitiativeService.REWARD_POLICY_VERSION);
        entry.put("demoMode", Boolean.TRUE.equals(report.get("demoMode")));
        return entry;
    }

    private static Map<String, Object> analyticsOutboxForLifecycle(
            Map<String, Object> report,
            String reportId,
            String ownerUid,
            Map<String, Object> event,
            ReportDedupeEvaluator.DedupeResult dedupe,
            int awardedPoints,
            Instant now) {
        String eventId = string(event, "eventId");
        Map<String, Object> outbox = new LinkedHashMap<>();
        outbox.put("outboxId", "analytics_" + eventId);
        outbox.put("recordType", "LIFECYCLE_EVENT");
        outbox.put("eventId", eventId);
        outbox.put("reportIdHash", sha256(reportId));
        outbox.put("ownerIdHash", sha256(ownerUid));
        outbox.put("fromStatus", event.get("fromStatus"));
        outbox.put("toStatus", event.get("toStatus"));
        outbox.put("eventType", event.get("eventType"));
        outbox.put("verificationBasis", event.get("verificationBasis"));
        outbox.put("issueType", firstNonBlank(string(report, "confirmedIssueType"), "UNKNOWN"));
        outbox.put("prabhagId", firstNonBlank(string(report, "prabhagId"), "UNKNOWN"));
        outbox.put("occurredAt", event.get("occurredAt"));
        outbox.put("packVersion", event.get("packVersion"));
        outbox.put("schemaVersion", event.get("schemaVersion"));
        outbox.put("routeSnapshotHash", event.get("routeSnapshotHash"));
        outbox.put("demoMode", Boolean.TRUE.equals(report.get("demoMode")));
        outbox.put("pointsAwarded", awardedPoints);
        outbox.put("dedupeDisposition", dedupe == null ? null : dedupe.disposition());
        outbox.put("dedupeDistanceMeters", dedupe == null ? null : dedupe.measuredDistanceMeters());
        outbox.put("overdueEligible", "OVERDUE_REACHED".equals(event.get("eventType")));
        outbox.put("deliveryStatus", "PENDING");
        outbox.put("createdAt", Date.from(now));
        return outbox;
    }

    private static Map<String, Object> analyticsOutboxForDedupe(Map<String, Object> evaluation, Instant now) {
        Map<String, Object> outbox = new LinkedHashMap<>();
        String evaluationId = string(evaluation, "evaluationId");
        outbox.put("outboxId", "analytics_" + evaluationId);
        outbox.put("recordType", "DEDUPE_EVALUATION");
        outbox.put("evaluationId", evaluationId);
        outbox.put("reportIdHash", sha256(string(evaluation, "reportId")));
        outbox.put("ownerIdHash", sha256(string(evaluation, "ownerUid")));
        outbox.put("issueType", evaluation.get("issueType"));
        outbox.put("prabhagId", evaluation.get("prabhagId"));
        Object candidate = evaluation.get("candidateReportId");
        outbox.put("candidateReportIdHash", candidate == null ? null : sha256(candidate.toString()));
        outbox.put("disposition", evaluation.get("disposition"));
        outbox.put("measuredDistanceMeters", evaluation.get("measuredDistanceMeters"));
        outbox.put("thresholdMeters", evaluation.get("thresholdMeters"));
        outbox.put("heuristicVersion", evaluation.get("heuristicVersion"));
        outbox.put("occurredAt", evaluation.get("occurredAt"));
        outbox.put("demoMode", false);
        outbox.put("deliveryStatus", "PENDING");
        outbox.put("createdAt", Date.from(now));
        return outbox;
    }

    private ValidatedRequest validate(TransitionRequest request) {
        if (request == null) throw new LifecycleException("EMPTY_REQUEST", "Lifecycle transition request is required");
        ReportLifecycleContract.ReportStatus toStatus;
        try {
            toStatus = ReportLifecycleContract.ReportStatus.valueOf(
                    required(request.toStatus(), "TARGET_STATUS_REQUIRED", "Target status is required", 40)
                            .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new LifecycleException("INVALID_TARGET_STATUS", "Target lifecycle status is invalid");
        }
        ReportLifecycleContract.VerificationBasis verificationBasis = null;
        if (request.verificationBasis() != null && !request.verificationBasis().isBlank()) {
            try {
                verificationBasis = ReportLifecycleContract.VerificationBasis.valueOf(
                        request.verificationBasis().strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new LifecycleException("INVALID_VERIFICATION_BASIS", "Verification basis is invalid");
            }
        }
        Double latitude = request.latitude();
        Double longitude = request.longitude();
        if ((latitude == null) != (longitude == null)) {
            throw new LifecycleException("INCOMPLETE_COORDINATES", "Latitude and longitude must be provided together");
        }
        if (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180)) {
            throw new LifecycleException("INVALID_COORDINATES", "Coordinates are outside valid latitude/longitude ranges");
        }
        if (request.dedupeOverride() && latitude == null) {
            throw new LifecycleException("DEDUPE_OVERRIDE_NOT_ALLOWED", "A duplicate override requires a measured match");
        }
        return new ValidatedRequest(
                toStatus,
                required(
                        request.idempotencyKey(),
                        "IDEMPOTENCY_KEY_REQUIRED",
                        "An idempotency key is required",
                        MAX_IDEMPOTENCY_KEY_LENGTH),
                verificationBasis,
                optional(request.filingChannelId(), 120, "FILING_CHANNEL_TOO_LONG"),
                optional(request.acknowledgementId(), MAX_ACKNOWLEDGEMENT_ID_LENGTH, "ACKNOWLEDGEMENT_TOO_LONG"),
                optional(request.evidenceReference(), MAX_EVIDENCE_REFERENCE_LENGTH, "EVIDENCE_REFERENCE_TOO_LONG"),
                optional(request.note(), MAX_NOTE_LENGTH, "NOTE_TOO_LONG"),
                latitude,
                longitude,
                request.dedupeOverride());
    }

    private String fingerprint(String reportId, String ownerUid, ValidatedRequest request) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("reportId", reportId);
        canonical.put("ownerUid", ownerUid);
        canonical.put("toStatus", request.toStatus().name());
        canonical.put("verificationBasis", request.verificationBasis() == null
                ? null
                : request.verificationBasis().name());
        canonical.put("filingChannelId", request.filingChannelId());
        canonical.put("acknowledgementId", request.acknowledgementId());
        canonical.put("evidenceReference", request.evidenceReference());
        canonical.put("note", request.note());
        canonical.put("latitude", request.latitude());
        canonical.put("longitude", request.longitude());
        canonical.put("dedupeOverride", request.dedupeOverride());
        return hashJson(canonical);
    }

    private String hashJson(Map<String, Object> value) {
        try {
            return sha256(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new LifecycleException("LIFECYCLE_SERIALIZATION_FAILED", "Lifecycle data could not be serialized", exception);
        }
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ReportLifecycleContract.ReportStatus parseStatus(String value) {
        try {
            return ReportLifecycleContract.ReportStatus.valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new LifecycleException("INVALID_CURRENT_STATUS", "Stored report status is invalid");
        }
    }

    private static String required(String value, String code, String message, int maxLength) {
        if (value == null || value.isBlank()) throw new LifecycleException(code, message);
        String clean = value.strip();
        if (clean.length() > maxLength) throw new LifecycleException(code, message);
        return clean;
    }

    private static String optional(String value, int maxLength, String code) {
        if (value == null || value.isBlank()) return null;
        String clean = value.strip();
        if (clean.length() > maxLength) throw new LifecycleException(code, "Lifecycle field is too long");
        return clean;
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return null;
        return (Map<String, Object>) map;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    public record TransitionRequest(
            String toStatus,
            String idempotencyKey,
            String verificationBasis,
            String filingChannelId,
            String acknowledgementId,
            String evidenceReference,
            String note,
            Double latitude,
            Double longitude,
            boolean dedupeOverride) {
        public TransitionRequest(
                String toStatus,
                String idempotencyKey,
                String verificationBasis,
                String filingChannelId,
                String acknowledgementId,
                String evidenceReference,
                String note) {
            this(toStatus, idempotencyKey, verificationBasis, filingChannelId, acknowledgementId,
                    evidenceReference, note, null, null, false);
        }
    }

    record ValidatedRequest(
            ReportLifecycleContract.ReportStatus toStatus,
            String idempotencyKey,
            ReportLifecycleContract.VerificationBasis verificationBasis,
            String filingChannelId,
            String acknowledgementId,
            String evidenceReference,
            String note,
            Double latitude,
            Double longitude,
            boolean dedupeOverride) {}

    public record TransitionAttempt(
            ReportLifecycleContract.ReportStatus toStatus,
            Double latitude,
            Double longitude,
            boolean dedupeOverride) {}

    public record TransitionResponse(
            String status,
            String eventId,
            String reportId,
            String fromStatus,
            String toStatus,
            String eventType,
            String verificationBasis,
            String schemaVersion,
            String packVersion,
            String routeSnapshotHash,
            String occurredAt,
            boolean idempotentReplay,
            String dedupeDisposition,
            Double measuredDistanceMeters,
            int pointsAwarded,
            double pointsWeight,
            String analyticsOutboxId) {
        public TransitionResponse(
                String status,
                String eventId,
                String reportId,
                String fromStatus,
                String toStatus,
                String eventType,
                String verificationBasis,
                String schemaVersion,
                String packVersion,
                String routeSnapshotHash,
                String occurredAt,
                boolean idempotentReplay) {
            this(status, eventId, reportId, fromStatus, toStatus, eventType, verificationBasis,
                    schemaVersion, packVersion, routeSnapshotHash, occurredAt, idempotentReplay,
                    null, null, 0, 0.0, null);
        }
    }

    public record TransitionPlan(
            Map<String, Object> reportUpdates,
            Map<String, Object> event,
            Map<String, Object> pointsLedgerEntry,
            Map<String, Object> dedupeEvaluation,
            Map<String, Object> analyticsOutbox,
            TransitionResponse response) {
        public TransitionPlan(
                Map<String, Object> reportUpdates,
                Map<String, Object> event,
                TransitionResponse response) {
            this(reportUpdates, event, null, null, null, response);
        }
    }

    public static final class LifecycleException extends RuntimeException {
        private final String code;

        LifecycleException(String code, String message) {
            super(message);
            this.code = code;
        }

        LifecycleException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
