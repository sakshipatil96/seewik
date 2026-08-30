package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionDay11AttendanceIT {
    private static final String PROJECT_ID = "seewik";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void linkedAccountsCoverAttendanceRewardsWindowsAndCleanup() throws Exception {
        String apiKey = System.getProperty("seewik.firebase.api-key");
        assertNotNull(apiKey, "Firebase web API key is required");
        String backendUrl = System.getProperty(
                "seewik.backend-url", "https://seewik-api-528138216934.asia-south1.run.app");
        FirebaseAdminProvider firebase = new FirebaseAdminProvider(PROJECT_ID);
        String runId = UUID.randomUUID().toString().replace("-", "");
        List<TestAccount> accounts = new ArrayList<>();
        List<String> importedUserIds = new ArrayList<>();
        List<String> initiativeIds = new ArrayList<>();
        List<String> attemptIds = new ArrayList<>();
        try {
            accounts = createLinkedAccounts(firebase, apiKey, runId, 4, importedUserIds);
            TestAccount organiser = accounts.get(0);
            TestAccount first = accounts.get(1);
            TestAccount second = accounts.get(2);
            TestAccount rateLimited = accounts.get(3);

            String completionFirst = create(backendUrl, organiser.idToken(), "Day 11 completion-first " + runId);
            initiativeIds.add(completionFirst);
            startNow(firebase, completionFirst);
            join(backendUrl, first.idToken(), completionFirst);
            join(backendUrl, second.idToken(), completionFirst);
            join(backendUrl, rateLimited.idToken(), completionFirst);

            String code = attendanceCode(backendUrl, organiser.idToken(), completionFirst);
            assertEquals(403, get(
                    backendUrl + "/api/initiatives/" + completionFirst + "/attendance/code",
                    first.idToken()).statusCode());

            String incorrect = code.equals("000000") ? "000001" : "000000";
            long attemptSlot = Math.floorDiv(Instant.now().getEpochSecond(), AttendanceCodeService.SLOT_SECONDS);
            attemptIds.add("attempt_" + InitiativeService.hash(
                    completionFirst + ":" + rateLimited.uid() + ":" + attemptSlot));
            for (int attempt = 1; attempt <= 5; attempt++) {
                assertEquals(400, submitCode(
                        backendUrl, rateLimited.idToken(), completionFirst, incorrect).statusCode());
            }
            assertEquals(429, submitCode(
                    backendUrl, rateLimited.idToken(), completionFirst, incorrect).statusCode());

            JsonNode firstAttendance = body(submitCode(
                    backendUrl, first.idToken(), completionFirst, code), 200);
            assertEquals(20, firstAttendance.path("participantPointsAwarded").asInt());
            assertEquals(0, firstAttendance.path("organiserPointsAwarded").asInt());
            assertEquals(409, post(
                    backendUrl + "/api/initiatives/" + completionFirst + "/cancel",
                    organiser.idToken(), Map.of("reason", "Must be blocked")).statusCode());

            JsonNode completion = body(post(
                    backendUrl + "/api/initiatives/" + completionFirst + "/complete",
                    organiser.idToken(), null), 200);
            assertEquals(0, completion.path("pointsAwarded").asInt());

            JsonNode secondAttendance = body(submitCode(
                    backendUrl, second.idToken(), completionFirst, code), 200);
            assertEquals(20, secondAttendance.path("participantPointsAwarded").asInt());
            assertEquals(40, secondAttendance.path("organiserPointsAwarded").asInt());
            JsonNode replay = body(submitCode(
                    backendUrl, first.idToken(), completionFirst, code), 200);
            assertTrue(replay.path("idempotentReplay").asBoolean());
            assertEquals(0, replay.path("participantPointsAwarded").asInt());

            JsonNode participantMine = body(get(
                    backendUrl + "/api/initiatives/mine", first.idToken()), 200);
            JsonNode completionFirstView = findInitiative(participantMine, completionFirst);
            assertEquals(3, completionFirstView.path("joinerCount").asInt());
            assertEquals(2, completionFirstView.path("codeAttendanceCount").asInt());
            assertEquals(0, completionFirstView.path("selfAttendanceCount").asInt());
            assertFalse(completionFirstView.path("canSelfAttend").asBoolean());
            assertAwardedLedger(firebase, completionFirst, 2, 1);

            String attendanceFirst = create(backendUrl, organiser.idToken(), "Day 11 attendance-first " + runId);
            initiativeIds.add(attendanceFirst);
            join(backendUrl, first.idToken(), attendanceFirst);
            join(backendUrl, second.idToken(), attendanceFirst);
            startNow(firebase, attendanceFirst);
            String secondCode = attendanceCode(backendUrl, organiser.idToken(), attendanceFirst);
            assertEquals(0, body(submitCode(
                    backendUrl, first.idToken(), attendanceFirst, secondCode), 200)
                    .path("organiserPointsAwarded").asInt());
            assertEquals(0, body(submitCode(
                    backendUrl, second.idToken(), attendanceFirst, secondCode), 200)
                    .path("organiserPointsAwarded").asInt());
            JsonNode thresholdCompletion = body(post(
                    backendUrl + "/api/initiatives/" + attendanceFirst + "/complete",
                    organiser.idToken(), null), 200);
            assertEquals(40, thresholdCompletion.path("pointsAwarded").asInt());
            assertAwardedLedger(firebase, attendanceFirst, 2, 1);

            String selfFallback = create(backendUrl, organiser.idToken(), "Day 11 self fallback " + runId);
            initiativeIds.add(selfFallback);
            join(backendUrl, first.idToken(), selfFallback);
            join(backendUrl, second.idToken(), selfFallback);
            firebase.firestore().collection("initiatives").document(selfFallback)
                    .update("startAt", Instant.now().minus(4, ChronoUnit.HOURS).toString()).get();
            body(post(
                    backendUrl + "/api/initiatives/" + selfFallback + "/complete",
                    organiser.idToken(), null), 200);
            JsonNode self = body(post(
                    backendUrl + "/api/initiatives/" + selfFallback + "/attendance/self",
                    first.idToken(), null), 200);
            assertEquals("SELF_ATTESTED", self.path("attendanceBasis").asText());
            assertEquals(0, self.path("participantPointsAwarded").asInt());
            assertTrue(body(post(
                    backendUrl + "/api/initiatives/" + selfFallback + "/attendance/self",
                    first.idToken(), null), 200).path("idempotentReplay").asBoolean());
            assertEquals(409, get(
                    backendUrl + "/api/initiatives/" + selfFallback + "/attendance/code",
                    organiser.idToken()).statusCode());
            firebase.firestore().collection("initiatives").document(selfFallback)
                    .update("completedAt", Instant.now().minus(8, ChronoUnit.DAYS).toString()).get();
            assertEquals(409, post(
                    backendUrl + "/api/initiatives/" + selfFallback + "/attendance/self",
                    second.idToken(), null).statusCode());

            assertAttemptStoresNoCode(firebase, attemptIds.getFirst());
        } finally {
            for (String attemptId : attemptIds) {
                firebase.firestore().collection("initiativeAttendanceAttempts")
                        .document(attemptId).delete().get();
            }
            for (String initiativeId : initiativeIds) cleanupInitiative(firebase, initiativeId);
            for (String uid : importedUserIds) firebase.auth().deleteUser(uid);
            for (FirebaseApp app : FirebaseApp.getApps()) {
                if ("seewik-backend".equals(app.getName())) app.delete();
            }
        }
    }

    private List<TestAccount> createLinkedAccounts(
            FirebaseAdminProvider firebase,
            String apiKey,
            String runId,
            int count,
            List<String> importedUserIds) throws Exception {
        List<ImportUserRecord> records = new ArrayList<>();
        List<AccountSeed> seeds = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String uid = "day11" + index + runId.substring(0, 20);
            String email = "day11-" + index + "-" + runId + "@example.invalid";
            seeds.add(new AccountSeed(uid, email));
            records.add(ImportUserRecord.builder()
                    .setUid(uid)
                    .setEmail(email)
                    .setEmailVerified(true)
                    .addUserProvider(UserProvider.builder()
                            .setProviderId("google.com")
                            .setUid("day11-google-" + index + "-" + runId)
                            .setEmail(email)
                            .build())
                    .build());
        }
        assertEquals(0, firebase.auth().importUsers(records).getFailureCount());
        importedUserIds.addAll(seeds.stream().map(AccountSeed::uid).toList());
        List<TestAccount> accounts = new ArrayList<>();
        for (AccountSeed seed : seeds) {
            String uid = firebase.auth().getUserByEmail(seed.email()).getUid();
            assertEquals(seed.uid(), uid);
            String customToken = firebase.auth().createCustomToken(uid);
            HttpResponse<String> response = postWithoutAuth(
                    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=" + apiKey,
                    Map.of("token", customToken, "returnSecureToken", true));
            JsonNode signedIn = body(response, 200);
            String idToken = signedIn.path("idToken").asText();
            Object identities = ((Map<?, ?>) firebase.auth().verifyIdToken(idToken)
                    .getClaims().get("firebase")).get("identities");
            assertTrue(identities instanceof Map<?, ?> map && map.containsKey("google.com"));
            accounts.add(new TestAccount(uid, idToken));
        }
        return accounts;
    }

    private String create(String backendUrl, String idToken, String title) throws Exception {
        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("title", title);
        activity.put("category", "CLEANUP");
        activity.put("description", "Temporary Day 11 production verification; removed after testing.");
        activity.put("startAt", Instant.now().plus(1, ChronoUnit.HOURS).toString());
        activity.put("placeName", "Day 11 production verification fixture");
        activity.put("latitude", 21.3700);
        activity.put("longitude", 74.2400);
        activity.put("needs", "None");
        return body(post(backendUrl + "/api/initiatives", idToken, activity), 201)
                .path("initiativeId").asText();
    }

    private void startNow(FirebaseAdminProvider firebase, String initiativeId) throws Exception {
        firebase.firestore().collection("initiatives").document(initiativeId)
                .update("startAt", Instant.now().minus(10, ChronoUnit.MINUTES).toString()).get();
    }

    private void join(String backendUrl, String idToken, String initiativeId) throws Exception {
        JsonNode joined = body(post(
                backendUrl + "/api/initiatives/" + initiativeId + "/join", idToken, null), 200);
        assertTrue("JOINED".equals(joined.path("status").asText())
                || "ALREADY_JOINED".equals(joined.path("status").asText()));
    }

    private String attendanceCode(String backendUrl, String idToken, String initiativeId) throws Exception {
        String code = body(get(
                backendUrl + "/api/initiatives/" + initiativeId + "/attendance/code", idToken), 200)
                .path("code").asText();
        assertTrue(code.matches("^[0-9]{6}$"));
        return code;
    }

    private HttpResponse<String> submitCode(
            String backendUrl, String idToken, String initiativeId, String code) throws Exception {
        return post(
                backendUrl + "/api/initiatives/" + initiativeId + "/attendance/code",
                idToken,
                Map.of("code", code));
    }

    private void assertAwardedLedger(
            FirebaseAdminProvider firebase, String initiativeId, int participantAwards, int organiserAwards)
            throws Exception {
        var entries = firebase.firestore().collection("pointsLedger")
                .whereEqualTo("sourceId", initiativeId).get().get().getDocuments();
        assertEquals(participantAwards, entries.stream()
                .filter(entry -> Long.valueOf(20).equals(entry.getLong("awardedPoints"))).count());
        assertEquals(organiserAwards, entries.stream()
                .filter(entry -> Long.valueOf(40).equals(entry.getLong("awardedPoints"))).count());
        assertTrue(entries.stream().allMatch(entry -> "points-ledger-v0.3".equals(entry.getString("schemaVersion"))));
    }

    private void assertAttemptStoresNoCode(FirebaseAdminProvider firebase, String attemptId) throws Exception {
        var attempt = firebase.firestore().collection("initiativeAttendanceAttempts")
                .document(attemptId).get().get();
        assertTrue(attempt.exists());
        assertEquals(5L, attempt.getLong("failedAttempts"));
        assertFalse(attempt.getData().containsKey("code"));
        assertFalse(attempt.getData().containsKey("submittedCode"));
    }

    private HttpResponse<String> get(String url, String idToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + idToken)
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String url, String idToken, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + idToken)
                .header("Content-Type", "application/json");
        String json = body == null ? "" : mapper.writeValueAsString(body);
        request.POST(body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithoutAuth(String url, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private static JsonNode findInitiative(JsonNode response, String initiativeId) {
        for (JsonNode initiative : response.path("initiatives")) {
            if (initiativeId.equals(initiative.path("initiativeId").asText())) return initiative;
        }
        throw new AssertionError("Activity missing from response");
    }

    private static void cleanupInitiative(FirebaseAdminProvider firebase, String initiativeId) throws Exception {
        var initiative = firebase.firestore().collection("initiatives").document(initiativeId);
        for (var event : initiative.collection("events").get().get().getDocuments()) {
            event.getReference().delete().get();
        }
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

    private record TestAccount(String uid, String idToken) {}

    private record AccountSeed(String uid, String email) {}
}
