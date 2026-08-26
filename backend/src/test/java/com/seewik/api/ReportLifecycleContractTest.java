package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReportLifecycleContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void schemaEnumsStayAlignedWithTheJavaContract() throws Exception {
        JsonNode schema;
        try (InputStream input = getClass().getResourceAsStream("/report-lifecycle-schema-v0.1.json")) {
            assertNotNull(input);
            schema = mapper.readTree(input);
        }
        assertFalse(schema.path("additionalProperties").asBoolean(true));
        assertEquals(
                names(ReportLifecycleContract.ReportStatus.values()),
                values(schema.path("$defs").path("reportStatus").path("enum")));
        assertEquals(
                names(ReportLifecycleContract.EventType.values()),
                values(schema.path("$defs").path("eventType").path("enum")));
        assertEquals(
                names(ReportLifecycleContract.VerificationBasis.values()),
                values(schema.path("$defs").path("verificationBasis").path("enum")));
        assertEquals(
                ReportLifecycleContract.SCHEMA_VERSION,
                schema.path("properties").path("schemaVersion").path("const").asText());
    }

    @Test
    void reopenEventsPreserveDifferentCivicMeanings() {
        assertEquals(
                ReportLifecycleContract.EventType.REPAIR_CLAIM_REJECTED,
                ReportLifecycleContract.eventType(
                        ReportLifecycleContract.ReportStatus.CLAIMED_FIXED,
                        ReportLifecycleContract.ReportStatus.REOPENED));
        assertEquals(
                ReportLifecycleContract.EventType.ISSUE_RECURRED,
                ReportLifecycleContract.eventType(
                        ReportLifecycleContract.ReportStatus.VERIFIED_FIXED,
                        ReportLifecycleContract.ReportStatus.REOPENED));
    }

    @Test
    void verifiedFixedCannotBeReachedWithoutClaimedFixed() {
        assertFalse(ReportLifecycleContract.allows(
                ReportLifecycleContract.ReportStatus.DRAFT,
                ReportLifecycleContract.ReportStatus.VERIFIED_FIXED));
        assertFalse(ReportLifecycleContract.allows(
                ReportLifecycleContract.ReportStatus.FILED,
                ReportLifecycleContract.ReportStatus.VERIFIED_FIXED));
        assertTrue(ReportLifecycleContract.allows(
                ReportLifecycleContract.ReportStatus.CLAIMED_FIXED,
                ReportLifecycleContract.ReportStatus.VERIFIED_FIXED));
    }

    private static Set<String> values(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static Set<String> names(Enum<?>[] values) {
        Set<String> names = new HashSet<>();
        for (Enum<?> value : values) names.add(value.name());
        return names;
    }
}
