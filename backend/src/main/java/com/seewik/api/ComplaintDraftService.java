package com.seewik.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ComplaintDraftService {
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MAX_LOCATION_LENGTH = 500;
    private final GeminiGateway geminiGateway;
    private final ComplaintPromptFactory promptFactory;
    private final ComplaintDraftValidator validator;
    private final CivicRouterService router;
    private final JsonNode responseSchema;
    private final Clock clock;
    private final ModelCallExecutor modelCalls;
    private final OperationalMetrics metrics;

    @Autowired
    public ComplaintDraftService(
            GeminiGateway geminiGateway,
            ComplaintPromptFactory promptFactory,
            ComplaintDraftValidator validator,
            CivicRouterService router,
            ObjectMapper objectMapper,
            ModelCallExecutor modelCalls,
            OperationalMetrics metrics) throws IOException {
        this(geminiGateway, promptFactory, validator, router, loadResponseSchema(objectMapper), Clock.systemUTC(), modelCalls, metrics);
    }

    ComplaintDraftService(
            GeminiGateway geminiGateway,
            ComplaintPromptFactory promptFactory,
            ComplaintDraftValidator validator,
            CivicRouterService router,
            ObjectMapper objectMapper) throws IOException {
        this(geminiGateway, promptFactory, validator, router, loadResponseSchema(objectMapper), Clock.systemUTC(),
                new ModelCallExecutor(ModelCallExecutor.DEFAULT_CLASSIFICATION_TIMEOUT, ModelCallExecutor.DEFAULT_DRAFTING_TIMEOUT),
                new OperationalMetrics(objectMapper, "test"));
    }

    ComplaintDraftService(
            GeminiGateway geminiGateway,
            ComplaintPromptFactory promptFactory,
            ComplaintDraftValidator validator,
            CivicRouterService router,
            JsonNode responseSchema,
            Clock clock) {
        this(geminiGateway, promptFactory, validator, router, responseSchema, clock,
                new ModelCallExecutor(ModelCallExecutor.DEFAULT_CLASSIFICATION_TIMEOUT, ModelCallExecutor.DEFAULT_DRAFTING_TIMEOUT),
                new OperationalMetrics(new ObjectMapper(), "test"));
    }

    ComplaintDraftService(
            GeminiGateway geminiGateway,
            ComplaintPromptFactory promptFactory,
            ComplaintDraftValidator validator,
            CivicRouterService router,
            JsonNode responseSchema,
            Clock clock,
            ModelCallExecutor modelCalls,
            OperationalMetrics metrics) {
        this.geminiGateway = geminiGateway;
        this.promptFactory = promptFactory;
        this.validator = validator;
        this.router = router;
        this.responseSchema = responseSchema;
        this.clock = clock;
        this.modelCalls = modelCalls;
        this.metrics = metrics;
    }

    public ComplaintDraftResponse draft(ComplaintDraftRequest request) {
        ValidatedInput input = validateInput(request);
        CivicRouterService.CivicRouteResponse route = router.route(new CivicRouterService.CivicRouteRequest(
                input.issueType(),
                input.prabhagId(),
                null,
                request.resolutionMethod(),
                request.citizenConfirmed(),
                request.boundaryDatasetVersion()));
        if ("CONFIRMATION_REQUIRED".equals(route.status())) {
            throw new ComplaintDraftInputException(
                    "ROUTE_CONFIRMATION_REQUIRED", "Confirm the suggested prabhag before drafting");
        }
        if (!"SUPPORTED_ROUTE".equals(route.status())) {
            throw new ComplaintDraftInputException(
                    "UNSUPPORTED_ROUTE", "A supported deterministic route is required before drafting");
        }

        String prompt = promptFactory.build(
                input.draftLanguage(),
                input.issueType(),
                input.citizenDescription(),
                input.locationDetails(),
                input.locationProvided(),
                route);
        long started = System.nanoTime();
        GeminiGateway.GeneratedContent generated;
        try {
            generated = modelCalls.drafting(() -> geminiGateway.generateStructured(
                    prompt, null, null, responseSchema, 2048, modelCalls.draftingTimeout()));
        } catch (ModelCallExecutor.ModelTimeoutException | GeminiGateway.ModelTransportTimeoutException exception) {
            metrics.increment("model.drafting.timeout");
            throw new ComplaintDraftExecutionException(
                    "MODEL_TIMEOUT", "The complaint drafting model exceeded its deadline", exception);
        } catch (Exception exception) {
            metrics.increment("model.drafting.failure");
            throw new ComplaintDraftExecutionException(
                    "MODEL_CALL_FAILED", "The complaint drafting model is temporarily unavailable", exception);
        }
        long latencyMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);

        ComplaintDraftValidator.ValidatedDraft draft;
        try {
            draft = validator.validate(generated.text(), input.draftLanguage());
        } catch (ComplaintDraftValidator.ComplaintDraftValidationException exception) {
            metrics.increment("model.drafting.schema_failure");
            throw new ComplaintDraftExecutionException(
                    "SCHEMA_VALIDATION_FAILED", "The model returned an invalid complaint draft", exception);
        }
        metrics.increment("model.drafting.success");
        metrics.recordLatency("model.drafting", latencyMs);

        List<String> missingDetails = input.locationProvided()
                ? List.of()
                : List.of("LOCATION_OR_LANDMARK");
        return new ComplaintDraftResponse(
                "DRAFT_READY",
                ComplaintDraftValidator.DRAFT_VERSION,
                ComplaintDraftValidator.SCHEMA_VERSION,
                CivicRouterService.PACK_VERSION,
                input.draftLanguage(),
                route.routeId(),
                route.prabhagId(),
                route.authority(),
                route.authorityLocalName(),
                draft.subject(),
                draft.body(),
                missingDetails,
                true,
                generated.modelVersion(),
                generated.responseId(),
                latencyMs,
                Instant.now(clock).toString(),
                generated.promptTokenCount(),
                generated.candidatesTokenCount(),
                generated.totalTokenCount());
    }

    private static ValidatedInput validateInput(ComplaintDraftRequest request) {
        if (request == null) {
            throw new ComplaintDraftInputException("EMPTY_REQUEST", "Complaint draft request is required");
        }
        if (!Boolean.TRUE.equals(request.classificationConfirmed())) {
            throw new ComplaintDraftInputException(
                    "CATEGORY_CONFIRMATION_REQUIRED", "Confirm the issue category before drafting");
        }
        String issueType = clean(request.issueType());
        String prabhagId = clean(request.prabhagId());
        String citizenDescription = clean(request.citizenDescription());
        String locationDetails = clean(request.locationDetails());
        String draftLanguage = clean(request.draftLanguage()).toUpperCase(java.util.Locale.ROOT);
        if (issueType.isEmpty() || prabhagId.isEmpty()) {
            throw new ComplaintDraftInputException(
                    "MISSING_ROUTE_INPUT", "Issue type and prabhag are required before drafting");
        }
        if (citizenDescription.isEmpty()) {
            throw new ComplaintDraftInputException(
                    "MISSING_CITIZEN_FACTS", "Provide a short factual description before drafting");
        }
        if (citizenDescription.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ComplaintDraftInputException(
                    "DESCRIPTION_TOO_LONG", "Citizen description must be 2,000 characters or shorter");
        }
        if (locationDetails.length() > MAX_LOCATION_LENGTH) {
            throw new ComplaintDraftInputException(
                    "LOCATION_TOO_LONG", "Location details must be 500 characters or shorter");
        }
        if (!ComplaintDraftValidator.DRAFT_LANGUAGES.contains(draftLanguage)) {
            throw new ComplaintDraftInputException(
                    "INVALID_DRAFT_LANGUAGE", "Choose Marathi or English for the complaint draft");
        }
        return new ValidatedInput(
                issueType, prabhagId, citizenDescription, locationDetails, !locationDetails.isEmpty(), draftLanguage);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static JsonNode loadResponseSchema(ObjectMapper objectMapper) throws IOException {
        try (InputStream input = ComplaintDraftService.class
                .getResourceAsStream("/complaint-draft-response-schema-vertex-v0.1.json")) {
            if (input == null) throw new IOException("Missing Vertex complaint draft response schema");
            return objectMapper.readTree(input);
        }
    }

    private record ValidatedInput(
            String issueType,
            String prabhagId,
            String citizenDescription,
            String locationDetails,
            boolean locationProvided,
            String draftLanguage) {}

    public record ComplaintDraftRequest(
            String issueType,
            String prabhagId,
            String resolutionMethod,
            Boolean citizenConfirmed,
            String boundaryDatasetVersion,
            Boolean classificationConfirmed,
            String citizenDescription,
            String locationDetails,
            String draftLanguage) {}

    public record ComplaintDraftResponse(
            String status,
            String draftVersion,
            String schemaVersion,
            String packVersion,
            String language,
            String routeId,
            String prabhagId,
            String authority,
            String authorityLocalName,
            String subject,
            String body,
            List<String> missingDetails,
            boolean citizenReviewRequired,
            String modelVersion,
            String responseId,
            long latencyMs,
            String generatedAt,
            Long promptTokenCount,
            Long candidatesTokenCount,
            Long totalTokenCount) {}

    public static final class ComplaintDraftInputException extends IllegalArgumentException {
        private final String code;

        ComplaintDraftInputException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public static final class ComplaintDraftExecutionException extends RuntimeException {
        private final String code;

        ComplaintDraftExecutionException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
