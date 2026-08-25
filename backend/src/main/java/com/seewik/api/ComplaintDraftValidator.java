package com.seewik.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ComplaintDraftValidator {
    public static final String DRAFT_VERSION = "complaint-draft-v0.1";
    public static final String SCHEMA_VERSION = "complaint-draft-v0.1";
    public static final Set<String> DRAFT_LANGUAGES = Set.of("MR", "EN");
    private static final Set<String> REQUIRED_FIELDS = Set.of("subject", "body");
    private static final Pattern DEVANAGARI = Pattern.compile("[\\p{InDevanagari}]");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern URL = Pattern.compile("(?i)https?://|www\\.");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?91[- ]?)?[6-9]\\d{9}(?!\\d)");
    private final ObjectMapper json;

    public ComplaintDraftValidator(ObjectMapper json) {
        this.json = json;
    }

    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");

    public ValidatedDraft validate(String raw, String draftLanguage) {
        if (!DRAFT_LANGUAGES.contains(draftLanguage)) {
            throw invalid("INVALID_DRAFT_LANGUAGE", "Draft language must be MR or EN");
        }
        JsonNode root;
        try {
            root = json.readTree(raw);
        } catch (JsonProcessingException exception) {
            throw invalid("MALFORMED_JSON", "Complaint draft is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw invalid("INVALID_ROOT", "Complaint draft must be a JSON object");
        }
        Set<String> actual = new java.util.HashSet<>();
        root.fieldNames().forEachRemaining(actual::add);
        if (!actual.containsAll(REQUIRED_FIELDS)) {
            throw invalid("MISSING_FIELDS", "Complaint draft is missing required fields");
        }
        if (!REQUIRED_FIELDS.containsAll(actual)) {
            throw invalid("UNEXPECTED_FIELDS", "Complaint model must not return routing or other fields");
        }
        String subject = text(root.get("subject"), "subject", 1, 160);
        String body = text(root.get("body"), "body", 20, 2500);
        Pattern expectedScript = "MR".equals(draftLanguage) ? DEVANAGARI : LATIN;
        if (!expectedScript.matcher(subject).find() || !expectedScript.matcher(body).find()) {
            throw invalid("WRONG_DRAFT_LANGUAGE", "Subject and body must use the requested draft language");
        }
        String combined = subject + "\n" + body;
        if (EMAIL.matcher(combined).find() || URL.matcher(combined).find() || PHONE.matcher(combined).find()) {
            throw invalid("UNVERIFIED_CONTACT_DETAIL", "Complaint draft must not invent contact details");
        }
        return new ValidatedDraft(subject, body);
    }

    private static String text(JsonNode value, String field, int minLength, int maxLength) {
        if (value == null || !value.isTextual()) {
            throw invalid("INVALID_TEXT_FIELD", field + " must be text");
        }
        String text = value.textValue().strip();
        if (text.length() < minLength || text.length() > maxLength) {
            throw invalid("INVALID_TEXT_FIELD", field + " must contain " + minLength + " to " + maxLength + " characters");
        }
        return text;
    }

    private static ComplaintDraftValidationException invalid(String code, String message) {
        return new ComplaintDraftValidationException(code, message);
    }

    public record ValidatedDraft(String subject, String body) {}

    public static final class ComplaintDraftValidationException extends IllegalArgumentException {
        private final String code;

        ComplaintDraftValidationException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
