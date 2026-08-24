package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CivicClassificationServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ClassificationPromptFactory promptFactory;
    private final ClassificationSchemaValidator validator;
    private final JsonNode vertexSchema;

    CivicClassificationServiceTest() throws Exception {
        promptFactory = new ClassificationPromptFactory(mapper);
        validator = new ClassificationSchemaValidator(mapper);
        try (InputStream input = getClass().getResourceAsStream("/classification-response-schema-vertex-v0.1.json")) {
            assertNotNull(input);
            vertexSchema = mapper.readTree(input);
        }
    }

    @Test
    void validHighConfidenceOutputIsClassifiedWithoutChangingModelFields() {
        var result = serviceReturning(highConfidence("POTHOLE_ROAD_DAMAGE", "MR", 0.92))
                .classify(null, null, "रस्त्यावर मोठा खड्डा आहे");
        assertEquals("CLASSIFIED", result.status());
        assertEquals("POTHOLE_ROAD_DAMAGE", result.issueType());
        assertEquals(0.92, result.confidence());
        assertEquals("MR", result.detectedLanguage());
        assertFalse(result.needsClarification());
        assertEquals("classification-v0.1", result.schemaVersion());
        assertEquals("v0.2", result.packVersion());
        assertEquals("gemini-test", result.modelVersion());
        assertEquals("2026-08-24T18:00:00Z", result.classifiedAt());
    }

    @Test
    void lowConfidenceOutputStopsForClarification() {
        var result = serviceReturning(clarification("WATER_SUPPLY", "MIXED", 0.72))
                .classify(null, null, "Paani कमी pressure ने येत आहे");
        assertEquals("CLARIFICATION_REQUIRED", result.status());
        assertTrue(result.needsClarification());
        assertFalse(result.clarificationQuestion().isBlank());
    }

    @Test
    void unknownAlwaysStopsForClarification() {
        var result = serviceReturning(clarification("UNKNOWN", "EN", 0.95))
                .classify(null, null, "Something is wrong");
        assertEquals("CLARIFICATION_REQUIRED", result.status());
        assertEquals("UNKNOWN", result.issueType());
    }

    @Test
    void gatewayReceivesCanonicalPromptImageAndVertexResponseSchema() {
        AtomicReference<String> prompt = new AtomicReference<>();
        AtomicReference<JsonNode> schema = new AtomicReference<>();
        byte[] image = {1, 2, 3};
        GeminiGateway gateway = (value, bytes, mimeType, responseSchema) -> {
            prompt.set(value);
            schema.set(responseSchema);
            assertEquals(image, bytes);
            assertEquals("image/png", mimeType);
            return generated(highConfidence("STREETLIGHT", "EN", 0.89));
        };
        service(gateway).classify(image, "image/png", "The streetlight is off");
        assertTrue(prompt.get().contains("STREETLIGHT"));
        assertTrue(prompt.get().contains("Do not decide civic responsibility"));
        assertEquals("OBJECT", schema.get().path("type").asText());
        assertTrue(schema.get().path("required").isArray());
    }

    @Test
    void malformedModelJsonIsIdentifiedAsSchemaStageFailure() {
        assertExecutionCode(serviceReturning("not json"), "SCHEMA_VALIDATION_FAILED");
    }

    @Test
    void forbiddenAuthorityFieldIsIdentifiedAsSchemaStageFailure() {
        String raw = highConfidence("STREETLIGHT", "EN", 0.90)
                .replace("\n}", ",\n  \"authority\": \"forbidden\"\n}");
        assertExecutionCode(serviceReturning(raw), "SCHEMA_VALIDATION_FAILED");
    }

    @Test
    void gatewayFailureIsIdentifiedAsModelStageFailure() {
        GeminiGateway failing = (prompt, image, mime, schema) -> {
            throw new IllegalStateException("upstream unavailable");
        };
        assertExecutionCode(service(failing), "MODEL_CALL_FAILED");
    }

    @Test
    void requiresImageOrText() {
        assertInputCode(() -> serviceReturning(highConfidence("STREETLIGHT", "EN", 0.9))
                .classify(null, null, "  "), "EMPTY_EVIDENCE");
    }

    @Test
    void rejectsUnsupportedImageMimeTypeBeforeModelCall() {
        assertInputCode(() -> serviceReturning(highConfidence("STREETLIGHT", "EN", 0.9))
                .classify(new byte[] {1}, "image/svg+xml", null), "UNSUPPORTED_IMAGE_TYPE");
    }

    @Test
    void rejectsOversizedImagesBeforeModelCall() {
        byte[] oversized = new byte[CivicClassificationService.MAX_IMAGE_BYTES + 1];
        assertInputCode(() -> serviceReturning(highConfidence("STREETLIGHT", "EN", 0.9))
                .classify(oversized, "image/jpeg", null), "IMAGE_TOO_LARGE");
    }

    private CivicClassificationService serviceReturning(String raw) {
        return service((prompt, image, mime, schema) -> generated(raw));
    }

    private CivicClassificationService service(GeminiGateway gateway) {
        return new CivicClassificationService(
                gateway,
                promptFactory,
                validator,
                vertexSchema,
                Clock.fixed(Instant.parse("2026-08-24T18:00:00Z"), ZoneOffset.UTC));
    }

    private static GeminiGateway.GeneratedContent generated(String raw) {
        return new GeminiGateway.GeneratedContent(raw, "gemini-test", "response-test", 10L, 20L, 30L);
    }

    private void assertExecutionCode(CivicClassificationService service, String expectedCode) {
        var exception = assertThrows(
                CivicClassificationService.ClassificationExecutionException.class,
                () -> service.classify(null, null, "test evidence"));
        assertEquals(expectedCode, exception.code());
    }

    private static void assertInputCode(Runnable call, String expectedCode) {
        var exception = assertThrows(CivicClassificationService.ClassificationInputException.class, call::run);
        assertEquals(expectedCode, exception.code());
    }

    private static String highConfidence(String issueType, String language, double confidence) {
        return """
                {
                  "issueType": "%s",
                  "subcategory": null,
                  "description": "Evidence supports one civic category.",
                  "confidence": %s,
                  "detectedLanguage": "%s",
                  "needsClarification": false,
                  "clarificationQuestion": null
                }
                """.formatted(issueType, confidence, language);
    }

    private static String clarification(String issueType, String language, double confidence) {
        return """
                {
                  "issueType": "%s",
                  "subcategory": null,
                  "description": "The evidence is ambiguous.",
                  "confidence": %s,
                  "detectedLanguage": "%s",
                  "needsClarification": true,
                  "clarificationQuestion": "Could you clarify the visible civic problem?"
                }
                """.formatted(issueType, confidence, language);
    }
}
