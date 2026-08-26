package com.seewik.api;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Component;

@Component
public class BigQueryLifecycleAnalyticsPublisher implements LifecycleAnalyticsPublisher {
    static final String DATASET = "seewik_civic";
    static final String LIFECYCLE_TABLE = "report_lifecycle_events";
    static final String DEDUPE_TABLE = "report_dedupe_evaluations";
    private final FirebaseAdminProvider firebase;
    private final BigQuery bigQuery;

    public BigQueryLifecycleAnalyticsPublisher(FirebaseAdminProvider firebase, BigQuery bigQuery) {
        this.firebase = firebase;
        this.bigQuery = bigQuery;
    }

    @Override
    public void publishPending(String outboxId) {
        if (outboxId == null || outboxId.isBlank()) return;
        DocumentReference outboxRef = firebase.firestore().collection("analyticsOutbox").document(outboxId);
        try {
            DocumentSnapshot snapshot = outboxRef.get().get();
            if (!snapshot.exists() || "SENT".equals(snapshot.getString("deliveryStatus"))) return;
            Map<String, Object> data = snapshot.getData();
            if (Boolean.TRUE.equals(data.get("demoMode"))) {
                outboxRef.update(Map.of("deliveryStatus", "SKIPPED_DEMO", "deliveredAt", new Date())).get();
                return;
            }
            String recordType = snapshot.getString("recordType");
            String table = "LIFECYCLE_EVENT".equals(recordType) ? LIFECYCLE_TABLE : DEDUPE_TABLE;
            Map<String, Object> row = "LIFECYCLE_EVENT".equals(recordType)
                    ? lifecycleRow(data)
                    : dedupeRow(data);
            String insertId = "LIFECYCLE_EVENT".equals(recordType)
                    ? String.valueOf(data.get("eventId"))
                    : String.valueOf(data.get("evaluationId"));
            InsertAllResponse response = bigQuery.insertAll(InsertAllRequest.newBuilder(DATASET, table)
                    .addRow(InsertAllRequest.RowToInsert.of(insertId, row))
                    .build());
            if (response.hasErrors()) {
                throw new IllegalStateException("BigQuery rejected analytics row: " + response.getInsertErrors());
            }
            outboxRef.update(Map.of("deliveryStatus", "SENT", "deliveredAt", new Date())).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Analytics delivery was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Analytics outbox could not be delivered", exception.getCause());
        }
    }

    static Map<String, Object> lifecycleRow(Map<String, Object> data) {
        Map<String, Object> row = new LinkedHashMap<>();
        copy(row, data, "eventId", "event_id");
        copy(row, data, "reportIdHash", "report_id_hash");
        copy(row, data, "ownerIdHash", "owner_id_hash");
        copy(row, data, "fromStatus", "from_status");
        copy(row, data, "toStatus", "to_status");
        copy(row, data, "eventType", "event_type");
        copy(row, data, "verificationBasis", "verification_basis");
        copy(row, data, "issueType", "issue_type");
        copy(row, data, "prabhagId", "prabhag_id");
        timestamp(row, data, "occurredAt", "occurred_at");
        copy(row, data, "packVersion", "pack_version");
        copy(row, data, "schemaVersion", "schema_version");
        copy(row, data, "routeSnapshotHash", "route_snapshot_hash");
        copy(row, data, "demoMode", "demo_mode");
        copy(row, data, "pointsAwarded", "points_awarded");
        copy(row, data, "dedupeDisposition", "dedupe_disposition");
        copy(row, data, "dedupeDistanceMeters", "dedupe_distance_meters");
        copy(row, data, "overdueEligible", "overdue_eligible");
        row.put("exported_at", Instant.now().toString());
        return row;
    }

    static Map<String, Object> dedupeRow(Map<String, Object> data) {
        Map<String, Object> row = new LinkedHashMap<>();
        copy(row, data, "evaluationId", "evaluation_id");
        copy(row, data, "reportIdHash", "report_id_hash");
        copy(row, data, "ownerIdHash", "owner_id_hash");
        copy(row, data, "candidateReportIdHash", "candidate_report_id_hash");
        copy(row, data, "issueType", "issue_type");
        copy(row, data, "prabhagId", "prabhag_id");
        copy(row, data, "disposition", "disposition");
        copy(row, data, "measuredDistanceMeters", "measured_distance_meters");
        copy(row, data, "thresholdMeters", "threshold_meters");
        copy(row, data, "heuristicVersion", "heuristic_version");
        timestamp(row, data, "occurredAt", "occurred_at");
        copy(row, data, "demoMode", "demo_mode");
        row.put("exported_at", Instant.now().toString());
        return row;
    }

    private static void copy(Map<String, Object> target, Map<String, Object> source, String from, String to) {
        Object value = source.get(from);
        if (value != null) target.put(to, value);
    }

    private static void timestamp(Map<String, Object> target, Map<String, Object> source, String from, String to) {
        Object value = source.get(from);
        if (value instanceof com.google.cloud.Timestamp timestamp) {
            target.put(to, timestamp.toDate().toInstant().toString());
        } else if (value instanceof Date date) {
            target.put(to, date.toInstant().toString());
        } else if (value != null) {
            target.put(to, value.toString());
        }
    }
}
