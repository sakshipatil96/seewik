package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.firebase.FirebaseApp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionLifecycleAnalyticsIT {
    private static final String PROJECT_ID = "seewik";
    private static final String FIRESTORE_BASE =
            "https://firestore.googleapis.com/v1/projects/seewik/databases/(default)/documents/";
    private static final String ANALYTICS_TABLE = "`seewik.seewik_civic.report_lifecycle_events`";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void deployedLifecycleWritesPrivacySafeBigQueryRowAndCleansUpEphemeralResources() throws Exception {
        String apiKey = System.getProperty("seewik.firebase.api-key");
        assertNotNull(apiKey, "Run with -Dseewik.firebase.api-key=<Firebase web API key>");
        String backendUrl = System.getProperty(
                "seewik.backend-url", "https://seewik-api-528138216934.asia-south1.run.app");
        FirebaseAdminProvider firebase = new FirebaseAdminProvider(PROJECT_ID);
        BigQuery bigQuery = BigQueryOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
        AnonymousSession citizen = null;
        String reportId = "day5-production-smoke-" + UUID.randomUUID();
        String eventId = null;
        String outboxId = null;
        String dedupeId = null;
        String pointsId = null;
        try {
            citizen = signInAnonymously(apiKey);
            HttpResponse<String> create = firestore(
                    "PATCH", "reports/" + reportId, citizen.idToken(), draftDocument(citizen.uid()), null);
            assertEquals(200, create.statusCode(), create.body());

            ObjectNode transitionBody = mapper.createObjectNode();
            transitionBody.put("toStatus", "FILED");
            transitionBody.put("idempotencyKey", "production-smoke-" + UUID.randomUUID());
            transitionBody.put("filingChannelId", "EMAIL_NMC");
            transitionBody.put("note", "Disposable production lifecycle analytics verification");
            HttpRequest transitionRequest = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/api/reports/" + reportId + "/transitions"))
                    .header("Authorization", "Bearer " + citizen.idToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(transitionBody)))
                    .build();
            HttpResponse<String> transition = http.send(transitionRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, transition.statusCode(), transition.body());
            JsonNode response = mapper.readTree(transition.body());
            assertEquals("TRANSITION_RECORDED", response.path("status").asText());
            assertEquals("FILED", response.path("toStatus").asText());
            assertEquals(5, response.path("pointsAwarded").asInt());
            assertEquals("DEDUPE_NOT_EVALUATED", response.path("dedupeDisposition").asText());
            eventId = response.path("eventId").asText();
            outboxId = response.path("analyticsOutboxId").asText();
            dedupeId = eventId.replaceFirst("^evt_", "dedupe_");

            var outbox = firebase.firestore().collection("analyticsOutbox").document(outboxId).get().get();
            assertEquals("SENT", outbox.getString("deliveryStatus"));
            var points = firebase.firestore().collection("pointsLedger")
                    .whereEqualTo("reportId", reportId).get().get().getDocuments();
            assertEquals(1, points.size());
            pointsId = points.getFirst().getId();

            QueryJobConfiguration verify = QueryJobConfiguration.newBuilder(
                            "SELECT event_id, report_id_hash, owner_id_hash, event_type, points_awarded, demo_mode "
                                    + "FROM " + ANALYTICS_TABLE + " WHERE event_id = @eventId")
                    .setUseLegacySql(false)
                    .addNamedParameter("eventId", QueryParameterValue.string(eventId))
                    .build();
            var rows = bigQuery.query(verify).iterateAll().iterator();
            assertFalse(!rows.hasNext(), "Production analytics row was not found");
            var row = rows.next();
            assertEquals("REPORT_FILED", row.get("event_type").getStringValue());
            assertEquals(5, row.get("points_awarded").getLongValue());
            assertFalse(row.get("demo_mode").getBooleanValue());
            assertEquals(64, row.get("report_id_hash").getStringValue().length());
            assertEquals(64, row.get("owner_id_hash").getStringValue().length());

            System.out.println(mapper.writeValueAsString(Map.of(
                    "status", "PASS",
                    "backendUrl", backendUrl,
                    "eventId", eventId,
                    "firestoreOutboxStatus", "SENT",
                    "bigQueryRowFound", true,
                    "pointsAwarded", 5,
                    "rawIdentifiersExported", false)));
        } finally {
            if (outboxId != null && !outboxId.isBlank()) delete(firebase, "analyticsOutbox/" + outboxId);
            if (pointsId != null) delete(firebase, "pointsLedger/" + pointsId);
            if (dedupeId != null) delete(firebase, "reports/" + reportId + "/dedupeEvaluations/" + dedupeId);
            if (eventId != null) delete(firebase, "reports/" + reportId + "/lifecycleEvents/" + eventId);
            delete(firebase, "reports/" + reportId);
            if (citizen != null) firebase.auth().deleteUser(citizen.uid());
            String bigQueryCleanup = removeBigQueryEvidenceRow(bigQuery, eventId);
            System.out.println(mapper.writeValueAsString(Map.of(
                    "cleanup", "PASS", "firestoreRecordsRemoved", true,
                    "anonymousUserDeleted", citizen != null, "bigQueryCleanup", bigQueryCleanup)));
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
            String method, String documentPath, String idToken, JsonNode body, String query) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(FIRESTORE_BASE + documentPath + (query == null ? "" : "?" + query)))
                .header("Authorization", "Bearer " + idToken)
                .header("Content-Type", "application/json");
        request.method(method, body == null
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
        fields.set("draftLanguage", stringValue("EN"));
        fields.set("draftSubject", stringValue("Disposable production lifecycle test"));
        fields.set("draftBody", stringValue("Disposable production lifecycle verification record; remove after the test."));
        fields.set("packVersion", stringValue("v0.2"));
        fields.set("schemaVersion", stringValue("complaint-draft-v0.1"));
        fields.set("createdAt", timestampValue(Instant.now()));
        fields.set("updatedAt", timestampValue(Instant.now()));
        return mapper.createObjectNode().set("fields", fields);
    }

    private ObjectNode stringValue(String value) {
        return mapper.createObjectNode().put("stringValue", value);
    }

    private ObjectNode timestampValue(Instant value) {
        return mapper.createObjectNode().put("timestampValue", value.toString());
    }

    private static void delete(FirebaseAdminProvider firebase, String path) throws Exception {
        var reference = firebase.firestore().document(path);
        if (reference.get().get().exists()) reference.delete().get();
    }

    private String removeBigQueryEvidenceRow(BigQuery bigQuery, String eventId) throws Exception {
        if (eventId == null || eventId.isBlank()) return "NOT_CREATED";
        QueryJobConfiguration delete = QueryJobConfiguration.newBuilder(
                        "DELETE FROM " + ANALYTICS_TABLE + " WHERE event_id = @eventId")
                .setUseLegacySql(false)
                .addNamedParameter("eventId", QueryParameterValue.string(eventId))
                .build();
        try {
            bigQuery.query(delete);
        } catch (BigQueryException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("streaming buffer")) {
                return "DEFERRED_STREAMING_BUFFER:" + eventId;
            }
            throw exception;
        }
        long remaining = bigQuery.query(QueryJobConfiguration.newBuilder(
                        "SELECT COUNT(*) AS row_count FROM " + ANALYTICS_TABLE + " WHERE event_id = @eventId")
                .setUseLegacySql(false)
                .addNamedParameter("eventId", QueryParameterValue.string(eventId))
                .build()).iterateAll().iterator().next().get("row_count").getLongValue();
        assertEquals(0, remaining);
        return "REMOVED";
    }

    private record AnonymousSession(String uid, String idToken) {}
}
