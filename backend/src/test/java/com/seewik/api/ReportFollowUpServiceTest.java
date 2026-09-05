package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportFollowUpServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");

    @Test
    void serverTimePreventsAnEarlyFollowUp() {
        InMemoryGateway gateway = new InMemoryGateway(report(NOW.minusSeconds(6 * 86_400L)), List.of());
        var service = service(gateway);
        assertFalse(service.get("owner-1", "report-1").promptDue());
        var error = assertThrows(ReportFollowUpService.FollowUpException.class, () -> service.record(
                "owner-1", "report-1", new ReportFollowUpService.FollowUpRequest("UNRESOLVED", "key-1", null, null)));
        assertEquals("FOLLOW_UP_NOT_DUE", error.code());
    }

    @Test
    void unresolvedAnswerUnlocksEscalationWithoutPoints() {
        InMemoryGateway gateway = new InMemoryGateway(report(NOW.minusSeconds(8 * 86_400L)), List.of());
        var response = service(gateway).record(
                "owner-1", "report-1", new ReportFollowUpService.FollowUpRequest("UNRESOLVED", "key-1", null, null));
        assertTrue(response.summary().escalationAvailable());
        assertEquals(0, response.summary().events().getFirst().pointsAwarded());
    }

    @Test
    void unsureUsesAThreeDayServerReminder() {
        InMemoryGateway gateway = new InMemoryGateway(report(NOW.minusSeconds(8 * 86_400L)), List.of());
        var response = service(gateway).record(
                "owner-1", "report-1", new ReportFollowUpService.FollowUpRequest("UNSURE", "key-1", null, null));
        assertEquals("SNOOZED", response.summary().state());
        assertEquals("2026-09-08T12:00:00Z", response.summary().nextPromptAt());
    }

    @Test
    void verifiedRecurrenceStartsANewServerAnchoredCycle() {
        Map<String, Object> reopen = new LinkedHashMap<>();
        reopen.put("fromStatus", "VERIFIED_FIXED");
        reopen.put("toStatus", "REOPENED");
        reopen.put("occurredAt", Date.from(NOW.minusSeconds(2 * 86_400L)));
        InMemoryGateway gateway = new InMemoryGateway(report(NOW.minusSeconds(30 * 86_400L)), List.of(reopen));
        var summary = service(gateway).get("owner-1", "report-1");
        assertEquals(2, summary.cycleNumber());
        assertTrue(summary.recurrence());
        assertFalse(summary.promptDue());
        assertEquals("2026-09-10T12:00:00Z", summary.followUpDueAt());
    }

    @Test
    void rejectedRepairClaimResumesThePromptImmediately() {
        Map<String, Object> reopen = new LinkedHashMap<>();
        reopen.put("fromStatus", "CLAIMED_FIXED");
        reopen.put("toStatus", "REOPENED");
        reopen.put("occurredAt", Date.from(NOW));
        InMemoryGateway gateway = new InMemoryGateway(report(NOW.minusSeconds(2 * 86_400L)), List.of(reopen));
        assertTrue(service(gateway).get("owner-1", "report-1").promptDue());
    }

    private static ReportFollowUpService service(InMemoryGateway gateway) {
        return new ReportFollowUpService(gateway, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Map<String, Object> report(Instant filedAt) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("ownerUid", "owner-1");
        report.put("status", "FILED");
        report.put("filedAt", Date.from(filedAt));
        report.put("routeId", "NMC-PW-POTHOLE-v0.2");
        report.put("packVersion", "v0.2");
        report.put("routeSnapshotHash", "route-hash");
        return report;
    }

    private static final class InMemoryGateway implements ReportFollowUpGateway {
        private final Map<String, Object> report;
        private final List<Map<String, Object>> lifecycle;
        private final List<Map<String, Object>> followUps = new ArrayList<>();

        InMemoryGateway(Map<String, Object> report, List<Map<String, Object>> lifecycle) {
            this.report = report;
            this.lifecycle = lifecycle;
        }

        @Override
        public ReportBundle load(String reportId, String ownerUid) {
            return new ReportBundle(report, List.copyOf(followUps), lifecycle);
        }

        @Override
        public boolean append(String reportId, String ownerUid, String eventId, String fingerprint, Map<String, Object> event) {
            followUps.add(new LinkedHashMap<>(event));
            return false;
        }
    }
}
