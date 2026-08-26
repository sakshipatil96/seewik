package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.firebase.FirebaseApp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LiveReportLifecycleIntegrationIT {
    private static final String PROJECT_ID = "seewik";
    private static final String FIRESTORE_BASE =
            "https://firestore.googleapis.com/v1/projects/seewik/databases/(default)/documents/";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void liveAnonymousOwnershipAndServerTransition() throws Exception {
        String apiKey = System.getProperty("seewik.firebase.api-key");
        assertNotNull(apiKey, "Run with -Dseewik.firebase.api-key=<Firebase web API key>");
        FirebaseAdminProvider firebase = new FirebaseAdminProvider(PROJECT_ID);
        AnonymousSession owner = null;
        AnonymousSession other = null;
        String reportId = "day5-live-" + UUID.randomUUID();
        String eventId = null;
        String dedupeId = null;
        String pointsEntryId = null;
        String outboxId = null;
        try {
            owner = signInAnonymously(apiKey);
            other = signInAnonymously(apiKey);
            HttpResponse<String> create = firestore(
                    "PATCH", "reports/" + reportId, owner.idToken(), draftDocument(owner.uid()), null);
            assertEquals(200, create.statusCode(), create.body());

            assertEquals(200, firestore("GET", "reports/" + reportId, owner.idToken(), null, null).statusCode());
            assertEquals(403, firestore("GET", "reports/" + reportId, other.idToken(), null, null).statusCode());
            assertEquals(
                    403,
                    firestore(
                                    "PATCH",
                                    "reports/" + reportId + "/lifecycleEvents/client-attempt",
                                    owner.idToken(),
                                    minimalEventDocument(owner.uid()),
                                    null)
                            .statusCode());

            CitizenIdentityVerifier.AuthenticatedCitizen citizen =
                    new FirebaseCitizenIdentityVerifier(firebase).verifyBearer("Bearer " + owner.idToken());
            assertEquals(owner.uid(), citizen.uid());
            ReportLifecycleService service = new ReportLifecycleService(
                    new FirestoreReportLifecycleGateway(firebase, new ReportDedupeEvaluator()),
                    new CivicRouterService(mapper),
                    mapper,
                    Clock.systemUTC());
            ReportLifecycleService.TransitionResponse transition = service.transition(
                    citizen.uid(),
                    reportId,
                    new ReportLifecycleService.TransitionRequest(
                            "FILED",
                            "live-file-" + UUID.randomUUID(),
                            null,
                            "EMAIL_NMC",
                            null,
                            null,
                            "Disposable Day 5 ownership test"));
            eventId = transition.eventId();
            dedupeId = eventId.replaceFirst("^evt_", "dedupe_");
            outboxId = transition.analyticsOutboxId();
            var pointsDocuments = firebase.firestore().collection("pointsLedger")
                    .whereEqualTo("reportId", reportId).get().get().getDocuments();
            assertEquals(1, pointsDocuments.size());
            pointsEntryId = pointsDocuments.getFirst().getId();
            assertEquals("FILED", transition.toStatus());
            assertEquals("CITIZEN_ATTESTATION", transition.verificationBasis());
            assertFalse(transition.idempotentReplay());
            assertNotNull(transition.routeSnapshotHash());
            assertEquals(5, transition.pointsAwarded());
            assertEquals("DEDUPE_NOT_EVALUATED", transition.dedupeDisposition());

            HttpResponse<String> ownerReport =
                    firestore("GET", "reports/" + reportId, owner.idToken(), null, null);
            assertEquals(200, ownerReport.statusCode(), ownerReport.body());
            assertEquals("FILED", mapper.readTree(ownerReport.body())
                    .path("fields").path("status").path("stringValue").asText());
            String eventPath = "reports/" + reportId + "/lifecycleEvents/" + eventId;
            assertEquals(200, firestore("GET", eventPath, owner.idToken(), null, null).statusCode());
            assertEquals(403, firestore("GET", eventPath, other.idToken(), null, null).statusCode());
            assertEquals(
                    403,
                    firestore("PATCH", eventPath, owner.idToken(), minimalEventDocument(owner.uid()), null)
                            .statusCode());
            String dedupePath = "reports/" + reportId + "/dedupeEvaluations/" + dedupeId;
            assertEquals(200, firestore("GET", dedupePath, owner.idToken(), null, null).statusCode());
            assertEquals(403, firestore("GET", dedupePath, other.idToken(), null, null).statusCode());
            assertEquals(403, firestore("PATCH", dedupePath, owner.idToken(), minimalEventDocument(owner.uid()), null)
                    .statusCode());
            String pointsPath = "pointsLedger/" + pointsEntryId;
            assertEquals(200, firestore("GET", pointsPath, owner.idToken(), null, null).statusCode());
            assertEquals(403, firestore("GET", pointsPath, other.idToken(), null, null).statusCode());
            assertEquals(403, firestore("PATCH", pointsPath, owner.idToken(), minimalEventDocument(owner.uid()), null)
                    .statusCode());
            assertEquals(403, firestore("GET", "analyticsOutbox/" + outboxId, owner.idToken(), null, null).statusCode());
            assertEquals(
                    403,
                    firestore(
                                    "PATCH",
                                    "reports/" + reportId,
                                    owner.idToken(),
                                    statusOnlyDocument("REOPENED"),
                                    "updateMask.fieldPaths=status")
                            .statusCode());
            assertEquals(
                    403,
                    firestore("DELETE", "reports/" + reportId, owner.idToken(), null, null).statusCode());

            System.out.println(mapper.writeValueAsString(java.util.Map.ofEntries(
                    java.util.Map.entry("status", "PASS"),
                    java.util.Map.entry("reportId", reportId),
                    java.util.Map.entry("eventId", eventId),
                    java.util.Map.entry("ownerCreateRead", true),
                    java.util.Map.entry("crossOwnerReportReadDenied", true),
                    java.util.Map.entry("clientEventWriteDenied", true),
                    java.util.Map.entry("serverFiledTransition", true),
                    java.util.Map.entry("ownerEventRead", true),
                    java.util.Map.entry("crossOwnerEventReadDenied", true),
                    java.util.Map.entry("clientStatusMutationDenied", true),
                    java.util.Map.entry("filedReportDeleteDenied", true),
                    java.util.Map.entry("routeSnapshotFrozen", true),
                    java.util.Map.entry("ownerDedupeRead", true),
                    java.util.Map.entry("clientDedupeWriteDenied", true),
                    java.util.Map.entry("ownerPointsRead", true),
                    java.util.Map.entry("crossOwnerPointsReadDenied", true),
                    java.util.Map.entry("clientPointsWriteDenied", true),
                    java.util.Map.entry("analyticsOutboxClientReadDenied", true),
                    java.util.Map.entry("filedPoints", transition.pointsAwarded()))));
        } finally {
            if (outboxId != null) firebase.firestore().document("analyticsOutbox/" + outboxId).delete().get();
            if (pointsEntryId != null) firebase.firestore().document("pointsLedger/" + pointsEntryId).delete().get();
            if (dedupeId != null) firebase.firestore()
                    .document("reports/" + reportId + "/dedupeEvaluations/" + dedupeId).delete().get();
            if (eventId != null) {
                firebase.firestore()
                        .document("reports/" + reportId + "/lifecycleEvents/" + eventId)
                        .delete().get();
            }
            firebase.firestore().document("reports/" + reportId).delete().get();
            if (owner != null) firebase.auth().deleteUser(owner.uid());
            if (other != null) firebase.auth().deleteUser(other.uid());
            boolean reportRemoved = !firebase.firestore().document("reports/" + reportId).get().get().exists();
            boolean eventRemoved = eventId == null || !firebase.firestore()
                    .document("reports/" + reportId + "/lifecycleEvents/" + eventId)
                    .get().get().exists();
            assertTrue(reportRemoved);
            assertTrue(eventRemoved);
            System.out.println(mapper.writeValueAsString(java.util.Map.of(
                    "cleanup", "PASS",
                    "reportRemoved", reportRemoved,
                    "eventRemoved", eventRemoved,
                    "anonymousUsersDeleted", owner != null && other != null)));
            for (FirebaseApp app : FirebaseApp.getApps()) {
                if ("seewik-backend".equals(app.getName())) app.delete();
            }
        }
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

    private HttpResponse<String> firestore(
            String method,
            String documentPath,
            String idToken,
            JsonNode body,
            String query) throws Exception {
        String url = FIRESTORE_BASE + documentPath + (query == null ? "" : "?" + query);
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + idToken)
                .header("Content-Type", "application/json");
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private ObjectNode draftDocument(String ownerUid) {
        ObjectNode fields = mapper.createObjectNode();
        fields.set("ownerUid", stringValue(ownerUid));
        fields.set("status", stringValue("DRAFT"));
        fields.set("confirmedIssueType", stringValue("POTHOLE_ROAD_DAMAGE"));
        fields.set("prabhagId", stringValue("PRABHAG-03"));
        fields.set("routeId", stringValue("NMC-PW-POTHOLE-v0.2"));
        fields.set("authority", stringValue("Nandurbar Municipal Council"));
        fields.set("draftLanguage", stringValue("MR"));
        fields.set("draftSubject", stringValue("रस्त्यावरील खड्ड्याबाबत तक्रार"));
        fields.set("draftBody", stringValue("आमच्या परिसरातील रस्त्यावर मोठा खड्डा आहे. कृपया आवश्यक कार्यवाही करावी."));
        fields.set("packVersion", stringValue("v0.2"));
        fields.set("schemaVersion", stringValue("complaint-draft-v0.1"));
        fields.set("createdAt", timestampValue(Instant.now()));
        fields.set("updatedAt", timestampValue(Instant.now()));
        return mapper.createObjectNode().set("fields", fields);
    }

    private ObjectNode minimalEventDocument(String ownerUid) {
        ObjectNode fields = mapper.createObjectNode();
        fields.set("ownerUid", stringValue(ownerUid));
        fields.set("eventType", stringValue("CLIENT_FORGED_EVENT"));
        return mapper.createObjectNode().set("fields", fields);
    }

    private ObjectNode statusOnlyDocument(String status) {
        ObjectNode fields = mapper.createObjectNode();
        fields.set("status", stringValue(status));
        return mapper.createObjectNode().set("fields", fields);
    }

    private ObjectNode stringValue(String value) {
        return mapper.createObjectNode().put("stringValue", value);
    }

    private ObjectNode timestampValue(Instant value) {
        return mapper.createObjectNode().put("timestampValue", value.toString());
    }

    private record AnonymousSession(String uid, String idToken) {}
}
