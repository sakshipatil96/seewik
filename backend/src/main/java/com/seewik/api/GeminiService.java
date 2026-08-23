package com.seewik.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeminiService {
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newHttpClient();
    private final String projectId;

    public GeminiService(ObjectMapper json, @Value("${GOOGLE_CLOUD_PROJECT:seewik}") String projectId) {
        this.json = json;
        this.projectId = projectId;
    }

    public String generate(String prompt, byte[] image, String mimeType) throws Exception {
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
        body.put("generationConfig", Map.of("maxOutputTokens", 256, "temperature", 0.2));

        String endpoint = "https://aiplatform.googleapis.com/v1/projects/%s/locations/global/publishers/google/models/gemini-3.7-flash:generateContent".formatted(projectId);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Vertex AI returned " + response.statusCode() + ": " + response.body());
        }
        JsonNode root = json.readTree(response.body());
        JsonNode text = root.at("/candidates/0/content/parts/0/text");
        if (text.isMissingNode()) throw new IllegalStateException("No text in Vertex AI response");
        return text.asText();
    }
}

