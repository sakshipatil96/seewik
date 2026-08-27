package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ProductionDay8Set2ReleaseIT {
    private static final String PROJECT_ID = "seewik";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void organiserLifecycleAndConcurrentJoinProtection() throws Exception {
        String apiKey = System.getProperty("seewik.firebase.api-key");
        assertNotNull(apiKey, "Firebase web API key is required");
        String backendUrl = System.getProperty(
                "seewik.backend-url", "https://seewik-api-528138216934.asia-south1.run.app");
        FirebaseAdminProvider firebase = new FirebaseAdminProvider(PROJECT_ID);
        AnonymousSession organiser = null;
        AnonymousSession participant = null;
        String cancelledId = null;
        String completedId = null;
        try {
            organiser = signInAnonymously(apiKey);
            participant = signInAnonymously(apiKey);

            JsonNode cancelled = create(backendUrl, organiser.idToken(), "Cancellation check");
            cancelledId = cancelled.path("initiativeId").asText();
            assertEquals(1, cancelled.path("participantCount").asInt());
            assertFalse(cancelled.has("ownerUid"));
            assertFalse(cancelled.has("latitude"));
            assertFalse(cancelled.has("longitude"));

            String joinUrl = backendUrl + "/api/initiatives/" + cancelledId + "/join";
            CompletableFuture<HttpResponse<String>> joinOne = jsonAsync("POST", joinUrl, participant.idToken(), null);
            CompletableFuture<HttpResponse<String>> joinTwo = jsonAsync("POST", joinUrl, participant.idToken(), null);
            JsonNode firstJoin = mapper.readTree(joinOne.get().body());
            JsonNode secondJoin = mapper.readTree(joinTwo.get().body());
            assertTrue(
                    ("JOINED".equals(firstJoin.path("status").asText())
                            && "ALREADY_JOINED".equals(secondJoin.path("status").asText()))
                    || ("ALREADY_JOINED".equals(firstJoin.path("status").asText())
                            && "JOINED".equals(secondJoin.path("status").asText())));

            assertEquals(403, json(
                    "POST",
                    backendUrl + "/api/initiatives/" + cancelledId + "/cancel",
                    participant.idToken(),
                    Map.of("reason", "Not allowed")).statusCode());
            assertEquals(403, json(
                    "POST",
                    backendUrl + "/api/initiatives/" + cancelledId + "/complete",
                    participant.idToken(), null).statusCode());

            HttpResponse<String> cancel = json(
                    "POST",
                    backendUrl + "/api/initiatives/" + cancelledId + "/cancel",
                    organiser.idToken(),
                    Map.of("reason", "Heavy rain"));
            assertEquals(200, cancel.statusCode(), cancel.body());
            assertEquals(0, mapper.readTree(cancel.body()).path("pointsAwarded").asInt());
            HttpResponse<String> cancelReplay = json(
                    "POST",
                    backendUrl + "/api/initiatives/" + cancelledId + "/cancel",
                    organiser.idToken(),
                    Map.of("reason", "Heavy rain"));
            assertTrue(mapper.readTree(cancelReplay.body()).path("idempotentReplay").asBoolean());
            assertEquals(409, json(
                    "POST",
                    backendUrl + "/api/initiatives/" + cancelledId + "/complete",
                    organiser.idToken(), null).statusCode());

            JsonNode participantMine = mapper.readTree(json(
                    "GET", backendUrl + "/api/initiatives/mine", participant.idToken(), null).body());
            JsonNode cancelledForParticipant = findInitiative(participantMine, cancelledId);
            assertEquals("CANCELLED", cancelledForParticipant.path("status").asText());
            assertEquals("Heavy rain", cancelledForParticipant.path("cancellationReason").asText());
            assertFalse(cancelledForParticipant.path("canManage").asBoolean());

            JsonNode completed = create(backendUrl, organiser.idToken(), "Completion check");
            completedId = completed.path("initiativeId").asText();
            firebase.firestore().collection("initiatives").document(completedId)
                    .update("startAt", Instant.now().minus(1, ChronoUnit.HOURS).toString()).get();
            HttpResponse<String> completion = json(
                    "POST",
                    backendUrl + "/api/initiatives/" + completedId + "/complete",
                    organiser.idToken(), null);
            assertEquals(200, completion.statusCode(), completion.body());
            assertEquals(0, mapper.readTree(completion.body()).path("pointsAwarded").asInt());
            HttpResponse<String> completionReplay = json(
                    "POST",
                    backendUrl + "/api/initiatives/" + completedId + "/complete",
                    organiser.idToken(), null);
            assertTrue(mapper.readTree(completionReplay.body()).path("idempotentReplay").asBoolean());
            assertEquals(409, json(
                    "POST",
                    backendUrl + "/api/initiatives/" + completedId + "/cancel",
                    organiser.idToken(), Map.of("reason", "Too late")).statusCode());

            assertStoredState(firebase, cancelledId, "CANCELLED", 2, 3, 2);
            assertStoredState(firebase, completedId, "COMPLETED", 1, 2, 1);
        } finally {
            if (cancelledId != null && !cancelledId.isBlank()) cleanupInitiative(firebase, cancelledId);
            if (completedId != null && !completedId.isBlank()) cleanupInitiative(firebase, completedId);
            if (organiser != null) firebase.auth().deleteUser(organiser.uid());
            if (participant != null) firebase.auth().deleteUser(participant.uid());
            for (FirebaseApp app : FirebaseApp.getApps()) {
                if ("seewik-backend".equals(app.getName())) app.delete();
            }
        }
    }

    private JsonNode create(String backendUrl, String idToken, String title) throws Exception {
        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("title", title);
        activity.put("category", "CLEANUP");
        activity.put("description", "Temporary lifecycle verification activity; removed after testing.");
        activity.put("startAt", Instant.now().plus(2, ChronoUnit.DAYS).toString());
        activity.put("placeName", "Public test meeting point");
        activity.put("latitude", 21.3700);
        activity.put("longitude", 74.2400);
        activity.put("needs", "None");
        activity.put("participantCount", 999);
        HttpResponse<String> response = json("POST", backendUrl + "/api/initiatives", idToken, activity);
        assertEquals(201, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private void assertStoredState(
            FirebaseAdminProvider firebase,
            String initiativeId,
            String status,
            int participants,
            int events,
            int ledgerEntries) throws Exception {
        var stored = firebase.firestore().collection("initiatives").document(initiativeId).get().get();
        assertEquals(status, stored.getString("status"));
        assertEquals(participants, stored.getLong("participantCount").intValue());
        assertEquals(events, stored.getReference().collection("events").get().get().size());
        var ledger = firebase.firestore().collection("pointsLedger")
                .whereEqualTo("sourceId", initiativeId).get().get().getDocuments();
        assertEquals(ledgerEntries, ledger.size());
        for (var entry : ledger) assertEquals(0L, entry.getLong("pointsAwarded"));
    }

    private HttpResponse<String> json(String method, String url, String idToken, Object body) throws Exception {
        return jsonAsync(method, url, idToken, body).get();
    }

    private CompletableFuture<HttpResponse<String>> jsonAsync(
            String method, String url, String idToken, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + idToken)
                .header("Content-Type", "application/json");
        String json = body == null ? "" : mapper.writeValueAsString(body);
        request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));
        return http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private AnonymousSession signInAnonymously(String apiKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"returnSecureToken\":true}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = mapper.readTree(response.body());
        return new AnonymousSession(body.path("localId").asText(), body.path("idToken").asText());
    }

    private static JsonNode findInitiative(JsonNode response, String initiativeId) {
        for (JsonNode initiative : response.path("initiatives")) {
            if (initiativeId.equals(initiative.path("initiativeId").asText())) return initiative;
        }
        throw new AssertionError("Activity missing from response");
    }

    private static void cleanupInitiative(FirebaseAdminProvider firebase, String initiativeId) throws Exception {
        var initiative = firebase.firestore().collection("initiatives").document(initiativeId);
        for (var event : initiative.collection("events").get().get().getDocuments()) event.getReference().delete().get();
        for (var participation : firebase.firestore().collection("initiativeParticipations")
                .whereEqualTo("initiativeId", initiativeId).get().get().getDocuments()) {
            participation.getReference().delete().get();
        }
        for (var entry : firebase.firestore().collection("pointsLedger")
                .whereEqualTo("sourceId", initiativeId).get().get().getDocuments()) {
            entry.getReference().delete().get();
        }
        if (initiative.get().get().exists()) initiative.delete().get();
    }

    private record AnonymousSession(String uid, String idToken) {}
}
