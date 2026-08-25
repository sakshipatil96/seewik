package com.seewik.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ComplaintDraftControllerTest {
    @Test
    void validRequestReturnsDraftAndDeterministicRecipient() throws Exception {
        mvcReturning(ComplaintDraftValidatorTest.validDraft()).perform(post("/api/civic/draft-complaint")
                        .contentType("application/json")
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT_READY"))
                .andExpect(jsonPath("$.draftVersion").value("complaint-draft-v0.1"))
                .andExpect(jsonPath("$.schemaVersion").value("complaint-draft-v0.1"))
                .andExpect(jsonPath("$.packVersion").value("v0.2"))
                .andExpect(jsonPath("$.language").value("MR"))
                .andExpect(jsonPath("$.authority").value("Nandurbar Municipal Council"))
                .andExpect(jsonPath("$.routeId").value("NMC-PW-POTHOLE-v0.2"))
                .andExpect(jsonPath("$.citizenReviewRequired").value(true));
    }

    @Test
    void unconfirmedCategoryReturnsControlledClientError() throws Exception {
        mvcReturning(ComplaintDraftValidatorTest.validDraft()).perform(post("/api/civic/draft-complaint")
                        .contentType("application/json")
                        .content(validRequest().replace("\"classificationConfirmed\":true", "\"classificationConfirmed\":false")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("DRAFT_ERROR"))
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_CONFIRMATION_REQUIRED"));
    }

    @Test
    void unsupportedRouteReturnsUnprocessableEntityWithoutDraft() throws Exception {
        mvcReturning(ComplaintDraftValidatorTest.validDraft()).perform(post("/api/civic/draft-complaint")
                        .contentType("application/json")
                        .content(validRequest().replace("POTHOLE_ROAD_DAMAGE", "ALIEN_INVASION")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_ROUTE"))
                .andExpect(jsonPath("$.subject").doesNotExist());
    }

    @Test
    void invalidModelDraftReturnsControlledGatewayErrorWithoutRawOutput() throws Exception {
        mvcReturning("{\"subject\":\"Wrong\",\"body\":\"Raw invalid model output that must not be exposed.\"}")
                .perform(post("/api/civic/draft-complaint")
                        .contentType("application/json")
                        .content(validRequest()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("SCHEMA_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(
                        "A complaint draft could not be created. Your confirmed route remains available."));
    }

    private static MockMvc mvcReturning(String output) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        GeminiGateway gateway = (prompt, image, mime, schema) ->
                new GeminiGateway.GeneratedContent(output, "gemini-test", "response-test", 1L, 2L, 3L);
        ComplaintDraftService service = new ComplaintDraftService(
                gateway,
                new ComplaintPromptFactory(),
                new ComplaintDraftValidator(mapper),
                new CivicRouterService(mapper),
                mapper);
        return MockMvcBuilders.standaloneSetup(new ComplaintDraftController(service)).build();
    }

    private static String validRequest() {
        return """
                {
                  "issueType":"POTHOLE_ROAD_DAMAGE",
                  "prabhagId":"PRABHAG-03",
                  "resolutionMethod":"SELF_REPORTED",
                  "classificationConfirmed":true,
                  "citizenDescription":"रस्त्यावर मोठा खड्डा आहे",
                  "locationDetails":"बस स्थानकाजवळ",
                  "draftLanguage":"MR"
                }
                """;
    }
}
