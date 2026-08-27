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
import java.time.format.DateTimeParseException;
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
    public List<CitizenInitiative> listPublished(String ownerUid) {
        try {
            List<QueryDocumentSnapshot> documents = firebase.firestore().collection("initiatives")
                    .whereEqualTo("status", "PUBLISHED")
                    .limit(100)
                    .get().get().getDocuments();
            List<CitizenInitiative> result = new ArrayList<>();
            for (QueryDocumentSnapshot document : documents) {
                String initiativeId = document.getId();
                DocumentSnapshot participation = firebase.firestore().collection("initiativeParticipations")
                        .document(InitiativeService.participationId(initiativeId, ownerUid)).get().get();
                String role = participation.exists() ? participation.getString("role") : null;
                result.add(new CitizenInitiative(new LinkedHashMap<>(document.getData()), role));
            }
            return List.copyOf(result);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("INITIATIVE_STORE_INTERRUPTED", "Nearby activities could not be loaded", exception);
        } catch (ExecutionException exception) {
            throw failure("INITIATIVE_STORE_FAILED", "Nearby activities could not be loaded", exception.getCause());
        }
    }

    @Override
    public List<CitizenInitiative> listForCitizen(String ownerUid) {
        try {
            List<QueryDocumentSnapshot> participations = firebase.firestore()
                    .collection("initiativeParticipations")
                    .whereEqualTo("ownerUid", ownerUid)
                    .limit(100)
                    .get().get().getDocuments();
            List<CitizenInitiative> result = new ArrayList<>();
            for (QueryDocumentSnapshot participation : participations) {
                String initiativeId = participation.getString("initiativeId");
                if (initiativeId == null || initiativeId.isBlank()) continue;
                DocumentSnapshot initiative = firebase.firestore()
                        .collection("initiatives").document(initiativeId).get().get();
                if (initiative.exists()) {
                    result.add(new CitizenInitiative(
                            new LinkedHashMap<>(initiative.getData()), participation.getString("role")));
                }
            }
            return List.copyOf(result);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("INITIATIVE_STORE_INTERRUPTED", "Your activities could not be loaded", exception);
        } catch (ExecutionException exception) {
            throw failure("INITIATIVE_STORE_FAILED", "Your activities could not be loaded", exception.getCause());
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

    @Override
    public TransitionResult transition(
            String ownerUid,
            String initiativeId,
            String targetStatus,
            String cancellationReason,
            Instant occurredAt) {
        Firestore store = firebase.firestore();
        DocumentReference initiativeRef = store.collection("initiatives").document(initiativeId);
        try {
            return store.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(initiativeRef).get();
                if (!snapshot.exists()) {
                    throw new InitiativeService.InitiativeException("INITIATIVE_NOT_FOUND", "Activity was not found");
                }
                Map<String, Object> initiative = new LinkedHashMap<>(snapshot.getData());
                if (!ownerUid.equals(initiative.get("ownerUid"))) {
                    throw new InitiativeService.InitiativeException(
                            "INITIATIVE_FORBIDDEN", "Only the organiser can change this activity");
                }
                String currentStatus = String.valueOf(initiative.get("status"));
                if (targetStatus.equals(currentStatus)) {
                    return new TransitionResult(Map.copyOf(initiative), true);
                }
                if (!"PUBLISHED".equals(currentStatus)) {
                    throw new InitiativeService.InitiativeException(
                            "INITIATIVE_INVALID_TRANSITION", "This activity can no longer be changed");
                }
                if ("COMPLETED".equals(targetStatus)) {
                    Instant startAt;
                    try {
                        startAt = Instant.parse(String.valueOf(initiative.get("startAt")));
                    } catch (DateTimeParseException exception) {
                        throw new InitiativeService.InitiativeException(
                                "INITIATIVE_STORE_FAILED", "The activity date could not be verified");
                    }
                    if (occurredAt.isBefore(startAt)) {
                        throw new InitiativeService.InitiativeException(
                                "INITIATIVE_NOT_STARTED", "This activity can be completed after its scheduled time");
                    }
                }

                String eventType = "CANCELLED".equals(targetStatus)
                        ? "INITIATIVE_CANCELLED"
                        : "INITIATIVE_COMPLETED";
                String eventId = "evt_" + InitiativeService.hash(initiativeId + ":" + eventType);
                Map<String, Object> event = InitiativeService.event(
                        eventId, initiativeId, eventType, ownerUid, occurredAt);
                Map<String, Object> updates = new LinkedHashMap<>();
                updates.put("status", targetStatus);
                updates.put("updatedAt", occurredAt.toString());
                if ("CANCELLED".equals(targetStatus)) {
                    updates.put("cancelledAt", occurredAt.toString());
                    updates.put("cancellationReason", cancellationReason);
                    event.put("reason", cancellationReason);
                } else {
                    updates.put("completedAt", occurredAt.toString());
                }
                transaction.create(initiativeRef.collection("events").document(eventId), event);
                transaction.update(initiativeRef, updates);
                initiative.putAll(updates);
                return new TransitionResult(Map.copyOf(initiative), false);
            }).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("INITIATIVE_STORE_INTERRUPTED", "The activity change could not be saved", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            while (cause instanceof ExecutionException && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof InitiativeService.InitiativeException initiativeException) throw initiativeException;
            throw failure("INITIATIVE_STORE_FAILED", "The activity change could not be saved", cause);
        }
    }

    private static InitiativeService.InitiativeException failure(String code, String message, Throwable cause) {
        return new InitiativeService.InitiativeException(code, message, cause);
    }
}
