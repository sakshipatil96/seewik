package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.ImportUserRecord;
import com.google.firebase.auth.UserProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionDay13RewardsHttpIT {
    private static final String PROJECT_ID = "seewik";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void deployedApiEnforcesRewardOwnershipAndCleansFixtures() throws Exception {
        String apiKey = System.getProperty("seewik.firebase.api-key");
        assertNotNull(apiKey, "Firebase web API key is required");
        String backendUrl = System.getProperty(
                "seewik.backend-url", "https://seewik-api-528138216934.asia-south1.run.app");
        FirebaseAdminProvider firebase = new FirebaseAdminProvider(PROJECT_ID);
        String runId = UUID.randomUUID().toString().replace("-", "");
        String ownerUid = "day13httpowner" + runId.substring(0, 16);
        String otherUid = "day13httpother" + runId.substring(0, 16);
        try {
            importLinkedUser(firebase, ownerUid, "owner", runId);
            importLinkedUser(firebase, otherUid, "other", runId);
            seedAward(firebase, ownerUid, runId, "fix", "FIX_VERIFIED", 60);
            seedAward(firebase, ownerUid, runId, "organiser", "INITIATIVE_ORGANISER_COMPLETED_REWARDED", 40);
            LinkedSession owner = signInWithCustomToken(firebase, apiKey, ownerUid);
            LinkedSession other = signInWithCustomToken(firebase, apiKey, otherUid);

            HttpResponse<String> overviewResponse = json(
                    "GET", backendUrl + "/api/recognition/me/rewards", owner.idToken(), null);
            assertStatus(200, overviewResponse);
            JsonNode overview = mapper.readTree(overviewResponse.body());
            assertEquals(100, overview.path("lifetimePoints").asInt());
            assertEquals("UNLOCKED", coupon(overview, "coupon-juthalal-100").path("claimStatus").asText());

            HttpResponse<String> claimResponse = json(
                    "POST", backendUrl + "/api/recognition/me/rewards/claims", owner.idToken(),
                    Map.of("couponId", "coupon-juthalal-100"));
            assertStatus(201, claimResponse);
            JsonNode claim = mapper.readTree(claimResponse.body());
            String claimId = claim.path("claimId").asText();
            String code = claim.path("code").asText();
            assertFalse(claimId.isBlank());
            assertTrue(code.matches("SEE-[0-9A-F]{4}-[0-9A-F]{4}"));

            HttpResponse<String> replayResponse = json(
                    "POST", backendUrl + "/api/recognition/me/rewards/claims", owner.idToken(),
                    Map.of("couponId", "coupon-juthalal-100"));
            assertStatus(200, replayResponse);
            JsonNode replay = mapper.readTree(replayResponse.body());
            assertEquals(claimId, replay.path("claimId").asText());
            assertEquals(code, replay.path("code").asText());

            HttpResponse<String> forbidden = json(
                    "POST", backendUrl + "/api/recognition/me/rewards/claims/" + claimId + "/simulate-use",
                    other.idToken(), null);
            assertStatus(403, forbidden);
            assertEquals("REWARD_CLAIM_FORBIDDEN", mapper.readTree(forbidden.body()).path("errorCode").asText());

            HttpResponse<String> usedResponse = json(
                    "POST", backendUrl + "/api/recognition/me/rewards/claims/" + claimId + "/simulate-use",
                    owner.idToken(), null);
            assertStatus(200, usedResponse);
            JsonNode used = mapper.readTree(usedResponse.body());
            assertEquals("USED", used.path("claimStatus").asText());
            assertNotEquals("", used.path("usedAt").asText());

            HttpResponse<String> finalOverviewResponse = json(
                    "GET", backendUrl + "/api/recognition/me/rewards", owner.idToken(), null);
            assertStatus(200, finalOverviewResponse);
            JsonNode finalOverview = mapper.readTree(finalOverviewResponse.body());
            assertEquals(100, finalOverview.path("lifetimePoints").asInt());
            assertEquals("USED", coupon(finalOverview, "coupon-juthalal-100").path("claimStatus").asText());
            assertEquals(1, claims(firebase, ownerUid).size());
            assertEquals(2, events(firebase, ownerUid).size());
            assertTrue(events(firebase, ownerUid).stream().allMatch(event -> !event.getData().containsKey("code")));
        } finally {
            cleanupByOwner(firebase, "recognitionRewardEvents", ownerUid);
            cleanupByOwner(firebase, "recognitionRewardClaims", ownerUid);
            cleanupByOwner(firebase, "pointsLedger", ownerUid);
            firebase.firestore().collection("operationalRateLimitsV1")
                    .document("user-rewardClaims-" + FirestorePaidEndpointRateLimiter.hashUid(ownerUid)).delete().get();
            firebase.firestore().collection("operationalRateLimitsV1")
                    .document("user-rewardClaims-" + FirestorePaidEndpointRateLimiter.hashUid(otherUid)).delete().get();
            deleteUserIfPresent(firebase, ownerUid);
            deleteUserIfPresent(firebase, otherUid);
            for (FirebaseApp app : FirebaseApp.getApps()) {
                if ("seewik-backend".equals(app.getName())) app.delete();
            }
        }
    }

    private LinkedSession signInWithCustomToken(
            FirebaseAdminProvider firebase,
            String apiKey,
            String uid) throws Exception {
        String customToken = firebase.auth().createCustomToken(uid);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                        "token", customToken,
                        "returnSecureToken", true))))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertStatus(200, response);
        JsonNode body = mapper.readTree(response.body());
        assertEquals(uid, body.path("localId").asText());
        return new LinkedSession(uid, body.path("idToken").asText());
    }

    private HttpResponse<String> json(String method, String url, String idToken, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + idToken)
                .header("Content-Type", "application/json");
        request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertStatus(int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(), "Unexpected deployed API status; response body intentionally omitted");
    }

    private static JsonNode coupon(JsonNode overview, String couponId) {
        for (JsonNode coupon : overview.path("coupons")) {
            if (couponId.equals(coupon.path("couponId").asText())) return coupon;
        }
        throw new AssertionError("Expected reward coupon missing");
    }

    private static void importLinkedUser(
            FirebaseAdminProvider firebase,
            String uid,
            String role,
            String runId) throws Exception {
        String email = "day13-http-" + role + "-" + runId + "@example.invalid";
        ImportUserRecord record = ImportUserRecord.builder()
                .setUid(uid)
                .setEmail(email)
                .setEmailVerified(true)
                .addUserProvider(UserProvider.builder()
                        .setProviderId("google.com")
                        .setUid("day13-http-google-" + role + "-" + runId)
                        .setEmail(email)
                        .build())
                .build();
        assertEquals(0, firebase.auth().importUsers(List.of(record)).getFailureCount());
    }

    private static void seedAward(
            FirebaseAdminProvider firebase,
            String ownerUid,
            String runId,
            String suffix,
            String reason,
            int points) throws Exception {
        String id = "day13-http-reward-" + suffix + "-" + runId;
        firebase.firestore().collection("pointsLedger").document(id).set(Map.ofEntries(
                Map.entry("ledgerEntryId", id),
                Map.entry("ownerUid", ownerUid),
                Map.entry("sourceType", "DAY13_DEPLOYED_HTTP_TEST"),
                Map.entry("sourceId", "day13-http-source-" + suffix + "-" + runId),
                Map.entry("reason", reason),
                Map.entry("awardedPoints", points),
                Map.entry("policyStatus", "AWARDED"),
                Map.entry("occurredAt", Instant.now().toString()),
                Map.entry("schemaVersion", InitiativeService.LEDGER_SCHEMA_VERSION),
                Map.entry("rewardPolicyVersion", InitiativeService.REWARD_POLICY_VERSION),
                Map.entry("demoMode", false),
                Map.entry("testFixture", true))).get();
    }

    private static List<com.google.cloud.firestore.QueryDocumentSnapshot> claims(
            FirebaseAdminProvider firebase,
            String ownerUid) throws Exception {
        return firebase.firestore().collection("recognitionRewardClaims")
                .whereEqualTo("ownerUid", ownerUid).get().get().getDocuments();
    }

    private static List<com.google.cloud.firestore.QueryDocumentSnapshot> events(
            FirebaseAdminProvider firebase,
            String ownerUid) throws Exception {
        return firebase.firestore().collection("recognitionRewardEvents")
                .whereEqualTo("ownerUid", ownerUid).get().get().getDocuments();
    }

    private static void cleanupByOwner(
            FirebaseAdminProvider firebase,
            String collection,
            String ownerUid) throws Exception {
        for (var document : firebase.firestore().collection(collection)
                .whereEqualTo("ownerUid", ownerUid).get().get().getDocuments()) {
            document.getReference().delete().get();
        }
    }

    private static void deleteUserIfPresent(FirebaseAdminProvider firebase, String uid) throws Exception {
        try {
            firebase.auth().deleteUser(uid);
        } catch (com.google.firebase.auth.FirebaseAuthException error) {
            if (error.getAuthErrorCode() == null
                    || !"USER_NOT_FOUND".equals(error.getAuthErrorCode().name())) throw error;
        }
    }

    private record LinkedSession(String uid, String idToken) {}
}
