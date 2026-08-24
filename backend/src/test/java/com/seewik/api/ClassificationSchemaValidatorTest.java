package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClassificationSchemaValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ClassificationSchemaValidator validator;

    ClassificationSchemaValidatorTest() throws Exception {
        validator = new ClassificationSchemaValidator(mapper);
    }

    @Test
    void acceptsSupportedClassificationAboveThreshold() {
        var result = validator.validate(highConfidenceJson("GARBAGE_SOLID_WASTE", "EN", 0.91));
        assertEquals("GARBAGE_SOLID_WASTE", result.issueType());
        assertEquals(0.91, result.confidence());
        assertFalse(result.needsClarification());
        assertEquals(null, result.clarificationQuestion());
    }

    @Test
    void confidenceExactlyPointEightContinuesWithoutClarification() {
        var result = validator.validate(highConfidenceJson("STREETLIGHT", "MR", 0.80));
        assertEquals(0.80, result.confidence());
        assertFalse(result.needsClarification());
    }

    @Test
    void acceptsMixedLanguageEnum() {
        var result = validator.validate(highConfidenceJson("DRAINAGE_SEWAGE", "MIXED", 0.88));
        assertEquals("MIXED", result.detectedLanguage());
    }

    @Test
    void lowConfidenceRequiresAQuestion() {
        var result = validator.validate(clarificationJson("WATER_SUPPLY", "HI", 0.79));
        assertTrue(result.needsClarification());
        assertFalse(result.clarificationQuestion().isBlank());
    }

    @Test
    void unknownRequiresClarificationEvenWithHighConfidence() {
        var result = validator.validate(clarificationJson("UNKNOWN", "UNKNOWN", 0.95));
        assertTrue(result.needsClarification());
    }

    @Test
    void schemaEnumsMatchCivicPackAndValidator() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/classification-schema-v0.1.json")) {
            assertNotNull(input);
            JsonNode schema = mapper.readTree(input);
            Set<String> schemaIssueTypes = values(schema.path("properties").path("issueType").path("enum"));
            Set<String> schemaLanguages = values(schema.path("properties").path("detectedLanguage").path("enum"));

            assertEquals(validator.allowedIssueTypes(), schemaIssueTypes);
            assertEquals(ClassificationSchemaValidator.DETECTED_LANGUAGES, schemaLanguages);
            assertEquals(
                    ClassificationSchemaValidator.CONFIDENCE_THRESHOLD,
                    schema.path("allOf")
                            .get(0)
                            .path("if")
                            .path("anyOf")
                            .get(1)
                            .path("properties")
                            .path("confidence")
                            .path("exclusiveMaximum")
                            .asDouble());
            assertFalse(schema.path("additionalProperties").asBoolean(true));
            assertTrue(schema.path("description").asText().contains("Authority"));
        }
    }

    @Test
    void vertexResponseSchemaEnumsAndOrderMatchStrictContract() throws Exception {
        JsonNode strict;
        JsonNode vertex;
        try (InputStream input = getClass().getResourceAsStream("/classification-schema-v0.1.json")) {
            assertNotNull(input);
            strict = mapper.readTree(input);
        }
        try (InputStream input = getClass().getResourceAsStream("/classification-response-schema-vertex-v0.1.json")) {
            assertNotNull(input);
            vertex = mapper.readTree(input);
        }
        assertEquals(
                values(strict.path("properties").path("issueType").path("enum")),
                values(vertex.path("properties").path("issueType").path("enum")));
        assertEquals(
                values(strict.path("properties").path("detectedLanguage").path("enum")),
                values(vertex.path("properties").path("detectedLanguage").path("enum")));
        assertEquals(
                values(strict.path("required")),
                values(vertex.path("required")));
        assertEquals(strict.path("required"), vertex.path("propertyOrdering"));
        assertEquals(0.80d, ClassificationSchemaValidator.CONFIDENCE_THRESHOLD);
    }

    @Test
    void rejectsUnknownIssueEnum() {
        assertCode(highConfidenceJson("DAMAGED_INFRASTRUCTURE", "EN", 0.92), "INVALID_ISSUE_TYPE");
    }

    @Test
    void rejectsUnknownLanguageEnum() {
        assertCode(highConfidenceJson("STREETLIGHT", "MARATHI", 0.92), "INVALID_LANGUAGE");
    }

    @Test
    void rejectsConfidenceBelowZero() {
        assertCode(highConfidenceJson("STREETLIGHT", "EN", -0.01), "INVALID_CONFIDENCE");
    }

    @Test
    void rejectsConfidenceAboveOne() {
        assertCode(highConfidenceJson("STREETLIGHT", "EN", 1.01), "INVALID_CONFIDENCE");
    }

    @Test
    void rejectsMissingRequiredField() {
        String raw = highConfidenceJson("STREETLIGHT", "EN", 0.90)
                .replace("\n  \"subcategory\": null,", "");
        assertCode(raw, "MISSING_FIELDS");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "authority", "department", "prabhagId", "officialChannels", "sla", "escalation", "routeId"
    })
    void rejectsEveryForbiddenDecisionField(String field) {
        String raw = highConfidenceJson("STREETLIGHT", "EN", 0.90)
                .replace("\n}", ",\n  \"" + field + "\": \"forbidden\"\n}");
        assertCode(raw, "UNEXPECTED_FIELDS");
    }

    @Test
    void rejectsMalformedJson() {
        assertCode("{not-json", "MALFORMED_JSON");
    }

    @Test
    void rejectsNonObjectRoot() {
        assertCode("[]", "INVALID_ROOT");
    }

    @Test
    void rejectsLowConfidenceWithoutClarificationFlag() {
        assertCode(highConfidenceJson("WATER_SUPPLY", "EN", 0.79), "INCONSISTENT_CLARIFICATION");
    }

    @Test
    void rejectsLowConfidenceWithoutQuestion() {
        String raw = clarificationJson("WATER_SUPPLY", "EN", 0.79)
                .replace("\"clarificationQuestion\": \"Could you confirm the visible civic problem?\"", "\"clarificationQuestion\": null");
        assertCode(raw, "MISSING_CLARIFICATION_QUESTION");
    }

    @Test
    void rejectsHighConfidenceClarificationFlag() {
        assertCode(clarificationJson("STREETLIGHT", "EN", 0.90), "INCONSISTENT_CLARIFICATION");
    }

    @Test
    void rejectsHighConfidenceQuestionEvenWhenFlagIsFalse() {
        String raw = highConfidenceJson("STREETLIGHT", "EN", 0.90)
                .replace("\"clarificationQuestion\": null", "\"clarificationQuestion\": \"Is this a streetlight?\"");
        assertCode(raw, "UNEXPECTED_CLARIFICATION_QUESTION");
    }

    @Test
    void rejectsBlankDescription() {
        String raw = highConfidenceJson("STREETLIGHT", "EN", 0.90)
                .replace("\"description\": \"A visible civic issue.\"", "\"description\": \"   \"");
        assertCode(raw, "INVALID_TEXT_FIELD");
    }

    private void assertCode(String raw, String expectedCode) {
        var exception = assertThrows(
                ClassificationSchemaValidator.ClassificationValidationException.class,
                () -> validator.validate(raw));
        assertEquals(expectedCode, exception.code());
    }

    private static Set<String> values(JsonNode array) {
        Set<String> values = new HashSet<>();
        for (JsonNode value : array) values.add(value.asText());
        return values;
    }

    private static String highConfidenceJson(String issueType, String language, double confidence) {
        return """
                {
                  "issueType": "%s",
                  "subcategory": null,
                  "description": "A visible civic issue.",
                  "confidence": %s,
                  "detectedLanguage": "%s",
                  "needsClarification": false,
                  "clarificationQuestion": null
                }
                """.formatted(issueType, confidence, language);
    }

    private static String clarificationJson(String issueType, String language, double confidence) {
        return """
                {
                  "issueType": "%s",
                  "subcategory": null,
                  "description": "The evidence is ambiguous.",
                  "confidence": %s,
                  "detectedLanguage": "%s",
                  "needsClarification": true,
                  "clarificationQuestion": "Could you confirm the visible civic problem?"
                }
                """.formatted(issueType, confidence, language);
    }
}
