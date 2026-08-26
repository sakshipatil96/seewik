package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FirestoreReportLifecycleGatewayTest {
    private FirebaseAdminProvider firebase;
    private Firestore firestore;
    private Transaction transaction;
    private DocumentReference reportRef;
    private DocumentReference eventRef;
    private DocumentReference dedupeRef;
    private DocumentSnapshot reportSnapshot;
    private DocumentSnapshot eventSnapshot;
    private DocumentSnapshot dedupeSnapshot;
    private FirestoreReportLifecycleGateway gateway;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        firebase = mock(FirebaseAdminProvider.class);
        firestore = mock(Firestore.class);
        transaction = mock(Transaction.class);
        CollectionReference reports = mock(CollectionReference.class);
        CollectionReference events = mock(CollectionReference.class);
        CollectionReference dedupeEvaluations = mock(CollectionReference.class);
        reportRef = mock(DocumentReference.class);
        eventRef = mock(DocumentReference.class);
        dedupeRef = mock(DocumentReference.class);
        reportSnapshot = mock(DocumentSnapshot.class);
        eventSnapshot = mock(DocumentSnapshot.class);
        dedupeSnapshot = mock(DocumentSnapshot.class);

        when(firebase.firestore()).thenReturn(firestore);
        when(firestore.collection("reports")).thenReturn(reports);
        when(reports.document("report-1")).thenReturn(reportRef);
        when(reportRef.collection("lifecycleEvents")).thenReturn(events);
        when(reportRef.collection("dedupeEvaluations")).thenReturn(dedupeEvaluations);
        when(events.document("evt_key")).thenReturn(eventRef);
        when(dedupeEvaluations.document("dedupe_key")).thenReturn(dedupeRef);
        when(transaction.get(reportRef)).thenReturn(ApiFutures.immediateFuture(reportSnapshot));
        when(transaction.get(eventRef)).thenReturn(ApiFutures.immediateFuture(eventSnapshot));
        when(transaction.get(dedupeRef)).thenReturn(ApiFutures.immediateFuture(dedupeSnapshot));
        when(reportSnapshot.exists()).thenReturn(true);
        when(dedupeSnapshot.exists()).thenReturn(false);
        when(reportSnapshot.getString("ownerUid")).thenReturn("owner-1");
        when(reportSnapshot.getData()).thenReturn(new LinkedHashMap<>(Map.of(
                "ownerUid", "owner-1", "status", "DRAFT")));
        when(firestore.runTransaction(any(Transaction.Function.class))).thenAnswer(invocation -> {
            Transaction.Function<ReportLifecycleService.TransitionResponse> function = invocation.getArgument(0);
            return ApiFutures.immediateFuture(function.updateCallback(transaction));
        });
        gateway = new FirestoreReportLifecycleGateway(firebase);
    }

    @Test
    void newTransitionUpdatesReportAndCreatesAnImmutableEventAtomically() {
        when(eventSnapshot.exists()).thenReturn(false);
        ReportLifecycleService.TransitionPlan plan = plan(false);
        var response = gateway.transact(
                "report-1", "owner-1", "evt_key", "fingerprint",
                new ReportLifecycleService.TransitionAttempt(
                        ReportLifecycleContract.ReportStatus.FILED, null, null, false),
                (ignored, dedupe) -> plan);
        assertEquals("FILED", response.toStatus());
        verify(transaction).update(reportRef, plan.reportUpdates());
        verify(transaction).create(eventRef, plan.event());
    }

    @Test
    void existingMatchingEventReturnsAnIdempotentReplayWithoutPlanningAnotherWrite() {
        when(eventSnapshot.exists()).thenReturn(true);
        when(eventSnapshot.getString("requestFingerprint")).thenReturn("fingerprint");
        when(eventSnapshot.getData()).thenReturn(eventData());
        var response = gateway.transact(
                "report-1",
                "owner-1",
                "evt_key",
                "fingerprint",
                new ReportLifecycleService.TransitionAttempt(
                        ReportLifecycleContract.ReportStatus.FILED, null, null, false),
                (ignored, dedupe) -> {
                    throw new AssertionError("Planner must not run for an idempotent replay");
                });
        assertTrue(response.idempotentReplay());
        assertEquals("evt_key", response.eventId());
    }

    private static ReportLifecycleService.TransitionPlan plan(boolean replay) {
        Map<String, Object> updates = Map.of("status", "FILED");
        Map<String, Object> event = eventData();
        return new ReportLifecycleService.TransitionPlan(
                updates,
                event,
                new ReportLifecycleService.TransitionResponse(
                        "TRANSITION_RECORDED", "evt_key", "report-1", "DRAFT", "FILED",
                        "REPORT_FILED", "CITIZEN_ATTESTATION", "report-lifecycle-v0.1", "v0.2",
                        "route-hash", "2026-08-25T12:00:00Z", replay));
    }

    private static Map<String, Object> eventData() {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "evt_key");
        event.put("reportId", "report-1");
        event.put("fromStatus", "DRAFT");
        event.put("toStatus", "FILED");
        event.put("eventType", "REPORT_FILED");
        event.put("verificationBasis", "CITIZEN_ATTESTATION");
        event.put("schemaVersion", "report-lifecycle-v0.1");
        event.put("packVersion", "v0.2");
        event.put("routeSnapshotHash", "route-hash");
        event.put("occurredAt", Date.from(Instant.parse("2026-08-25T12:00:00Z")));
        event.put("requestFingerprint", "fingerprint");
        return event;
    }
}
