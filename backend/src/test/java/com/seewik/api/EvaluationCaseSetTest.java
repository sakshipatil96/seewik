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
import java.security.MessageDigest;
import java.util.HexFormat;
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
    void frozenTrackATextSetHasSixtyClassifierOnlyCasesAndOneInjectionCase() throws Exception {
        JsonNode root = mapper.readTree(Files.readString(REPO_ROOT.resolve("data/eval/classification-cases-v0.2.json")));
        ClassificationSchemaValidator validator = new ClassificationSchemaValidator(mapper);
        assertEquals("classification-cases-v0.2", root.path("case_set_version").asText());
        assertEquals("TRACK_A_TEXT_MULTILINGUAL", root.path("track").asText());
        assertEquals(ClassificationPromptFactory.PROMPT_VERSION, root.path("prompt_version").asText());
        assertTrue(root.path("frozen_before_scored_run").asBoolean());
        assertEquals(60, root.path("cases").size());
        assertTrue(root.path("cases_sha256").asText().matches("^[a-f0-9]{64}$"));

        Set<String> ids = new HashSet<>();
        int injectionCases = 0;
        Set<String> expectedLanguages = Set.of("MR", "HI", "EN", "MIXED", "UNKNOWN");
        for (JsonNode testCase : root.path("cases")) {
            assertTrue(ids.add(testCase.path("case_id").asText()));
            assertTrue(testCase.path("image_ref").isNull());
            assertTrue(testCase.path("input_text").asText().length() > 5);
            assertTrue(validator.allowedIssueTypes().contains(testCase.path("expected_issueType").asText()));
            assertTrue(expectedLanguages.contains(testCase.path("expectedLanguage").asText()));
            assertTrue(testCase.hasNonNull("source"));
            assertFalse(testCase.has("expected_authority"));
            if (testCase.path("case_id").asText().contains("INJECTION")) injectionCases++;
        }
        assertEquals(1, injectionCases);
    }

    @Test
    void humanBaselineKeyWasFrozenBeforeResponsesAndCoversTheTenSurveyScenarios() throws Exception {
        JsonNode root = mapper.readTree(Files.readString(
                REPO_ROOT.resolve("data/eval/human-baseline-answer-key-v0.1.json")));
        assertEquals("human-baseline-answer-key-v0.1", root.path("answerKeyVersion").asText());
        assertTrue(root.path("frozenBeforeResponsesOpened").asBoolean());
        assertTrue(root.path("contentSha256").asText().matches("^[a-f0-9]{64}$"));
        String declaredSha = root.path("contentSha256").asText();
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).remove("contentSha256");
        String canonical = mapper.writeValueAsString(root);
        String computedSha = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(declaredSha, computedSha);
        assertEquals("Nandurbar Municipal Council",
                root.path("authorityScoring").path("canonicalAuthority").asText());
        assertFalse(root.path("authorityScoring").path("departmentScored").asBoolean());
        assertEquals(10, root.path("scenarioKeys").size());

        Set<String> ids = new HashSet<>();
        for (JsonNode scenario : root.path("scenarioKeys")) {
            assertTrue(ids.add(scenario.path("id").asText()));
            assertEquals("Nandurbar Municipal Council", scenario.path("expectedAuthority").asText());
            assertTrue(scenario.path("en").asText().length() > 20);
            assertTrue(scenario.path("mr").asText().length() > 20);
            assertTrue(scenario.path("hi").asText().length() > 20);
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
