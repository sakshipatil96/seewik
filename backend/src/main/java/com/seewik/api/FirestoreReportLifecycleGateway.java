package com.seewik.api;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FirestoreReportLifecycleGateway implements ReportLifecycleGateway {
    private final FirebaseAdminProvider firebase;
    private final ReportDedupeEvaluator dedupeEvaluator;

    @Autowired
    public FirestoreReportLifecycleGateway(
            FirebaseAdminProvider firebase,
            ReportDedupeEvaluator dedupeEvaluator) {
        this.firebase = firebase;
        this.dedupeEvaluator = dedupeEvaluator;
    }

    FirestoreReportLifecycleGateway(FirebaseAdminProvider firebase) {
        this(firebase, new ReportDedupeEvaluator());
    }

    @Override
    public ReportLifecycleService.TransitionResponse transact(
            String reportId,
            String ownerUid,
            String eventId,
            String requestFingerprint,
            ReportLifecycleService.TransitionAttempt attempt,
            BiFunction<Map<String, Object>, ReportDedupeEvaluator.DedupeResult,
                    ReportLifecycleService.TransitionPlan> planner) {
        Firestore firestore = firebase.firestore();
        DocumentReference reportRef = firestore.collection("reports").document(reportId);
        DocumentReference eventRef = reportRef.collection("lifecycleEvents").document(eventId);
        String dedupeId = eventId.replaceFirst("^evt_", "dedupe_");
        DocumentReference dedupeRef = reportRef.collection("dedupeEvaluations").document(dedupeId);
        try {
            return firestore.runTransaction(transaction -> {
                DocumentSnapshot report = transaction.get(reportRef).get();
                DocumentSnapshot existingEvent = transaction.get(eventRef).get();
                DocumentSnapshot existingDedupe = transaction.get(dedupeRef).get();
                if (!report.exists()) {
                    throw new ReportLifecycleService.LifecycleException("REPORT_NOT_FOUND", "Report was not found");
                }
                if (!ownerUid.equals(report.getString("ownerUid"))) {
                    throw new ReportLifecycleService.LifecycleException(
                            "REPORT_FORBIDDEN", "The authenticated user does not own this report");
                }
                if (existingEvent.exists()) {
                    if (!requestFingerprint.equals(existingEvent.getString("requestFingerprint"))) {
                        throw new ReportLifecycleService.LifecycleException(
                                "IDEMPOTENCY_KEY_REUSED", "The idempotency key was already used for another request");
                    }
                    return responseFromEvent(existingEvent.getData(), true);
                }
                if (existingDedupe.exists()) {
                    if (!requestFingerprint.equals(existingDedupe.getString("requestFingerprint"))) {
                        throw new ReportLifecycleService.LifecycleException(
                                "IDEMPOTENCY_KEY_REUSED", "The idempotency key was already used for another request");
                    }
                    if ("POSSIBLE_DUPLICATE".equals(existingDedupe.getString("disposition"))) {
                        return responseFromDedupe(reportId, report.getData(), existingDedupe.getData(), true);
                    }
                    throw new ReportLifecycleService.LifecycleException(
                            "LIFECYCLE_STORE_FAILED", "A dedupe evaluation exists without its lifecycle event");
                }

                List<Map<String, Object>> candidates = List.of();
                if (attempt.toStatus() == ReportLifecycleContract.ReportStatus.FILED
                        && attempt.latitude() != null) {
                    QuerySnapshot candidateQuery = transaction.get(firestore.collection("reports")
                                    .whereEqualTo("prabhagId", report.getString("prabhagId")))
                            .get();
                    candidates = new ArrayList<>();
                    for (QueryDocumentSnapshot candidate : candidateQuery.getDocuments()) {
                        Map<String, Object> data = new LinkedHashMap<>(candidate.getData());
                        data.put("_documentId", candidate.getId());
                        candidates.add(data);
                    }
                }
                ReportDedupeEvaluator.DedupeResult dedupe = dedupeEvaluator.evaluate(
                        reportId,
                        report.getData(),
                        candidates,
                        attempt.latitude(),
                        attempt.longitude(),
                        attempt.dedupeOverride());
                ReportLifecycleService.TransitionPlan plan = planner.apply(report.getData(), dedupe);
                if (plan.event() != null) {
                    transaction.update(reportRef, plan.reportUpdates());
                    transaction.create(eventRef, plan.event());
                }
                if (plan.dedupeEvaluation() != null) {
                    transaction.create(dedupeRef, plan.dedupeEvaluation());
                }
                if (plan.pointsLedgerEntry() != null) {
                    String entryId = String.valueOf(plan.pointsLedgerEntry().get("ledgerEntryId"));
                    transaction.create(firestore.collection("pointsLedger").document(entryId), plan.pointsLedgerEntry());
                }
                if (plan.analyticsOutbox() != null) {
                    String outboxId = String.valueOf(plan.analyticsOutbox().get("outboxId"));
                    transaction.create(firestore.collection("analyticsOutbox").document(outboxId), plan.analyticsOutbox());
                }
                return plan.response();
            }).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ReportLifecycleService.LifecycleException(
                    "LIFECYCLE_STORE_INTERRUPTED", "The lifecycle transaction was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = unwrap(exception);
            if (cause instanceof ReportLifecycleService.LifecycleException lifecycleException) {
                throw lifecycleException;
            }
            throw new ReportLifecycleService.LifecycleException(
                    "LIFECYCLE_STORE_FAILED", "The lifecycle transaction could not be completed", cause);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof ExecutionException || current instanceof java.util.concurrent.CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static ReportLifecycleService.TransitionResponse responseFromEvent(
            Map<String, Object> event, boolean idempotentReplay) {
        return new ReportLifecycleService.TransitionResponse(
                "TRANSITION_RECORDED",
                string(event, "eventId"),
                string(event, "reportId"),
                string(event, "fromStatus"),
                string(event, "toStatus"),
                string(event, "eventType"),
                string(event, "verificationBasis"),
                string(event, "schemaVersion"),
                string(event, "packVersion"),
                string(event, "routeSnapshotHash"),
                instant(event.get("occurredAt")),
                idempotentReplay,
                string(event, "dedupeDisposition"),
                number(event.get("dedupeMeasuredDistanceMeters")),
                integer(event.get("pointsAwarded")),
                integer(event.get("pointsAwarded")) == 0 ? 0.0 : 1.0,
                "analytics_" + string(event, "eventId"));
    }

    private static ReportLifecycleService.TransitionResponse responseFromDedupe(
            String reportId,
            Map<String, Object> report,
            Map<String, Object> evaluation,
            boolean idempotentReplay) {
        return new ReportLifecycleService.TransitionResponse(
                "POSSIBLE_DUPLICATE",
                null,
                reportId,
                string(report, "status"),
                string(report, "status"),
                null,
                ReportLifecycleContract.VerificationBasis.NONE.name(),
                ReportLifecycleContract.SCHEMA_VERSION,
                string(report, "packVersion"),
                null,
                instant(evaluation.get("occurredAt")),
                idempotentReplay,
                string(evaluation, "disposition"),
                number(evaluation.get("measuredDistanceMeters")),
                0,
                0.0,
                "analytics_" + string(evaluation, "evaluationId"));
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private static Double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toDate().toInstant().toString();
        if (value instanceof Date date) return date.toInstant().toString();
        if (value instanceof Instant instant) return instant.toString();
        return value == null ? null : value.toString();
    }
}
