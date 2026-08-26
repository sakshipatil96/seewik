package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentReference;
import com.google.firebase.FirebaseApp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LiveDedupeAdversarialIT {
    @Test
    void samePotholeFiledFourTimesWithNudgedCoordinates() throws Exception {
        FirebaseAdminProvider firebase = new FirebaseAdminProvider("seewik");
        ObjectMapper mapper = new ObjectMapper();
        ReportLifecycleService service = new ReportLifecycleService(
                new FirestoreReportLifecycleGateway(firebase, new ReportDedupeEvaluator()),
                new CivicRouterService(mapper),
                mapper,
                Clock.systemUTC());
        String runId = UUID.randomUUID().toString();
        String ownerUid = "day5-dedupe-owner-" + runId;
        List<String> reportIds = new ArrayList<>();
        List<String> eventIds = new ArrayList<>();
        List<String> dedupeIds = new ArrayList<>();
        List<String> outboxIds = new ArrayList<>();
        List<String> pointsIds = new ArrayList<>();
        double[][] points = {
            {21.370000, 74.240000},
            {21.370100, 74.240000},
            {21.370300, 74.240000},
            {21.370540, 74.240000}
        };
        List<Map<String, Object>> outcomes = new ArrayList<>();
        try {
            for (int index = 0; index < points.length; index++) {
                String reportId = "day5-dedupe-" + runId + "-" + index;
                reportIds.add(reportId);
                firebase.firestore().collection("reports").document(reportId).create(draft(ownerUid)).get();
                ReportLifecycleService.TransitionResponse response = service.transition(
                        ownerUid,
                        reportId,
                        new ReportLifecycleService.TransitionRequest(
                                "FILED", "attempt-" + runId + "-" + index, null, "EMAIL_NMC",
                                null, null, "Disposable adversarial dedupe test",
                                points[index][0], points[index][1], false));
                if (response.eventId() != null) eventIds.add(response.eventId());
                dedupeIds.add(response.eventId() == null
                        ? response.analyticsOutboxId().replaceFirst("^analytics_dedupe_", "dedupe_")
                        : response.eventId().replaceFirst("^evt_", "dedupe_"));
                outboxIds.add(response.analyticsOutboxId());
                outcomes.add(Map.of(
                        "attempt", index + 1,
                        "status", response.status(),
                        "dedupeDisposition", response.dedupeDisposition(),
                        "measuredDistanceMeters", response.measuredDistanceMeters() == null
                                ? "NONE" : response.measuredDistanceMeters(),
                        "pointsAwarded", response.pointsAwarded()));
            }

            assertEquals("TRANSITION_RECORDED", outcomes.get(0).get("status"));
            assertEquals("NO_CANDIDATE", outcomes.get(0).get("dedupeDisposition"));
            assertEquals(5, outcomes.get(0).get("pointsAwarded"));
            for (int index = 1; index < outcomes.size(); index++) {
                assertEquals("POSSIBLE_DUPLICATE", outcomes.get(index).get("status"));
                assertEquals("POSSIBLE_DUPLICATE", outcomes.get(index).get("dedupeDisposition"));
                assertEquals(0, outcomes.get(index).get("pointsAwarded"));
                assertTrue(((Number) outcomes.get(index).get("measuredDistanceMeters")).doubleValue() <= 75.0);
                assertEquals("DRAFT", firebase.firestore().collection("reports")
                        .document(reportIds.get(index)).get().get().getString("status"));
            }
            var ledger = firebase.firestore().collection("pointsLedger")
                    .whereEqualTo("ownerUid", ownerUid).get().get().getDocuments();
            assertEquals(1, ledger.size());
            pointsIds.add(ledger.getFirst().getId());
            assertEquals(5L, ledger.getFirst().getLong("awardedPoints"));
            System.out.println(mapper.writeValueAsString(Map.of(
                    "status", "PASS",
                    "thresholdMeters", 75,
                    "heuristicVersion", ReportDedupeEvaluator.HEURISTIC_VERSION,
                    "outcomes", outcomes,
                    "derivedPoints", 5)));
        } finally {
            for (String outboxId : outboxIds) delete(firebase, "analyticsOutbox/" + outboxId);
            for (String pointsId : pointsIds) delete(firebase, "pointsLedger/" + pointsId);
            for (int index = 0; index < reportIds.size(); index++) {
                if (index < dedupeIds.size()) delete(firebase,
                        "reports/" + reportIds.get(index) + "/dedupeEvaluations/" + dedupeIds.get(index));
                if (index < eventIds.size()) delete(firebase,
                        "reports/" + reportIds.get(index) + "/lifecycleEvents/" + eventIds.get(index));
                delete(firebase, "reports/" + reportIds.get(index));
            }
            for (String reportId : reportIds) {
                assertTrue(!firebase.firestore().collection("reports").document(reportId).get().get().exists());
            }
            System.out.println(mapper.writeValueAsString(Map.of(
                    "cleanup", "PASS", "reportsRemoved", reportIds.size(), "outboxesRemoved", outboxIds.size())));
            for (FirebaseApp app : FirebaseApp.getApps()) {
                if ("seewik-backend".equals(app.getName())) app.delete();
            }
        }
    }

    private static Map<String, Object> draft(String ownerUid) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("ownerUid", ownerUid);
        report.put("status", "DRAFT");
        report.put("confirmedIssueType", "POTHOLE_ROAD_DAMAGE");
        report.put("prabhagId", "PRABHAG-03");
        report.put("routeId", "NMC-PW-POTHOLE-v0.2");
        report.put("authority", "Nandurbar Municipal Council");
        report.put("draftLanguage", "MR");
        report.put("draftSubject", "Disposable dedupe test");
        report.put("draftBody", "Disposable adversarial test record; it must be removed after the live test.");
        report.put("packVersion", "v0.2");
        report.put("schemaVersion", "complaint-draft-v0.1");
        report.put("createdAt", new Date());
        report.put("updatedAt", new Date());
        return report;
    }

    private static void delete(FirebaseAdminProvider firebase, String path) throws Exception {
        DocumentReference reference = firebase.firestore().document(path);
        if (reference.get().get().exists()) reference.delete().get();
    }
}
