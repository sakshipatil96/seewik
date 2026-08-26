package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportLifecycleServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private InMemoryGateway gateway;
    private ReportLifecycleService service;

    @BeforeEach
    void setUp() throws Exception {
        gateway = new InMemoryGateway(draft());
        service = new ReportLifecycleService(
                gateway,
                new CivicRouterService(new ObjectMapper()),
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void filingFreezesTheCompleteRouteAndRecordsCitizenAttestation() {
        var response = service.transition("owner-1", "report-1", filed("file-once"));

        assertEquals("FILED", response.toStatus());
        assertEquals("REPORT_FILED", response.eventType());
        assertEquals("CITIZEN_ATTESTATION", response.verificationBasis());
        assertEquals("v0.2", response.packVersion());
        assertFalse(response.idempotentReplay());
        assertNotNull(response.routeSnapshotHash());
        assertEquals(5, response.pointsAwarded());
        assertEquals(1.0, response.pointsWeight());
        assertEquals("DEDUPE_NOT_EVALUATED", response.dedupeDisposition());

        Map<String, Object> report = gateway.report();
        assertEquals("FILED", report.get("status"));
        assertEquals("OVERDUE_UNKNOWN", report.get("overdueEligibility"));
        assertNull(report.get("acknowledgementId"));
        Map<String, Object> snapshot = nested(report, "routeSnapshot");
        assertEquals("route-snapshot-v0.1", snapshot.get("schemaVersion"));
        assertEquals("NMC-PW-POTHOLE-v0.2", snapshot.get("routeId"));
        assertEquals("Nandurbar Municipal Council", snapshot.get("authority"));
        assertEquals("UNKNOWN_LEGACY_DRAFT", snapshot.get("resolutionMethod"));
        assertEquals("OFFICIAL_SOURCE", snapshot.get("sourceStatus"));
        assertEquals("REVIEW_PENDING", snapshot.get("reviewStatus"));
        assertNull(snapshot.get("verifiedDueAt"));
        assertFalse(((java.util.List<?>) snapshot.get("officialChannels")).isEmpty());
        assertEquals(1, gateway.pointsEntryCount());
        assertEquals(5, gateway.lastPointsEntry().get("awardedPoints"));
    }

    @Test
    void possibleDuplicateIsBlockedAndOverrideAwardsZeroFilingPoints() {
        Map<String, Object> existing = draft();
        existing.put("_documentId", "existing");
        existing.put("status", "FILED");
        existing.put("dedupeLatitude", 21.370000);
        existing.put("dedupeLongitude", 74.240000);
        gateway = new InMemoryGateway(draft(), java.util.List.of(existing));
        service = service(gateway);

        var blocked = new ReportLifecycleService.TransitionRequest(
                "FILED", "duplicate-block", null, "EMAIL_NMC", null, null, null,
                21.370100, 74.240000, false);
        var blockedResponse = service.transition("owner-1", "report-1", blocked);
        assertEquals("POSSIBLE_DUPLICATE", blockedResponse.status());
        assertEquals("DRAFT", gateway.report().get("status"));
        assertEquals(0, gateway.eventCount());

        var override = new ReportLifecycleService.TransitionRequest(
                "FILED", "duplicate-override", null, "EMAIL_NMC", null, null, null,
                21.370100, 74.240000, true);
        var response = service.transition("owner-1", "report-1", override);
        assertEquals("FILED", response.toStatus());
        assertEquals("OVERRIDDEN_POSSIBLE_DUPLICATE", response.dedupeDisposition());
        assertEquals(0, response.pointsAwarded());
        assertEquals(0.0, response.pointsWeight());
        assertEquals(5, gateway.lastPointsEntry().get("basePoints"));
        assertEquals(0, gateway.lastPointsEntry().get("awardedPoints"));
    }

    @Test
    void verifiedPointsAreAwardedOnlyOnceAcrossReopenAndReverification() {
        gateway = new InMemoryGateway(filedReport("CLAIMED_FIXED", null));
        service = service(gateway);
        var first = service.transition(
                "owner-1", "report-1", transition("VERIFIED_FIXED", "verify-1", "CITIZEN_ATTESTATION", null));
        assertEquals(40, first.pointsAwarded());

        service.transition("owner-1", "report-1", transition("REOPENED", "recur", "CITIZEN_ATTESTATION", null));
        service.transition("owner-1", "report-1", transition("CLAIMED_FIXED", "claim-2", "CITIZEN_ATTESTATION", null));
        var second = service.transition(
                "owner-1", "report-1", transition("VERIFIED_FIXED", "verify-2", "CITIZEN_ATTESTATION", null));
        assertEquals(0, second.pointsAwarded());
        assertEquals(1, gateway.pointsEntryCount());
    }

    @Test
    void sameIdempotencyKeyAndRequestReturnTheOriginalEvent() {
        var first = service.transition("owner-1", "report-1", filed("same-key"));
        var second = service.transition("owner-1", "report-1", filed("same-key"));
        assertEquals(first.eventId(), second.eventId());
        assertTrue(second.idempotentReplay());
        assertEquals(1, gateway.eventCount());
    }

    @Test
    void reusingAnIdempotencyKeyForDifferentInputIsRejected() {
        service.transition("owner-1", "report-1", filed("same-key"));
        var changed = new ReportLifecycleService.TransitionRequest(
                "FILED", "same-key", null, "EMAIL_NMC", "ACK-2", null, null);
        assertCode(() -> service.transition("owner-1", "report-1", changed), "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void ownerMismatchIsRejected() {
        assertCode(() -> service.transition("owner-2", "report-1", filed("owner-check")), "REPORT_FORBIDDEN");
    }

    @Test
    void directVerifiedFixedTransitionIsRejected() {
        var request = transition("VERIFIED_FIXED", "skip-claim", "CITIZEN_ATTESTATION", null);
        assertCode(() -> service.transition("owner-1", "report-1", request), "INVALID_TRANSITION");
    }

    @Test
    void verifiedFixedRequiresANonNoneBasis() {
        gateway = new InMemoryGateway(filedReport("CLAIMED_FIXED", null));
        service = service(gateway);
        assertCode(
                () -> service.transition("owner-1", "report-1", transition("VERIFIED_FIXED", "verify", "NONE", null)),
                "VERIFICATION_BASIS_REQUIRED");
    }

    @Test
    void photoBasisRequiresEvidenceReference() {
        gateway = new InMemoryGateway(filedReport("CLAIMED_FIXED", null));
        service = service(gateway);
        assertCode(
                () -> service.transition(
                        "owner-1", "report-1", transition("VERIFIED_FIXED", "verify-photo", "CITIZEN_PHOTO", null)),
                "EVIDENCE_REFERENCE_REQUIRED");
    }

    @Test
    void noVerifiedDueDateMeansOverdueIsUnknownNotInvented() {
        gateway = new InMemoryGateway(filedReport("FILED", null));
        service = service(gateway);
        assertCode(
                () -> service.transition("owner-1", "report-1", transition("OVERDUE", "overdue", null, null)),
                "OVERDUE_NOT_ELIGIBLE");
    }

    @Test
    void verifiedPastDueDateAllowsOverdue() {
        gateway = new InMemoryGateway(filedReport("FILED", Date.from(NOW.minusSeconds(60))));
        service = service(gateway);
        var response = service.transition("owner-1", "report-1", transition("OVERDUE", "overdue", null, null));
        assertEquals("OVERDUE", response.toStatus());
        assertEquals("OVERDUE_REACHED", response.eventType());
        assertEquals("NONE", response.verificationBasis());
    }

    @Test
    void laterTransitionsUseTheFrozenRouteWithoutReresolvingCurrentDraftFields() {
        Map<String, Object> report = filedReport("FILED", null);
        report.put("confirmedIssueType", "ALIEN_INVASION");
        report.put("routeId", "changed-after-filing");
        gateway = new InMemoryGateway(report);
        service = service(gateway);
        var response = service.transition(
                "owner-1",
                "report-1",
                transition("CLAIMED_FIXED", "claim", "CITIZEN_ATTESTATION", null));
        assertEquals("CLAIMED_FIXED", response.toStatus());
        assertEquals("frozen-route-hash", response.routeSnapshotHash());
    }

    @Test
    void theTwoReopenBranchesHaveDifferentEventTypes() {
        gateway = new InMemoryGateway(filedReport("CLAIMED_FIXED", null));
        service = service(gateway);
        var rejected = service.transition(
                "owner-1", "report-1", transition("REOPENED", "reject", "CITIZEN_ATTESTATION", null));
        assertEquals("REPAIR_CLAIM_REJECTED", rejected.eventType());

        gateway = new InMemoryGateway(filedReport("VERIFIED_FIXED", null));
        service = service(gateway);
        var recurred = service.transition(
                "owner-1", "report-1", transition("REOPENED", "recur", "CITIZEN_ATTESTATION", null));
        assertEquals("ISSUE_RECURRED", recurred.eventType());
    }

    @Test
    void filingChannelMustComeFromTheFrozenRoute() {
        var request = new ReportLifecycleService.TransitionRequest(
                "FILED", "bad-channel", null, "INVENTED_CHANNEL", null, null, null);
        assertCode(() -> service.transition("owner-1", "report-1", request), "INVALID_FILING_CHANNEL");
    }

    private ReportLifecycleService service(InMemoryGateway value) {
        try {
            return new ReportLifecycleService(
                    value,
                    new CivicRouterService(new ObjectMapper()),
                    new ObjectMapper(),
                    Clock.fixed(NOW, ZoneOffset.UTC));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static ReportLifecycleService.TransitionRequest filed(String key) {
        return new ReportLifecycleService.TransitionRequest(
                "FILED", key, null, "EMAIL_NMC", null, null, null);
    }

    private static ReportLifecycleService.TransitionRequest transition(
            String status, String key, String basis, String evidence) {
        return new ReportLifecycleService.TransitionRequest(status, key, basis, null, null, evidence, null);
    }

    private static Map<String, Object> draft() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("ownerUid", "owner-1");
        report.put("status", "DRAFT");
        report.put("confirmedIssueType", "POTHOLE_ROAD_DAMAGE");
        report.put("prabhagId", "PRABHAG-03");
        report.put("routeId", "NMC-PW-POTHOLE-v0.2");
        report.put("authority", "Nandurbar Municipal Council");
        report.put("packVersion", "v0.2");
        return report;
    }

    private static Map<String, Object> filedReport(String status, Date verifiedDueAt) {
        Map<String, Object> report = draft();
        report.put("status", status);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", "route-snapshot-v0.1");
        snapshot.put("packVersion", "v0.2");
        snapshot.put("routeId", "NMC-PW-POTHOLE-v0.2");
        snapshot.put("verifiedDueAt", verifiedDueAt);
        report.put("routeSnapshot", snapshot);
        report.put("routeSnapshotHash", "frozen-route-hash");
        return report;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> value, String key) {
        return (Map<String, Object>) value.get(key);
    }

    private static void assertCode(Runnable call, String code) {
        var exception = assertThrows(ReportLifecycleService.LifecycleException.class, call::run);
        assertEquals(code, exception.code());
    }

    private static final class InMemoryGateway implements ReportLifecycleGateway {
        private final Map<String, Object> report;
        private final Map<String, Map<String, Object>> events = new LinkedHashMap<>();
        private final java.util.List<Map<String, Object>> candidates;
        private final Map<String, Map<String, Object>> pointsEntries = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> dedupeEvaluations = new LinkedHashMap<>();

        InMemoryGateway(Map<String, Object> report) {
            this(report, java.util.List.of());
        }

        InMemoryGateway(Map<String, Object> report, java.util.List<Map<String, Object>> candidates) {
            this.report = new LinkedHashMap<>(report);
            this.candidates = candidates;
        }

        @Override
        public synchronized ReportLifecycleService.TransitionResponse transact(
                String reportId,
                String ownerUid,
                String eventId,
                String requestFingerprint,
                ReportLifecycleService.TransitionAttempt attempt,
                BiFunction<Map<String, Object>, ReportDedupeEvaluator.DedupeResult,
                        ReportLifecycleService.TransitionPlan> planner) {
            if (!ownerUid.equals(report.get("ownerUid"))) {
                throw new ReportLifecycleService.LifecycleException(
                        "REPORT_FORBIDDEN", "The authenticated user does not own this report");
            }
            Map<String, Object> existing = events.get(eventId);
            if (existing != null) {
                if (!requestFingerprint.equals(existing.get("requestFingerprint"))) {
                    throw new ReportLifecycleService.LifecycleException(
                            "IDEMPOTENCY_KEY_REUSED", "Idempotency key was reused");
                }
                ReportLifecycleService.TransitionResponse response =
                        (ReportLifecycleService.TransitionResponse) existing.get("_response");
                return new ReportLifecycleService.TransitionResponse(
                        response.status(), response.eventId(), response.reportId(), response.fromStatus(),
                        response.toStatus(), response.eventType(), response.verificationBasis(),
                        response.schemaVersion(), response.packVersion(), response.routeSnapshotHash(),
                        response.occurredAt(), true);
            }
            ReportDedupeEvaluator.DedupeResult dedupe = new ReportDedupeEvaluator().evaluate(
                    reportId,
                    report,
                    candidates,
                    attempt.latitude(),
                    attempt.longitude(),
                    attempt.dedupeOverride());
            ReportLifecycleService.TransitionPlan plan = planner.apply(new LinkedHashMap<>(report), dedupe);
            if (plan.event() == null) {
                dedupeEvaluations.put(String.valueOf(plan.dedupeEvaluation().get("evaluationId")),
                        new LinkedHashMap<>(plan.dedupeEvaluation()));
                return plan.response();
            }
            report.putAll(plan.reportUpdates());
            Map<String, Object> event = new LinkedHashMap<>(plan.event());
            event.put("_response", plan.response());
            events.put(eventId, event);
            if (plan.pointsLedgerEntry() != null) {
                pointsEntries.put(String.valueOf(plan.pointsLedgerEntry().get("ledgerEntryId")),
                        new LinkedHashMap<>(plan.pointsLedgerEntry()));
            }
            if (plan.dedupeEvaluation() != null) {
                dedupeEvaluations.put(String.valueOf(plan.dedupeEvaluation().get("evaluationId")),
                        new LinkedHashMap<>(plan.dedupeEvaluation()));
            }
            return plan.response();
        }

        Map<String, Object> report() {
            return report;
        }

        int eventCount() {
            return events.size();
        }

        int pointsEntryCount() {
            return pointsEntries.size();
        }

        Map<String, Object> lastPointsEntry() {
            return pointsEntries.values().stream().reduce((first, second) -> second).orElseThrow();
        }
    }
}
