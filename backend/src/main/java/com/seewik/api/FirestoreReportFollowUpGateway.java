package com.seewik.api;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Component;

@Component
public class FirestoreReportFollowUpGateway implements ReportFollowUpGateway {
    private final FirebaseAdminProvider firebase;

    public FirestoreReportFollowUpGateway(FirebaseAdminProvider firebase) {
        this.firebase = firebase;
    }

    @Override
    public ReportBundle load(String reportId, String ownerUid) {
        Firestore store = firebase.firestore();
        var reportRef = store.collection("reports").document(reportId);
        try {
            DocumentSnapshot report = reportRef.get().get();
            requireOwner(report, ownerUid);
            List<Map<String, Object>> followUps = reportRef.collection("followUpEvents").get().get()
                    .getDocuments().stream().map(FirestoreReportFollowUpGateway::mutableData).toList();
            List<Map<String, Object>> lifecycle = reportRef.collection("lifecycleEvents").get().get()
                    .getDocuments().stream().map(FirestoreReportFollowUpGateway::mutableData).toList();
            return new ReportBundle(new LinkedHashMap<>(report.getData()), followUps, lifecycle);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("Follow-up information could not be loaded", exception);
        } catch (ExecutionException exception) {
            throw failure("Follow-up information could not be loaded", exception.getCause());
        }
    }

    @Override
    public boolean append(
            String reportId,
            String ownerUid,
            String eventId,
            String requestFingerprint,
            Map<String, Object> event) {
        Firestore store = firebase.firestore();
        var reportRef = store.collection("reports").document(reportId);
        var eventRef = reportRef.collection("followUpEvents").document(eventId);
        try {
            return store.runTransaction(transaction -> {
                DocumentSnapshot report = transaction.get(reportRef).get();
                requireOwner(report, ownerUid);
                DocumentSnapshot existing = transaction.get(eventRef).get();
                if (existing.exists()) {
                    if (!requestFingerprint.equals(existing.getString("requestFingerprint"))) {
                        throw new ReportFollowUpService.FollowUpException(
                                "IDEMPOTENCY_KEY_REUSED", "The idempotency key was reused for another action");
                    }
                    return true;
                }
                transaction.create(eventRef, event);
                return false;
            }).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("The follow-up action could not be recorded", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof ReportFollowUpService.FollowUpException known) throw known;
            throw failure("The follow-up action could not be recorded", exception.getCause());
        }
    }

    private static void requireOwner(DocumentSnapshot report, String ownerUid) {
        if (!report.exists()) {
            throw new ReportFollowUpService.FollowUpException("REPORT_NOT_FOUND", "The report was not found");
        }
        if (!ownerUid.equals(report.getString("ownerUid"))) {
            throw new ReportFollowUpService.FollowUpException("REPORT_FORBIDDEN", "This report belongs to another citizen");
        }
    }

    private static Map<String, Object> mutableData(DocumentSnapshot document) {
        return new LinkedHashMap<>(document.getData());
    }

    private static ReportFollowUpService.FollowUpException failure(String message, Throwable cause) {
        return new ReportFollowUpService.FollowUpException("FOLLOW_UP_STORE_FAILED", message, cause);
    }
}
