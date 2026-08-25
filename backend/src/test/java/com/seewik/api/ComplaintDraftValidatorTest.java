package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ComplaintDraftValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ComplaintDraftValidator validator = new ComplaintDraftValidator(mapper);

    @Test
    void acceptsGroundedMarathiSubjectAndBody() {
        var result = validator.validate(validDraft(), "MR");
        assertEquals("रस्त्यावरील खड्ड्याबाबत तक्रार", result.subject());
        assertFalse(result.body().isBlank());
    }

    @Test
    void rejectsMalformedJson() {
        assertCode("{not-json", "MR", "MALFORMED_JSON");
    }

    @Test
    void rejectsMissingField() {
        assertCode("{\"subject\":\"मराठी विषय\"}", "MR", "MISSING_FIELDS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"authority", "department", "routeId", "officialChannels", "sla", "escalation"})
    void rejectsRoutingAndOtherUnexpectedFields(String field) {
        String raw = validDraft().replace("\n}", ",\n  \"" + field + "\": \"forbidden\"\n}");
        assertCode(raw, "MR", "UNEXPECTED_FIELDS");
    }

    @Test
    void rejectsNonMarathiDraft() {
        assertCode("""
                {"subject":"Pothole complaint","body":"There is a large pothole on the public road. Please take action."}
                """, "MR", "WRONG_DRAFT_LANGUAGE");
    }

    @Test
    void acceptsEnglishWhenEnglishIsRequested() {
        var result = validator.validate(validEnglishDraft(), "EN");
        assertEquals("Complaint about a pothole on the road", result.subject());
    }

    @ParameterizedTest
    @ValueSource(strings = {"help@example.com", "https://example.com/form", "9876543210"})
    void rejectsInventedContactDetails(String contact) {
        String raw = validDraft().replace("आवश्यक कार्यवाही करावी.", "आवश्यक कार्यवाही करावी. " + contact);
        assertCode(raw, "MR", "UNVERIFIED_CONTACT_DETAIL");
    }

    @Test
    void strictAndVertexSchemasStayAligned() throws Exception {
        JsonNode strict;
        JsonNode vertex;
        try (InputStream input = getClass().getResourceAsStream("/complaint-draft-schema-v0.1.json")) {
            assertNotNull(input);
            strict = mapper.readTree(input);
        }
        try (InputStream input = getClass().getResourceAsStream("/complaint-draft-response-schema-vertex-v0.1.json")) {
            assertNotNull(input);
            vertex = mapper.readTree(input);
        }
        assertEquals("complaint-draft-v0.1", strict.path("properties").path("draftVersion").path("const").asText());
        assertEquals(Set.of("MR", "EN"), values(strict.path("properties").path("language").path("enum")));
        assertEquals(Set.of("subject", "body"), values(vertex.path("required")));
        assertEquals(vertex.path("required"), vertex.path("propertyOrdering"));
        assertFalse(strict.path("additionalProperties").asBoolean(true));
        assertFalse(vertex.path("properties").has("authority"));
    }

    private void assertCode(String raw, String language, String expectedCode) {
        var exception = assertThrows(
                ComplaintDraftValidator.ComplaintDraftValidationException.class,
                () -> validator.validate(raw, language));
        assertEquals(expectedCode, exception.code());
    }

    private static Set<String> values(JsonNode array) {
        Set<String> values = new HashSet<>();
        for (JsonNode value : array) values.add(value.asText());
        return values;
    }

    static String validDraft() {
        return """
                {
                  "subject": "रस्त्यावरील खड्ड्याबाबत तक्रार",
                  "body": "आमच्या परिसरातील रस्त्यावर मोठा खड्डा आहे. कृपया आवश्यक कार्यवाही करावी."
                }
                """;
    }

    static String validEnglishDraft() {
        return """
                {
                  "subject": "Complaint about a pothole on the road",
                  "body": "There is a large pothole on the road in our area. Please inspect it and take necessary action."
                }
                """;
    }
}
