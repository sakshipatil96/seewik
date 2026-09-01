package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductionDay13Legacy40PointAuditIT {
    private static final String PROJECT_ID = "seewik";

    @Test
    void printPrivacySafeLegacyFortyPointAuditWithoutDeleting() throws Exception {
        FirebaseAdminProvider firebase = new FirebaseAdminProvider(PROJECT_ID);
        try {
            var documents = firebase.firestore().collection("pointsLedger")
                    .whereEqualTo("awardedPoints", 40).get().get().getDocuments();
            List<Map<String, Object>> records = new ArrayList<>();
            for (var document : documents) {
                Map<String, Object> data = document.getData();
                String ownerUid = string(data.get("ownerUid"));
                String sourceType = string(data.get("sourceType"));
                String sourceId = string(data.get("sourceId"));
                boolean explicitFixture = Boolean.TRUE.equals(data.get("testFixture"));
                boolean demoMode = Boolean.TRUE.equals(data.get("demoMode"));
                boolean namedTest = containsTestMarker(document.getId())
                        || containsTestMarker(sourceType)
                        || containsTestMarker(sourceId)
                        || containsTestMarker(ownerUid);
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("documentId", document.getId());
                record.put("ownerUidAuditHash", ownerUid.isBlank()
                        ? "MISSING"
                        : RecognitionService.hash("day13-legacy-40-audit-v0.1|" + ownerUid).substring(0, 16));
                record.put("sourceType", sourceType);
                record.put("sourceId", sourceId);
                record.put("reason", string(data.get("reason")));
                record.put("policyStatus", string(data.get("policyStatus")));
                record.put("schemaVersion", string(data.get("schemaVersion")));
                record.put("rewardPolicyVersion", string(data.get("rewardPolicyVersion")));
                record.put("occurredAt", String.valueOf(data.getOrDefault("occurredAt", "")));
                record.put("demoMode", demoMode);
                record.put("testFixture", explicitFixture);
                record.put("candidateTestOrDemo", explicitFixture || demoMode || namedTest);
                records.add(record);
            }
            records.sort((left, right) -> String.valueOf(left.get("documentId"))
                    .compareTo(String.valueOf(right.get("documentId"))));
            long candidates = records.stream()
                    .filter(record -> Boolean.TRUE.equals(record.get("candidateTestOrDemo")))
                    .count();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "READ_ONLY_AUDIT_COMPLETE");
            result.put("awardedFortyPointRecords", records.size());
            result.put("candidateTestOrDemoRecords", candidates);
            Map<String, Long> day13Residues = new LinkedHashMap<>();
            day13Residues.put("pointsLedger", day13OwnerPrefixCount(firebase, "pointsLedger"));
            day13Residues.put("recognitionRewardClaims", day13OwnerPrefixCount(firebase, "recognitionRewardClaims"));
            day13Residues.put("recognitionRewardEvents", day13OwnerPrefixCount(firebase, "recognitionRewardEvents"));
            long day13Users = 0;
            for (var user : firebase.auth().listUsers(null).iterateAll()) {
                if (user.getUid().startsWith("day13")) day13Users++;
            }
            day13Residues.put("firebaseUsers", day13Users);
            result.put("day13TemporaryResidues", day13Residues);
            result.put("records", records);
            System.out.println("DAY13_LEGACY_40_POINT_AUDIT=" + new ObjectMapper().writeValueAsString(result));
            assertTrue(records.stream().noneMatch(record -> record.containsKey("ownerUid")));
            assertTrue(day13Residues.values().stream().allMatch(count -> count == 0L));
        } finally {
            for (FirebaseApp app : FirebaseApp.getApps()) {
                if ("seewik-backend".equals(app.getName())) app.delete();
            }
        }
    }

    private static long day13OwnerPrefixCount(FirebaseAdminProvider firebase, String collection) throws Exception {
        return firebase.firestore().collection(collection)
                .whereGreaterThanOrEqualTo("ownerUid", "day13")
                .whereLessThan("ownerUid", "day14")
                .get().get().size();
    }

    private static boolean containsTestMarker(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.contains("test")
                || normalized.contains("demo")
                || normalized.contains("fixture")
                || normalized.contains("day10")
                || normalized.contains("day11")
                || normalized.contains("day12")
                || normalized.contains("day13");
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
