package com.seewik.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CivicClassificationService {
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_TEXT_LENGTH = 2000;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final GeminiGateway geminiGateway;
    private final ClassificationPromptFactory promptFactory;
    private final ClassificationSchemaValidator validator;
    private final JsonNode responseSchema;
    private final Clock clock;
    private final ModelCallExecutor modelCalls;
    private final OperationalMetrics metrics;

    @Autowired
    public CivicClassificationService(
            GeminiGateway geminiGateway,
            ClassificationPromptFactory promptFactory,
            ClassificationSchemaValidator validator,
            ObjectMapper objectMapper,
            ModelCallExecutor modelCalls,
            OperationalMetrics metrics) throws IOException {
        this(geminiGateway, promptFactory, validator, loadResponseSchema(objectMapper), Clock.systemUTC(), modelCalls, metrics);
    }

    CivicClassificationService(
            GeminiGateway geminiGateway,
            ClassificationPromptFactory promptFactory,
            ClassificationSchemaValidator validator,
            ObjectMapper objectMapper) throws IOException {
        this(geminiGateway, promptFactory, validator, loadResponseSchema(objectMapper), Clock.systemUTC(),
                new ModelCallExecutor(ModelCallExecutor.DEFAULT_CLASSIFICATION_TIMEOUT, ModelCallExecutor.DEFAULT_DRAFTING_TIMEOUT),
                new OperationalMetrics(objectMapper, "test"));
    }

    CivicClassificationService(
            GeminiGateway geminiGateway,
            ClassificationPromptFactory promptFactory,
            ClassificationSchemaValidator validator,
            JsonNode responseSchema,
            Clock clock) {
        this(geminiGateway, promptFactory, validator, responseSchema, clock,
                new ModelCallExecutor(ModelCallExecutor.DEFAULT_CLASSIFICATION_TIMEOUT, ModelCallExecutor.DEFAULT_DRAFTING_TIMEOUT),
                new OperationalMetrics(new ObjectMapper(), "test"));
    }

    CivicClassificationService(
            GeminiGateway geminiGateway,
            ClassificationPromptFactory promptFactory,
            ClassificationSchemaValidator validator,
            JsonNode responseSchema,
            Clock clock,
            ModelCallExecutor modelCalls,
            OperationalMetrics metrics) {
        this.geminiGateway = geminiGateway;
        this.promptFactory = promptFactory;
        this.validator = validator;
        this.responseSchema = responseSchema;
        this.clock = clock;
        this.modelCalls = modelCalls;
        this.metrics = metrics;
    }

    public ClassificationResult classify(byte[] image, String mimeType, String citizenText) {
        validateInput(image, mimeType, citizenText);
        String prompt = promptFactory.build(citizenText, image != null && image.length > 0);
        long started = System.nanoTime();
        GeminiGateway.GeneratedContent generated;
        try {
            generated = modelCalls.classification(() -> geminiGateway.generateStructured(
                    prompt, image, mimeType, responseSchema, 512, modelCalls.classificationTimeout()));
        } catch (ModelCallExecutor.ModelTimeoutException | GeminiGateway.ModelTransportTimeoutException exception) {
            metrics.increment("model.classification.timeout");
            throw new ClassificationExecutionException(
                    "MODEL_TIMEOUT", "The classification model exceeded its deadline", exception);
        } catch (Exception exception) {
            metrics.increment("model.classification.failure");
            throw new ClassificationExecutionException(
                    "MODEL_CALL_FAILED", "The classification model is temporarily unavailable", exception);
        }
        long latencyMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);

        ClassificationSchemaValidator.ValidatedClassification classification;
        try {
            classification = validator.validate(generated.text());
        } catch (ClassificationSchemaValidator.ClassificationValidationException exception) {
            metrics.increment("model.classification.schema_failure");
            metrics.increment("model.classification.schema_failure." + exception.code().toLowerCase(java.util.Locale.ROOT));
            SchemaFailureDiagnostics diagnostics = new SchemaFailureDiagnostics(
                    exception.code(),
                    generated.text() == null ? 0 : generated.text().length(),
                    generated.finishReason(),
                    generated.candidatesTokenCount());
            throw new ClassificationExecutionException(
                    "SCHEMA_VALIDATION_FAILED", "The model returned an invalid classification", exception, diagnostics);
        }
        metrics.increment("model.classification.success");
        metrics.recordLatency("model.classification", latencyMs);
        if (classification.needsClarification()
                && classification.confidence() < ClassificationSchemaValidator.CONFIDENCE_THRESHOLD) {
            metrics.increment("low_confidence_clarification_total");
        }

        String status = classification.needsClarification() ? "CLARIFICATION_REQUIRED" : "CLASSIFIED";
        return new ClassificationResult(
                status,
                classification.issueType(),
                classification.subcategory(),
                classification.description(),
                classification.confidence(),
                classification.detectedLanguage(),
                classification.needsClarification(),
                classification.clarificationQuestion(),
                ClassificationSchemaValidator.SCHEMA_VERSION,
                CivicRouterService.PACK_VERSION,
                generated.modelVersion(),
                generated.responseId(),
                latencyMs,
                Instant.now(clock).toString(),
                generated.promptTokenCount(),
                generated.candidatesTokenCount(),
                generated.totalTokenCount());
    }

    private static void validateInput(byte[] image, String mimeType, String citizenText) {
        boolean hasImage = image != null && image.length > 0;
        boolean hasText = citizenText != null && !citizenText.isBlank();
        if (!hasImage && !hasText) {
            throw new ClassificationInputException("EMPTY_EVIDENCE", "Provide an image or a short description");
        }
        if (hasImage && image.length > MAX_IMAGE_BYTES) {
            throw new ClassificationInputException("IMAGE_TOO_LARGE", "Image must be 5 MB or smaller");
        }
        if (hasImage && !ALLOWED_IMAGE_TYPES.contains(mimeType)) {
            throw new ClassificationInputException(
                    "UNSUPPORTED_IMAGE_TYPE", "Image must be JPEG, PNG, or WebP");
        }
        if (citizenText != null && citizenText.strip().length() > MAX_TEXT_LENGTH) {
            throw new ClassificationInputException(
                    "TEXT_TOO_LONG", "Description must be 2,000 characters or shorter");
        }
    }

    private static JsonNode loadResponseSchema(ObjectMapper objectMapper) throws IOException {
        try (InputStream input = CivicClassificationService.class
                .getResourceAsStream("/classification-response-schema-vertex-v0.1.json")) {
            if (input == null) throw new IOException("Missing Vertex classification response schema");
            return objectMapper.readTree(input);
        }
    }

    public record ClassificationResult(
            String status,
            String issueType,
            String subcategory,
            String description,
            double confidence,
            String detectedLanguage,
            boolean needsClarification,
            String clarificationQuestion,
            String schemaVersion,
            String packVersion,
            String modelVersion,
            String responseId,
            long latencyMs,
            String classifiedAt,
            Long promptTokenCount,
            Long candidatesTokenCount,
            Long totalTokenCount) {}

    public static final class ClassificationInputException extends IllegalArgumentException {
        private final String code;

        ClassificationInputException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public static final class ClassificationExecutionException extends RuntimeException {
        private final String code;
        private final SchemaFailureDiagnostics schemaDiagnostics;

        ClassificationExecutionException(String code, String message, Throwable cause) {
            this(code, message, cause, null);
        }

        ClassificationExecutionException(
                String code,
                String message,
                Throwable cause,
                SchemaFailureDiagnostics schemaDiagnostics) {
            super(message, cause);
            this.code = code;
            this.schemaDiagnostics = schemaDiagnostics;
        }

        public String code() {
            return code;
        }

        public SchemaFailureDiagnostics schemaDiagnostics() {
            return schemaDiagnostics;
        }
    }

    public record SchemaFailureDiagnostics(
            String validatorSubcode,
            int generatedOutputLength,
            String finishReason,
            Long candidatesTokenCount) {}
}
