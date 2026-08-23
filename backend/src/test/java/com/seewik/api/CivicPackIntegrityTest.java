package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CivicPackIntegrityTest {
    @Test
    void packHasTenUniqueRoutesAndLockedVerificationStates() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = CivicPackIntegrityTest.class.getResourceAsStream("/civic-pack-v0.1.json")) {
            assertNotNull(input);
            JsonNode pack = mapper.readTree(input);
            assertEquals("v0.1", pack.path("packVersion").asText());
            assertEquals(10, pack.path("routes").size());

            Set<String> routeIds = new HashSet<>();
            Set<String> issueTypes = new HashSet<>();
            for (JsonNode route : pack.path("routes")) {
                assertTrue(routeIds.add(route.path("routeId").asText()), "duplicate routeId");
                assertTrue(issueTypes.add(route.path("issueType").asText()), "duplicate issueType");
                assertEquals("OFFICIAL_SOURCE", route.path("sourceStatus").asText());
                assertEquals("REVIEW_PENDING", route.path("reviewStatus").asText());
                assertTrue(route.path("department").asText().startsWith("UNVERIFIED_"));
                assertTrue(route.path("officialSource").path("url").asText().startsWith("https://"));
            }
        }
    }
}
