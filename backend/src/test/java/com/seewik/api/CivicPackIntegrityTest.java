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

class CivicPackIntegrityTest {
    private static final Set<String> EXPECTED_ISSUE_TYPES = Set.of(
            "GARBAGE_SOLID_WASTE",
            "ILLEGAL_DUMPING",
            "PUBLIC_AREA_CLEANLINESS",
            "PUBLIC_TOILET_SANITATION",
            "MOSQUITO_FOGGING",
            "DEAD_ANIMAL_REMOVAL",
            "WATER_SUPPLY",
            "POTHOLE_ROAD_DAMAGE",
            "DRAINAGE_SEWAGE",
            "PUBLIC_ROAD_OBSTRUCTION",
            "STREETLIGHT");

    @Test
    void packHasElevenUniqueRoutesAndLockedVerificationStates() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = CivicPackIntegrityTest.class.getResourceAsStream("/civic-pack-v0.2.json")) {
            assertNotNull(input);
            JsonNode pack = mapper.readTree(input);
            assertEquals("v0.2", pack.path("packVersion").asText());
            assertEquals("NANDURBAR", pack.path("cityId").asText());
            assertEquals(11, pack.path("routes").size());
            assertFalse(pack.toString().contains("\"openQuestion\""));

            Set<String> departmentIds = new HashSet<>();
            for (JsonNode department : pack.path("departments")) {
                assertTrue(departmentIds.add(department.path("departmentId").asText()), "duplicate departmentId");
                assertEquals("TYPICAL_STRUCTURE_UNVERIFIED", department.path("status").asText());
                assertFalse(department.path("displayName").asText().isBlank());
                assertFalse(department.path("basis").asText().isBlank());
            }

            assertEquals(3, pack.path("officialChannels").size());
            for (JsonNode channel : pack.path("officialChannels")) {
                assertEquals("OFFICIAL_SOURCE", channel.path("sourceStatus").asText());
                assertTrue(channel.path("sourceReference").path("url").asText().startsWith("https://"));
                assertFalse(channel.path("value").asText().contains("facebook.com"));
            }
            JsonNode dma = pack.path("officialChannels").get(1);
            assertEquals("FORM_DMA", dma.path("channelId").asText());
            assertTrue(dma.path("localizedValues").path("EN").asText().contains("/en/complaint-2/"));
            assertTrue(dma.path("localizedValues").path("MR").asText().contains("/complaint/"));
            assertEquals(1, pack.path("informationalLinks").size());
            assertTrue(pack.path("informationalLinks").get(0).path("value").asText().contains("facebook.com"));

            Set<String> routeIds = new HashSet<>();
            Set<String> issueTypes = new HashSet<>();
            for (JsonNode route : pack.path("routes")) {
                assertTrue(routeIds.add(route.path("routeId").asText()), "duplicate routeId");
                assertTrue(issueTypes.add(route.path("issueType").asText()), "duplicate issueType");
                assertEquals("OFFICIAL_SOURCE", route.path("sourceStatus").asText());
                assertEquals("REVIEW_PENDING", route.path("reviewStatus").asText());
                assertTrue(departmentIds.contains(route.path("departmentId").asText()), "unknown departmentId");
                assertFalse(route.path("classificationDefinition").asText().isBlank());
                assertTrue(route.path("excludes").isArray());
                assertTrue(route.path("excludes").size() >= 2);
                assertTrue(route.path("knownLimitations").isArray());
                assertFalse(route.has("openQuestion"));
                assertTrue(route.path("sla").isNull());
                assertTrue(route.path("escalation").isNull());
                assertFalse(route.path("officialSource").path("title").asText().isBlank());
                assertTrue(route.path("officialSource").path("url").asText().startsWith("https://"));
                assertFalse(route.path("officialSource").path("reference").asText().isBlank());
            }
            assertEquals(EXPECTED_ISSUE_TYPES, issueTypes);
        }
    }

    @Test
    void overlappingWasteCategoriesHaveCanonicalDistinctDefinitions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = CivicPackIntegrityTest.class.getResourceAsStream("/civic-pack-v0.2.json")) {
            assertNotNull(input);
            JsonNode pack = mapper.readTree(input);
            JsonNode garbage = route(pack, "GARBAGE_SOLID_WASTE");
            JsonNode dumping = route(pack, "ILLEGAL_DUMPING");
            JsonNode cleanliness = route(pack, "PUBLIC_AREA_CLEANLINESS");

            assertTrue(garbage.path("classificationDefinition").asText().contains("overflowing public bin"));
            assertTrue(dumping.path("classificationDefinition").asText().contains("undesignated public location"));
            assertTrue(cleanliness.path("classificationDefinition").asText().contains("without a specific garbage overflow"));
            assertFalse(garbage.path("classificationDefinition").asText()
                    .equals(dumping.path("classificationDefinition").asText()));
            assertFalse(dumping.path("classificationDefinition").asText()
                    .equals(cleanliness.path("classificationDefinition").asText()));
        }
    }

    @Test
    void routeSpecificLimitationsAreMachineActionable() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = CivicPackIntegrityTest.class.getResourceAsStream("/civic-pack-v0.2.json")) {
            assertNotNull(input);
            JsonNode pack = mapper.readTree(input);
            assertLimitation(route(pack, "POTHOLE_ROAD_DAMAGE"), "ROAD_OWNERSHIP_UNKNOWN");
            assertLimitation(route(pack, "STREETLIGHT"), "ELECTRICITY_NETWORK_FAULT_EXCLUDED");
            assertLimitation(route(pack, "WATER_SUPPLY"), "WATER_NETWORK_OPERATOR_UNKNOWN");
            assertLimitation(route(pack, "DRAINAGE_SEWAGE"), "DRAINAGE_DESK_SPLIT_UNCONFIRMED");
        }
    }

    private static JsonNode route(JsonNode pack, String issueType) {
        for (JsonNode route : pack.path("routes")) {
            if (issueType.equals(route.path("issueType").asText())) return route;
        }
        throw new AssertionError("Missing route for " + issueType);
    }

    private static void assertLimitation(JsonNode route, String expectedCode) {
        assertTrue(route.path("knownLimitations").size() > 0);
        JsonNode limitation = route.path("knownLimitations").get(0);
        assertEquals(expectedCode, limitation.path("code").asText());
        assertFalse(limitation.path("citizenMessage").asText().isBlank());
        assertFalse(limitation.path("routingImpact").asText().isBlank());
        assertTrue(limitation.path("requiresCitizenAttention").asBoolean());
    }
}
