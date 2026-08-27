package com.seewik.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ClassificationControllerTest {
    private static final String AUTHORIZATION = "Bearer valid-token";
    private static final String VALID = """
            {
              "issueType": "POTHOLE_ROAD_DAMAGE",
              "subcategory": "Pothole",
              "description": "A pothole is described on a public road.",
              "confidence": 0.93,
              "detectedLanguage": "MR",
              "needsClarification": false,
              "clarificationQuestion": null
            }
            """;

    @Test
    void textOnlyClassificationReturnsValidatedMetadata() throws Exception {
        MockMvc mvc = mvcReturning(VALID);
        mvc.perform(multipart("/api/civic/classify").header("Authorization", AUTHORIZATION).param("text", "रस्त्यावर खड्डा आहे"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLASSIFIED"))
                .andExpect(jsonPath("$.issueType").value("POTHOLE_ROAD_DAMAGE"))
                .andExpect(jsonPath("$.detectedLanguage").value("MR"))
                .andExpect(jsonPath("$.confidence").value(0.93))
                .andExpect(jsonPath("$.schemaVersion").value("classification-v0.1"))
                .andExpect(jsonPath("$.packVersion").value("v0.2"));
    }

    @Test
    void imageClassificationAcceptsSafeMimeTypes() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "road.png", "image/png", new byte[] {1});
        mvcReturning(VALID).perform(multipart("/api/civic/classify").file(image).header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLASSIFIED"));
    }

    @Test
    void emptyEvidenceReturnsControlledClientError() throws Exception {
        mvcReturning(VALID).perform(multipart("/api/civic/classify").header("Authorization", AUTHORIZATION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("CLASSIFICATION_ERROR"))
                .andExpect(jsonPath("$.errorCode").value("EMPTY_EVIDENCE"));
    }

    @Test
    void malformedModelOutputReturnsControlledGatewayErrorWithoutRawOutput() throws Exception {
        mvcReturning("not json").perform(multipart("/api/civic/classify").header("Authorization", AUTHORIZATION).param("text", "test"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("CLASSIFICATION_ERROR"))
                .andExpect(jsonPath("$.errorCode").value("SCHEMA_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Classification could not be completed. Please choose a category manually."));
    }

    @Test
    void unreadableImageReturnsControlledClientError() throws Exception {
        MultipartFile image = new MultipartFile() {
            public String getName() { return "image"; }
            public String getOriginalFilename() { return "unreadable.png"; }
            public String getContentType() { return "image/png"; }
            public boolean isEmpty() { return false; }
            public long getSize() { return 1; }
            public byte[] getBytes() throws IOException { throw new IOException("test read failure"); }
            public InputStream getInputStream() throws IOException { throw new IOException("test read failure"); }
            public void transferTo(java.io.File destination) throws IOException { throw new IOException("test read failure"); }
            public void transferTo(Path destination) throws IOException { throw new IOException("test read failure"); }
        };
        var response = controllerReturning(VALID).classify(AUTHORIZATION, image, null);
        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("IMAGE_READ_FAILED", body.get("errorCode"));
    }

    @Test
    void missingFirebaseTokenIsRejectedBeforeCallingTheModel() throws Exception {
        mvcReturning(VALID).perform(multipart("/api/civic/classify").param("text", "test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rateLimitedRequestReturns429AndNeverCallsModelService() throws Exception {
        AtomicInteger modelCalls = new AtomicInteger();
        CivicClassificationService service = service((prompt, image, mime, schema) -> {
            modelCalls.incrementAndGet();
            return generated(VALID);
        });
        PaidEndpointRateLimiter limiter = (uid, endpoint) -> {
            throw new PaidEndpointRateLimiter.RateLimitedException("per_user", 7);
        };
        ClassificationController controller = new ClassificationController(
                service, validVerifier(), limiter, new OperationalMetrics(new ObjectMapper(), "test"));

        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(multipart("/api/civic/classify")
                        .header("Authorization", AUTHORIZATION).param("text", "test"))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Retry-After", "7"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"));
        assertEquals(0, modelCalls.get());
    }

    @Test
    void modelTimeoutReturns504WithManualCategoryFallback() throws Exception {
        CivicClassificationService service = service((prompt, image, mime, schema) -> {
            throw new GeminiGateway.ModelTransportTimeoutException(new java.net.http.HttpTimeoutException("deadline"));
        });
        ClassificationController controller = new ClassificationController(
                service, validVerifier(), (uid, endpoint) -> {},
                new OperationalMetrics(new ObjectMapper(), "test"));

        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(multipart("/api/civic/classify")
                        .header("Authorization", AUTHORIZATION).param("text", "test"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.errorCode").value("MODEL_TIMEOUT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("manually")));
    }

    private static MockMvc mvcReturning(String output) throws Exception {
        return MockMvcBuilders.standaloneSetup(controllerReturning(output)).build();
    }

    private static ClassificationController controllerReturning(String output) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        GeminiGateway gateway = (prompt, image, mime, schema) ->
                generated(output);
        CivicClassificationService service = service(gateway);
        CitizenIdentityVerifier verifier = validVerifier();
        PaidEndpointRateLimiter limiter = (uid, endpoint) -> {};
        return new ClassificationController(service, verifier, limiter, new OperationalMetrics(mapper, "test"));
    }

    private static CivicClassificationService service(GeminiGateway gateway) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return new CivicClassificationService(
                gateway,
                new ClassificationPromptFactory(mapper),
                new ClassificationSchemaValidator(mapper),
                mapper);
    }

    private static GeminiGateway.GeneratedContent generated(String output) {
        return new GeminiGateway.GeneratedContent(output, "gemini-test", "response-test", 1L, 2L, 3L);
    }

    private static CitizenIdentityVerifier validVerifier() {
        return authorization -> {
            if (!AUTHORIZATION.equals(authorization)) {
                throw new CitizenIdentityVerifier.AuthenticationException("A Firebase ID token is required");
            }
            return new CitizenIdentityVerifier.AuthenticatedCitizen("test-owner");
        };
    }
}
