package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvaluationCaseSetTest {
    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void classificationCasesAreClassifierOnlyAndUseTheV02Enum() throws Exception {
        JsonNode root = mapper.readTree(Files.readString(REPO_ROOT.resolve("data/eval/classification-cases-v0.1.json")));
        ClassificationSchemaValidator validator = new ClassificationSchemaValidator(mapper);
        assertEquals("classification-v0.1", root.path("schema_version").asText());
        assertEquals("v0.2", root.path("pack_version").asText());
        assertEquals(12, root.path("cases").size());

        Set<String> ids = new HashSet<>();
        for (JsonNode testCase : root.path("cases")) {
            assertTrue(ids.add(testCase.path("case_id").asText()));
            assertTrue(validator.allowedIssueTypes().contains(testCase.path("expected_issueType").asText()));
            assertTrue(testCase.has("image_ref"));
            assertTrue(testCase.hasNonNull("source"));
            assertFalse(testCase.has("expected_authority"));
            if (!testCase.path("image_ref").isNull()) {
                Path imagePath = REPO_ROOT.resolve("data/eval")
                        .resolve(testCase.path("image_ref").asText())
                        .normalize();
                assertTrue(Files.isRegularFile(imagePath));
            }
        }
    }

    @Test
    void routingCasesMatchTheDeterministicRouterAndContainNoModelFields() throws Exception {
        JsonNode root = mapper.readTree(Files.readString(REPO_ROOT.resolve("data/eval/routing-cases-v0.1.json")));
        CivicRouterService router = new CivicRouterService(mapper);
        assertEquals("v0.2", root.path("pack_version").asText());
        assertEquals(12, root.path("cases").size());

        Set<String> ids = new HashSet<>();
        for (JsonNode testCase : root.path("cases")) {
            assertTrue(ids.add(testCase.path("case_id").asText()));
            assertFalse(testCase.has("image_ref"));
            assertFalse(testCase.has("confidence"));
            CivicRouterService.CivicRouteResponse response = router.route(new CivicRouterService.CivicRouteRequest(
                    testCase.path("expected_issueType").asText(),
                    testCase.path("confirmed_prabhagId").asText(),
                    null,
                    "SELF_REPORTED",
                    false,
                    null));
            if (testCase.path("expected_routeId").isNull()) {
                assertEquals("UNSUPPORTED_ROUTE", response.status());
                assertNull(response.routeId());
            } else {
                assertEquals("SUPPORTED_ROUTE", response.status());
                assertEquals(testCase.path("expected_routeId").asText(), response.routeId());
                assertEquals(testCase.path("expected_authority").asText(), response.authority());
                assertNotNull(response.department());
            }
        }
    }
}
