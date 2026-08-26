package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.google.firebase.FirebaseApp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionDay7ReleaseIT {
    private static final String PROJECT_ID = "seewik";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void authenticatedPaidEndpointsAndTwoUserInitiativeFlow() throws Exception {
        String apiKey = System.getProperty("seewik.firebase.api-key");
        assertNotNull(apiKey, "Run with -Dseewik.firebase.api-key=<Firebase web API key>");
        String backendUrl = System.getProperty(
                "seewik.backend-url", "https://seewik-api-528138216934.asia-south1.run.app");
        FirebaseAdminProvider firebase = new FirebaseAdminProvider(PROJECT_ID);
        AnonymousSession organiser = null;
        AnonymousSession participant = null;
        String initiativeId = null;
        try {
            organiser = signInAnonymously(apiKey);
            participant = signInAnonymously(apiKey);

            HttpResponse<String> classification = classify(backendUrl, organiser.idToken());
            assertEquals(200, classification.statusCode(), classification.body());
            JsonNode classificationBody = mapper.readTree(classification.body());
            assertEquals("CLASSIFIED", classificationBody.path("status").asText());
            assertEquals("POTHOLE_ROAD_DAMAGE", classificationBody.path("issueType").asText());

            HttpResponse<String> draft = json(
                    "POST",
                    backendUrl + "/api/civic/draft-complaint",
                    organiser.idToken(),
                    Map.ofEntries(
                            Map.entry("issueType", "POTHOLE_ROAD_DAMAGE"),
                            Map.entry("prabhagId", "PRABHAG-03"),
                            Map.entry("resolutionMethod", "SELF_REPORTED"),
                            Map.entry("classificationConfirmed", true),
                            Map.entry("citizenDescription", "A large pothole is making a public road unsafe."),
                            Map.entry("locationDetails", "Near the public bus stand"),
                            Map.entry("draftLanguage", "EN")));
            assertEquals(200, draft.statusCode(), draft.body());
            JsonNode draftBody = mapper.readTree(draft.body());
            assertEquals("DRAFT_READY", draftBody.path("status").asText());
            assertEquals("Nandurbar Municipal Council", draftBody.path("authority").asText());

            Map<String, Object> activity = new LinkedHashMap<>();
            activity.put("title", "Day 7 release clean-up");
            activity.put("category", "CLEANUP");
            activity.put("description", "Disposable production verification activity; removed after testing.");
            activity.put("startAt", Instant.now().plus(2, ChronoUnit.DAYS).toString());
            activity.put("placeName", "Nehru Chowk public meeting point");
            activity.put("latitude", 21.3700);
            activity.put("longitude", 74.2400);
            activity.put("needs", "Bring reusable water bottles");
            HttpResponse<String> create = json(
                    "POST", backendUrl + "/api/initiatives", organiser.idToken(), activity);
            assertEquals(201, create.statusCode(), create.body());
            JsonNode created = mapper.readTree(create.body());
            initiativeId = created.path("initiativeId").asText();
            assertFalse(initiativeId.isBlank());
            assertEquals("PUBLISHED", created.path("status").asText());
            assertEquals(1, created.path("participantCount").asInt());
            assertFalse(created.has("ownerUid"));
            assertFalse(created.has("latitude"));
            assertFalse(created.has("longitude"));

            HttpResponse<String> nearby = json(
                    "POST",
                    backendUrl + "/api/initiatives/nearby",
                    participant.idToken(),
                    Map.of("latitude", 21.3700, "longitude", 74.2400, "radiusKm", 5));
            assertEquals(200, nearby.statusCode(), nearby.body());
            JsonNode discovered = findInitiative(mapper.readTree(nearby.body()), initiativeId);
            assertFalse(discovered.isMissingNode(), nearby.body());
            assertEquals(1, discovered.path("participantCount").asInt());
            assertFalse(discovered.has("ownerUid"));
            assertFalse(discovered.has("latitude"));
            assertFalse(discovered.has("longitude"));

            HttpResponse<String> firstJoin = json(
                    "POST", backendUrl + "/api/initiatives/" + initiativeId + "/join",
                    participant.idToken(), null);
            assertEquals(200, firstJoin.statusCode(), firstJoin.body());
            assertEquals("JOINED", mapper.readTree(firstJoin.body()).path("status").asText());
            assertEquals(2, mapper.readTree(firstJoin.body()).path("participantCount").asInt());

            HttpResponse<String> secondJoin = json(
                    "POST", backendUrl + "/api/initiatives/" + initiativeId + "/join",
                    participant.idToken(), null);
            assertEquals(200, secondJoin.statusCode(), secondJoin.body());
            assertEquals("ALREADY_JOINED", mapper.readTree(secondJoin.body()).path("status").asText());
            assertEquals(2, mapper.readTree(secondJoin.body()).path("participantCount").asInt());

            var stored = firebase.firestore().collection("initiatives").document(initiativeId).get().get();
            assertEquals(2L, stored.getLong("participantCount"));
            var participations = firebase.firestore().collection("initiativeParticipations")
                    .whereEqualTo("initiativeId", initiativeId).get().get().getDocuments();
            assertEquals(2, participations.size());
            var events = firebase.firestore().collection("initiatives").document(initiativeId)
                    .collection("events").get().get().getDocuments();
            assertEquals(2, events.size());
            var ledger = firebase.firestore().collection("pointsLedger")
                    .whereEqualTo("sourceId", initiativeId).get().get().getDocuments();
            assertEquals(2, ledger.size());
            for (var entry : ledger) {
                assertEquals(0L, entry.getLong("awardedPoints"));
                assertEquals(0L, entry.getLong("pointsAwarded"));
                assertEquals("RECORDED_NOT_REWARDED", entry.getString("policyStatus"));
            }

            System.out.println(mapper.writeValueAsString(Map.ofEntries(
                    Map.entry("status", "PASS"),
                    Map.entry("backendUrl", backendUrl),
                    Map.entry("classificationAuthenticated", true),
                    Map.entry("classificationIssueType", classificationBody.path("issueType").asText()),
                    Map.entry("draftAuthenticated", true),
                    Map.entry("draftAuthority", draftBody.path("authority").asText()),
                    Map.entry("initiativeCreated", true),
                    Map.entry("secondUserDiscovered", true),
                    Map.entry("firstJoinStatus", "JOINED"),
                    Map.entry("secondJoinStatus", "ALREADY_JOINED"),
                    Map.entry("participantCount", 2),
                    Map.entry("ledgerEntries", 2),
                    Map.entry("initiativePointsAwarded", 0),
                    Map.entry("privateCoordinatesExcluded", true))));
        } finally {
            if (initiativeId != null && !initiativeId.isBlank()) cleanupInitiative(firebase, initiativeId);
            if (organiser != null) firebase.auth().deleteUser(organiser.uid());
            if (participant != null) firebase.auth().deleteUser(participant.uid());
            boolean removed = initiativeId == null || !firebase.firestore()
                    .collection("initiatives").document(initiativeId).get().get().exists();
            assertTrue(removed);
            System.out.println(mapper.writeValueAsString(Map.of(
                    "cleanup", "PASS",
                    "initiativeRemoved", removed,
                    "temporaryUsersDeleted", organiser != null && participant != null)));
            for (FirebaseApp app : FirebaseApp.getApps()) {
                if ("seewik-backend".equals(app.getName())) app.delete();
            }
        }
    }

    private HttpResponse<String> classify(String backendUrl, String idToken) throws Exception {
        String boundary = "seewik-" + UUID.randomUUID();
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"text\"\r\n\r\n"
                + "A dangerous pothole is in the middle of a public city road.\r\n"
                + "--" + boundary + "--\r\n";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + "/api/civic/classify"))
                .header("Authorization", "Bearer " + idToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> json(
            String method, String url, String idToken, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + idToken)
                .header("Content-Type", "application/json");
        String json = body == null ? "" : mapper.writeValueAsString(body);
        request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
        return MissingNode.getInstance();
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
