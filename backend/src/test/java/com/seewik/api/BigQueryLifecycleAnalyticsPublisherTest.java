package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BigQueryLifecycleAnalyticsPublisherTest {
    @Test
    void lifecycleRowContainsOnlyAnalyticsSafeFields() {
        Map<String, Object> outbox = new LinkedHashMap<>();
        outbox.put("eventId", "evt-1");
        outbox.put("reportIdHash", "hashed-report");
        outbox.put("ownerIdHash", "hashed-owner");
        outbox.put("fromStatus", "DRAFT");
        outbox.put("toStatus", "FILED");
        outbox.put("eventType", "REPORT_FILED");
        outbox.put("verificationBasis", "CITIZEN_ATTESTATION");
        outbox.put("issueType", "POTHOLE_ROAD_DAMAGE");
        outbox.put("prabhagId", "PRABHAG-03");
        outbox.put("occurredAt", new Date(0));
        outbox.put("schemaVersion", "report-lifecycle-v0.1");
        outbox.put("demoMode", false);
        outbox.put("pointsAwarded", 5);
        outbox.put("overdueEligible", false);
        outbox.put("draftBody", "must never leave Firestore");
        outbox.put("acknowledgementId", "private-tracking-id");

        Map<String, Object> row = BigQueryLifecycleAnalyticsPublisher.lifecycleRow(outbox);
        assertEquals("hashed-report", row.get("report_id_hash"));
        assertEquals("hashed-owner", row.get("owner_id_hash"));
        assertTrue(row.containsKey("exported_at"));
        assertFalse(row.containsKey("draftBody"));
        assertFalse(row.containsKey("acknowledgementId"));
        assertFalse(row.containsKey("ownerUid"));
    }
}
