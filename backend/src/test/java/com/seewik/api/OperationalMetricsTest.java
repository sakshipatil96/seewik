package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationalMetricsTest {
    @Test
    void snapshotContainsOnlyFixedCountersLatencyAndRevision() throws Exception {
        OperationalMetrics metrics = new OperationalMetrics(new ObjectMapper(), "seewik-api-test-revision");
        metrics.increment("request.classification.authenticated");
        metrics.recordLatency("endpoint.classification", 10);
        metrics.recordLatency("endpoint.classification", 40);
        metrics.recordLatency("endpoint.classification", 20);

        Map<String, Object> snapshot = metrics.snapshot();
        assertEquals("seewik-api-test-revision", snapshot.get("revision"));
        @SuppressWarnings("unchecked")
        Map<String, Long> counters = (Map<String, Long>) snapshot.get("counters");
        assertEquals(1L, counters.get("request.classification.authenticated"));
        String serialized = new ObjectMapper().writeValueAsString(snapshot);
        assertFalse(serialized.contains("uid"));
        assertFalse(serialized.contains("coordinates"));
        assertFalse(serialized.contains("complaintBody"));
    }
}
