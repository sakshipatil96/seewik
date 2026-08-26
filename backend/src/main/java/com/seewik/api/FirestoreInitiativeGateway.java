package com.seewik.api;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Component;

@Component
public class FirestoreInitiativeGateway implements InitiativeGateway {
    private final FirebaseAdminProvider firebase;

    public FirestoreInitiativeGateway(FirebaseAdminProvider firebase) {
        this.firebase = firebase;
    }

    @Override
    public Map<String, Object> create(
            String ownerUid,
            String initiativeId,
            Map<String, Object> initiative,
            Map<String, Object> event,
            Map<String, Object> participation,
            Map<String, Object> ledgerEntry) {
        Firestore store = firebase.firestore();
        try {
            var batch = store.batch();
            batch.create(store.collection("initiatives").document(initiativeId), initiative);
            batch.create(store.collection("initiatives").document(initiativeId)
                    .collection("events").document(String.valueOf(event.get("eventId"))), event);
            batch.create(store.collection("initiativeParticipations")
                    .document(String.valueOf(participation.get("participationId"))), participation);
            batch.create(store.collection("pointsLedger")
                    .document(String.valueOf(ledgerEntry.get("ledgerEntryId"))), ledgerEntry);
            batch.commit().get();
            return Map.copyOf(initiative);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("INITIATIVE_STORE_INTERRUPTED", "The activity could not be saved", exception);
        } catch (ExecutionException exception) {
            throw failure("INITIATIVE_STORE_FAILED", "The activity could not be saved", exception.getCause());
        }
    }

    @Override
    public List<Map<String, Object>> listPublished() {
        try {
            List<QueryDocumentSnapshot> documents = firebase.firestore().collection("initiatives")
                    .whereEqualTo("status", "PUBLISHED")
                    .limit(100)
                    .get().get().getDocuments();
            List<Map<String, Object>> result = new ArrayList<>();
            for (QueryDocumentSnapshot document : documents) result.add(new LinkedHashMap<>(document.getData()));
            return List.copyOf(result);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("INITIATIVE_STORE_INTERRUPTED", "Nearby activities could not be loaded", exception);
        } catch (ExecutionException exception) {
            throw failure("INITIATIVE_STORE_FAILED", "Nearby activities could not be loaded", exception.getCause());
        }
    }

    @Override
    public JoinResult join(String ownerUid, String initiativeId, Instant occurredAt) {
        Firestore store = firebase.firestore();
        DocumentReference initiativeRef = store.collection("initiatives").document(initiativeId);
        String participationId = InitiativeService.participationId(initiativeId, ownerUid);
        DocumentReference participationRef = store.collection("initiativeParticipations").document(participationId);
        try {
            return store.runTransaction(transaction -> {
                DocumentSnapshot initiativeSnapshot = transaction.get(initiativeRef).get();
                if (!initiativeSnapshot.exists()) {
                    throw new InitiativeService.InitiativeException("INITIATIVE_NOT_FOUND", "Activity was not found");
                }
                Map<String, Object> initiative = new LinkedHashMap<>(initiativeSnapshot.getData());
                if (!"PUBLISHED".equals(initiative.get("status"))) {
                    throw new InitiativeService.InitiativeException("INITIATIVE_NOT_JOINABLE", "Activity is not open to join");
                }
                DocumentSnapshot existing = transaction.get(participationRef).get();
                if (existing.exists()) return new JoinResult(Map.copyOf(initiative), true);

                int count = initiative.get("participantCount") instanceof Number number ? number.intValue() : 0;
                int updatedCount = count + 1;
                String eventId = "evt_" + InitiativeService.hash(
                        initiativeId + ":INITIATIVE_JOINED:" + ownerUid);
                String ledgerEntryId = "pts_" + InitiativeService.hash(
                        initiativeId + ":INITIATIVE_JOINED:" + ownerUid);
                Map<String, Object> event = InitiativeService.event(
                        eventId, initiativeId, "INITIATIVE_JOINED", ownerUid, occurredAt);
                Map<String, Object> participation = InitiativeService.participation(
                        participationId, initiativeId, ownerUid, "PARTICIPANT", occurredAt);
                Map<String, Object> ledger = InitiativeService.ledger(
                        ledgerEntryId, initiativeId, eventId, ownerUid, "INITIATIVE_JOINED", occurredAt);
                transaction.create(participationRef, participation);
                transaction.create(initiativeRef.collection("events").document(eventId), event);
                transaction.create(store.collection("pointsLedger").document(ledgerEntryId), ledger);
                transaction.update(initiativeRef, Map.of(
                        "participantCount", updatedCount,
                        "updatedAt", occurredAt.toString()));
                initiative.put("participantCount", updatedCount);
                initiative.put("updatedAt", occurredAt.toString());
                return new JoinResult(Map.copyOf(initiative), false);
            }).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("INITIATIVE_STORE_INTERRUPTED", "The activity join could not be saved", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            while (cause instanceof ExecutionException && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof InitiativeService.InitiativeException initiativeException) throw initiativeException;
            throw failure("INITIATIVE_STORE_FAILED", "The activity join could not be saved", cause);
        }
    }

    private static InitiativeService.InitiativeException failure(String code, String message, Throwable cause) {
        return new InitiativeService.InitiativeException(code, message, cause);
    }
}
