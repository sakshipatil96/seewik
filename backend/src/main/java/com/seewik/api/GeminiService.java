package com.seewik.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeminiService implements GeminiGateway {
    public static final String MODEL = "gemini-3.7-flash";
    public static final String LOCATION = "global";
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newHttpClient();
    private final String projectId;

    public GeminiService(ObjectMapper json, @Value("${GOOGLE_CLOUD_PROJECT:seewik}") String projectId) {
        this.json = json;
        this.projectId = projectId;
    }

    public String generate(String prompt, byte[] image, String mimeType) throws Exception {
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("maxOutputTokens", 256);
        generationConfig.put("temperature", 0.2);
        return invoke(prompt, image, mimeType, generationConfig).text();
    }

    @Override
    public GeneratedContent generateStructured(
            String prompt,
            byte[] image,
            String mimeType,
            JsonNode responseSchema) throws Exception {
        return generateStructured(prompt, image, mimeType, responseSchema, 512);
    }

    @Override
    public GeneratedContent generateStructured(
            String prompt,
            byte[] image,
            String mimeType,
            JsonNode responseSchema,
            int maxOutputTokens) throws Exception {
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("maxOutputTokens", maxOutputTokens);
        generationConfig.put("temperature", 0.0);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", responseSchema);
        return invoke(prompt, image, mimeType, generationConfig);
    }

    @Override
    public GeneratedContent generateStructured(
            String prompt,
            byte[] image,
            String mimeType,
            JsonNode responseSchema,
            int maxOutputTokens,
            Duration timeout) throws Exception {
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("maxOutputTokens", maxOutputTokens);
        generationConfig.put("temperature", 0.0);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", responseSchema);
        return invoke(prompt, image, mimeType, generationConfig, timeout);
    }

    private GeneratedContent invoke(
            String prompt,
            byte[] image,
            String mimeType,
            Map<String, Object> generationConfig) throws Exception {
        return invoke(prompt, image, mimeType, generationConfig, null);
    }

    private GeneratedContent invoke(
            String prompt,
            byte[] image,
            String mimeType,
            Map<String, Object> generationConfig,
            Duration timeout) throws Exception {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped("https://www.googleapis.com/auth/cloud-platform");
        credentials.refreshIfExpired();

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));
        if (image != null) {
            parts.add(Map.of("inlineData", Map.of(
                    "mimeType", mimeType == null ? "image/jpeg" : mimeType,
                    "data", Base64.getEncoder().encodeToString(image))));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(Map.of("role", "user", "parts", parts)));
        body.put("generationConfig", generationConfig);

        String endpoint = "https://aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent"
                .formatted(projectId, LOCATION, MODEL);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue())
                .header("Content-Type", "application/json")
                .timeout(timeout == null ? Duration.ofSeconds(30) : timeout)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException exception) {
            throw new GeminiGateway.ModelTransportTimeoutException(exception);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Vertex AI request failed with status " + response.statusCode());
        }
        JsonNode root = json.readTree(response.body());
        JsonNode text = root.at("/candidates/0/content/parts/0/text");
        if (text.isMissingNode() || !text.isTextual() || text.asText().isBlank()) {
            throw new IllegalStateException("Vertex AI response did not contain generated text");
        }
        JsonNode usage = root.path("usageMetadata");
        String finishReason = nullableText(root.at("/candidates/0/finishReason"), null);
        return new GeneratedContent(
                text.asText(),
                nullableText(root.get("modelVersion"), MODEL),
                nullableText(root.get("responseId"), null),
                nullableLong(usage.get("promptTokenCount")),
                nullableLong(usage.get("candidatesTokenCount")),
                nullableLong(usage.get("totalTokenCount")),
                finishReason);
    }

    private static String nullableText(JsonNode node, String fallback) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : fallback;
    }

    private static Long nullableLong(JsonNode node) {
        return node != null && node.canConvertToLong() ? node.longValue() : null;
    }
}
