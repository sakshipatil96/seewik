package com.seewik.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public class ClassificationSchemaValidator {
    public static final String SCHEMA_VERSION = "classification-v0.1";
    public static final double CONFIDENCE_THRESHOLD = 0.80d;
    public static final Set<String> DETECTED_LANGUAGES =
            Set.of("MR", "HI", "EN", "MIXED", "UNKNOWN");

    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "issueType",
            "subcategory",
            "description",
            "confidence",
            "detectedLanguage",
            "needsClarification",
            "clarificationQuestion");

    private final ObjectMapper objectMapper;
    private final Set<String> allowedIssueTypes;

    public ClassificationSchemaValidator(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        this.allowedIssueTypes = loadAllowedIssueTypes(objectMapper);
    }

    public ValidatedClassification validate(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw invalid("EMPTY_OUTPUT", "Classification output is empty");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (JsonProcessingException exception) {
            throw invalid("MALFORMED_JSON", "Classification output is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw invalid("INVALID_ROOT", "Classification output must be a JSON object");
        }

        Set<String> actualFields = new HashSet<>();
        root.fieldNames().forEachRemaining(actualFields::add);
        Set<String> missingFields = new TreeSet<>(REQUIRED_FIELDS);
        missingFields.removeAll(actualFields);
        if (!missingFields.isEmpty()) {
            throw invalid("MISSING_FIELDS", "Missing classification fields: " + missingFields);
        }
        Set<String> unexpectedFields = new TreeSet<>(actualFields);
        unexpectedFields.removeAll(REQUIRED_FIELDS);
        if (!unexpectedFields.isEmpty()) {
            throw invalid("UNEXPECTED_FIELDS", "Unexpected classification fields: " + unexpectedFields);
        }

        String issueType = requiredText(root, "issueType", 80);
        if (!allowedIssueTypes.contains(issueType)) {
            throw invalid("INVALID_ISSUE_TYPE", "Unsupported issueType: " + issueType);
        }

        String subcategory = optionalText(root, "subcategory", 160);
        String description = requiredText(root, "description", 1000);

        JsonNode confidenceNode = root.get("confidence");
        if (!confidenceNode.isNumber()) {
            throw invalid("INVALID_CONFIDENCE", "confidence must be a number");
        }
        double confidence = confidenceNode.doubleValue();
        if (!Double.isFinite(confidence) || confidence < 0.0d || confidence > 1.0d) {
            throw invalid("INVALID_CONFIDENCE", "confidence must be between 0 and 1");
        }

        String detectedLanguage = requiredText(root, "detectedLanguage", 16);
        if (!DETECTED_LANGUAGES.contains(detectedLanguage)) {
            throw invalid("INVALID_LANGUAGE", "Unsupported detectedLanguage: " + detectedLanguage);
        }

        JsonNode needsClarificationNode = root.get("needsClarification");
        if (!needsClarificationNode.isBoolean()) {
            throw invalid("INVALID_CLARIFICATION_FLAG", "needsClarification must be a boolean");
        }
        boolean needsClarification = needsClarificationNode.booleanValue();
        String clarificationQuestion = optionalText(root, "clarificationQuestion", 300);

        boolean clarificationRequired = "UNKNOWN".equals(issueType) || confidence < CONFIDENCE_THRESHOLD;
        if (needsClarification != clarificationRequired) {
            throw invalid(
                    "INCONSISTENT_CLARIFICATION",
                    clarificationRequired
                            ? "Low-confidence or UNKNOWN classifications must request clarification"
                            : "Supported classifications at or above 0.80 must not request clarification");
        }
        if (clarificationRequired && clarificationQuestion == null) {
            throw invalid(
                    "MISSING_CLARIFICATION_QUESTION",
                    "A non-empty clarificationQuestion is required when clarification is requested");
        }
        if (!clarificationRequired && clarificationQuestion != null) {
            throw invalid(
                    "UNEXPECTED_CLARIFICATION_QUESTION",
                    "clarificationQuestion must be null when clarification is not required");
        }

        return new ValidatedClassification(
                issueType,
                subcategory,
                description,
                confidence,
                detectedLanguage,
                needsClarification,
                clarificationQuestion);
    }

    public Set<String> allowedIssueTypes() {
        return allowedIssueTypes;
    }

    private static Set<String> loadAllowedIssueTypes(ObjectMapper objectMapper) throws IOException {
        try (InputStream input = ClassificationSchemaValidator.class.getResourceAsStream("/civic-pack-v0.2.json")) {
            if (input == null) {
                throw new IOException("Missing Civic Pack resource civic-pack-v0.2.json");
            }
            JsonNode pack = objectMapper.readTree(input);
            Set<String> issueTypes = new LinkedHashSet<>();
            for (JsonNode route : pack.path("routes")) {
                String issueType = route.path("issueType").asText();
                if (issueType.isBlank() || !issueTypes.add(issueType)) {
                    throw new IOException("Invalid or duplicate Civic Pack issueType: " + issueType);
                }
            }
            issueTypes.add("UNKNOWN");
            return Set.copyOf(issueTypes);
        }
    }

    private static String requiredText(JsonNode root, String field, int maxLength) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual()) {
            throw invalid("INVALID_TEXT_FIELD", field + " must be a string");
        }
        String text = value.textValue().trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw invalid("INVALID_TEXT_FIELD", field + " must contain 1 to " + maxLength + " characters");
        }
        return text;
    }

    private static String optionalText(JsonNode root, String field, int maxLength) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) {
            throw invalid("INVALID_TEXT_FIELD", field + " must be a string or null");
        }
        String text = value.textValue().trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw invalid("INVALID_TEXT_FIELD", field + " must be null or contain 1 to " + maxLength + " characters");
        }
        return text;
    }

    private static ClassificationValidationException invalid(String code, String message) {
        return new ClassificationValidationException(code, message);
    }

    public record ValidatedClassification(
            String issueType,
            String subcategory,
            String description,
            double confidence,
            String detectedLanguage,
            boolean needsClarification,
            String clarificationQuestion) {}

    public static final class ClassificationValidationException extends IllegalArgumentException {
        private final String code;

        ClassificationValidationException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
