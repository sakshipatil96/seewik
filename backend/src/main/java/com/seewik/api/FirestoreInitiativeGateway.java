package com.seewik.api;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.Transaction;
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
        DocumentReference initiativeRef = store.collection("initiatives").document(initiativeId);
        DocumentReference eventRef = initiativeRef.collection("events")
                .document(String.valueOf(event.get("eventId")));
        DocumentReference participationRef = store.collection("initiativeParticipations")
                .document(String.valueOf(participation.get("participationId")));
        DocumentReference ledgerRef = store.collection("pointsLedger")
                .document(String.valueOf(ledgerEntry.get("ledgerEntryId")));
        try {
            return store.runTransaction(transaction -> {
                DocumentSnapshot existing = transaction.get(initiativeRef).get();
                if (existing.exists()) return Map.copyOf(existing.getData());
                transaction.set(initiativeRef, initiative);
                transaction.set(eventRef, event);
                transaction.set(participationRef, participation);
                transaction.set(ledgerRef, ledgerEntry);
                return Map.copyOf(initiative);
            }).get();
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
            Firestore store = firebase.firestore();
            List<QueryDocumentSnapshot> documents = store.collection("initiatives")
                    .whereIn("status", List.of("PUBLISHED", "COMPLETED"))
                    .limit(100)
                    .get().get().getDocuments();
            List<CitizenInitiative> result = new ArrayList<>();
            for (QueryDocumentSnapshot document : documents) {
                String initiativeId = document.getId();
                DocumentSnapshot participation = store.collection("initiativeParticipations")
                        .document(InitiativeService.participationId(initiativeId, ownerUid)).get().get();
                String role = participation.exists() ? participation.getString("role") : null;
                ParticipationSummary summary = participationSummary(store, initiativeId);
                result.add(new CitizenInitiative(
                        new LinkedHashMap<>(document.getData()),
                        role,
                        participation.exists() ? new LinkedHashMap<>(participation.getData()) : Map.of(),
                        summary.joiners(),
                        summary.selfAttendance(),
                        summary.codeAttendance()));
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
            Firestore store = firebase.firestore();
            List<QueryDocumentSnapshot> participations = store
                    .collection("initiativeParticipations")
                    .whereEqualTo("ownerUid", ownerUid)
                    .limit(100)
                    .get().get().getDocuments();
            List<CitizenInitiative> result = new ArrayList<>();
            for (QueryDocumentSnapshot participation : participations) {
                String initiativeId = participation.getString("initiativeId");
                if (initiativeId == null || initiativeId.isBlank()) continue;
                DocumentSnapshot initiative = store
                        .collection("initiatives").document(initiativeId).get().get();
                if (initiative.exists()) {
                    ParticipationSummary summary = participationSummary(store, initiativeId);
                    result.add(new CitizenInitiative(
                            new LinkedHashMap<>(initiative.getData()),
                            participation.getString("role"),
                            new LinkedHashMap<>(participation.getData()),
                            summary.joiners(),
                            summary.selfAttendance(),
                            summary.codeAttendance()));
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
                DocumentSnapshot existing = transaction.get(participationRef).get();
                if (existing.exists()) return new JoinResult(Map.copyOf(initiative), true);

                Instant startAt = requiredInstant(initiative.get("startAt"), "The activity date could not be verified");
                Instant codeWindowEndsAt = startAt.plus(InitiativeService.CODE_WINDOW);
                String status = String.valueOf(initiative.get("status"));
                boolean joinableStatus = "PUBLISHED".equals(status)
                        || ("COMPLETED".equals(status)
                            && !occurredAt.isBefore(startAt)
                            && !occurredAt.isAfter(codeWindowEndsAt));
                if (!joinableStatus || occurredAt.isAfter(codeWindowEndsAt)) {
                    throw new InitiativeService.InitiativeException(
                            "INITIATIVE_NOT_JOINABLE", "The activity is outside its joining window");
                }

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
                QuerySnapshot participationSnapshot = transaction.get(store.collection("initiativeParticipations")
                        .whereEqualTo("initiativeId", initiativeId)).get();
                ParticipationSummary summary = participationSummary(participationSnapshot.getDocuments());
                String organiserLedgerId = organiserLedgerId(initiativeId, ownerUid);
                DocumentReference organiserLedgerRef = store.collection("pointsLedger").document(organiserLedgerId);
                DocumentSnapshot organiserLedgerSnapshot = transaction.get(organiserLedgerRef).get();
                if ("COMPLETED".equals(targetStatus)) {
                    Instant startAt = requiredInstant(
                            initiative.get("startAt"), "The activity date could not be verified");
                    if (occurredAt.isBefore(startAt)) {
                        throw new InitiativeService.InitiativeException(
                                "INITIATIVE_NOT_STARTED", "This activity can be completed after its scheduled time");
                    }
                } else if (summary.codeAttendance() > 0) {
                    throw new InitiativeService.InitiativeException(
                            "INITIATIVE_ATTENDANCE_EXISTS",
                            "This activity cannot be cancelled after code attendance is recorded");
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
                int organiserPoints = 0;
                boolean organiserEligible = "COMPLETED".equals(targetStatus)
                        && summary.codeAttendance() >= 2
                        && !organiserLedgerSnapshot.exists();
                if (organiserEligible) {
                    organiserPoints = InitiativeService.ORGANISER_COMPLETION_POINTS;
                    createOrganiserReward(
                            transaction,
                            store,
                            initiativeRef,
                            initiativeId,
                            ownerUid,
                            organiserLedgerRef,
                            occurredAt);
                }
                event.put("pointsAwarded", organiserPoints);
                transaction.create(initiativeRef.collection("events").document(eventId), event);
                transaction.update(initiativeRef, updates);
                initiative.putAll(updates);
                return new TransitionResult(Map.copyOf(initiative), false, organiserPoints);
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

    @Override
    public AttendanceContext attendanceContext(String ownerUid, String initiativeId) {
        Firestore store = firebase.firestore();
        try {
            DocumentSnapshot initiative = store.collection("initiatives").document(initiativeId).get().get();
            if (!initiative.exists()) {
                throw new InitiativeService.InitiativeException("INITIATIVE_NOT_FOUND", "Activity was not found");
            }
            DocumentSnapshot participation = store.collection("initiativeParticipations")
                    .document(InitiativeService.participationId(initiativeId, ownerUid)).get().get();
            if (!participation.exists()) {
                throw new InitiativeService.InitiativeException(
                        "ATTENDANCE_NOT_PARTICIPANT", "Join this activity before recording attendance");
            }
            ParticipationSummary summary = participationSummary(store, initiativeId);
            return new AttendanceContext(
                    new LinkedHashMap<>(initiative.getData()),
                    new LinkedHashMap<>(participation.getData()),
                    summary.joiners(),
                    summary.selfAttendance(),
                    summary.codeAttendance());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("INITIATIVE_STORE_INTERRUPTED", "Attendance could not be loaded", exception);
        } catch (ExecutionException exception) {
            throw failure("INITIATIVE_STORE_FAILED", "Attendance could not be loaded", exception.getCause());
        }
    }

    @Override
    public AttendanceResult recordSelfAttendance(String ownerUid, String initiativeId, Instant occurredAt) {
        Firestore store = firebase.firestore();
        DocumentReference initiativeRef = store.collection("initiatives").document(initiativeId);
        DocumentReference participationRef = store.collection("initiativeParticipations")
                .document(InitiativeService.participationId(initiativeId, ownerUid));
        try {
            return store.runTransaction(transaction -> {
                DocumentSnapshot initiativeSnapshot = transaction.get(initiativeRef).get();
                DocumentSnapshot participationSnapshot = transaction.get(participationRef).get();
                QuerySnapshot participations = transaction.get(store.collection("initiativeParticipations")
                        .whereEqualTo("initiativeId", initiativeId)).get();
                if (!initiativeSnapshot.exists()) {
                    throw new InitiativeService.InitiativeException("INITIATIVE_NOT_FOUND", "Activity was not found");
                }
                if (!participationSnapshot.exists()
                        || !"PARTICIPANT".equals(participationSnapshot.getString("role"))) {
                    throw new InitiativeService.InitiativeException(
                            "ATTENDANCE_NOT_PARTICIPANT", "Only a joined participant can report attendance");
                }
                Map<String, Object> initiative = new LinkedHashMap<>(initiativeSnapshot.getData());
                Map<String, Object> participation = new LinkedHashMap<>(participationSnapshot.getData());
                ParticipationSummary summary = participationSummary(participations.getDocuments());
                String existingBasis = String.valueOf(participation.getOrDefault("attendanceBasis", ""));
                if ("SELF_ATTESTED".equals(existingBasis)) {
                    return attendanceResult(
                            "ATTENDANCE_RECORDED", initiative, participation, summary, true, 0, 0, 5);
                }
                if (!existingBasis.isBlank()) {
                    throw new InitiativeService.InitiativeException(
                            "ATTENDANCE_ALREADY_RECORDED", "Attendance is already recorded with another method");
                }
                if (!"COMPLETED".equals(initiative.get("status"))) {
                    throw new InitiativeService.InitiativeException(
                            "ATTENDANCE_REQUIRES_COMPLETION", "Self-attendance is available after completion");
                }
                Instant completedAt = requiredInstant(
                        initiative.get("completedAt"), "The completion time could not be verified");
                if (occurredAt.isBefore(completedAt)
                        || occurredAt.isAfter(completedAt.plus(InitiativeService.SELF_ATTENDANCE_WINDOW))) {
                    throw new InitiativeService.InitiativeException(
                            "SELF_ATTENDANCE_WINDOW_CLOSED", "The seven-day self-attendance window is closed");
                }

                String eventId = attendanceEventId(initiativeId, ownerUid);
                String ledgerId = "pts_" + InitiativeService.hash(
                        initiativeId + ":INITIATIVE_ATTENDANCE_SELF_ATTESTED:" + ownerUid);
                Map<String, Object> event = InitiativeService.attendanceEvent(
                        eventId,
                        initiativeId,
                        "INITIATIVE_ATTENDANCE_SELF_ATTESTED",
                        ownerUid,
                        "SELF_ATTESTED",
                        0,
                        occurredAt);
                Map<String, Object> ledger = InitiativeService.ledger(
                        ledgerId,
                        initiativeId,
                        eventId,
                        ownerUid,
                        "INITIATIVE_ATTENDANCE_SELF_ATTESTED",
                        occurredAt);
                Map<String, Object> updates = attendanceUpdates("SELF_ATTESTED", occurredAt);
                transaction.update(participationRef, updates);
                transaction.create(initiativeRef.collection("events").document(eventId), event);
                transaction.create(store.collection("pointsLedger").document(ledgerId), ledger);
                participation.putAll(updates);
                ParticipationSummary updated = new ParticipationSummary(
                        summary.joiners(), summary.selfAttendance() + 1, summary.codeAttendance());
                return attendanceResult(
                        "ATTENDANCE_RECORDED", initiative, participation, updated, false, 0, 0, 5);
            }).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("INITIATIVE_STORE_INTERRUPTED", "Attendance could not be recorded", exception);
        } catch (ExecutionException exception) {
            Throwable cause = unwrap(exception);
            if (cause instanceof InitiativeService.InitiativeException initiativeException) throw initiativeException;
            throw failure("INITIATIVE_STORE_FAILED", "Attendance could not be recorded", cause);
        }
    }

    @Override
    public AttendanceResult recordCodeAttendance(
            String ownerUid,
            String initiativeId,
            Instant occurredAt,
            long attemptSlot,
            boolean codeAccepted) {
        Firestore store = firebase.firestore();
        DocumentReference initiativeRef = store.collection("initiatives").document(initiativeId);
        DocumentReference participationRef = store.collection("initiativeParticipations")
                .document(InitiativeService.participationId(initiativeId, ownerUid));
        String attemptId = "attempt_" + InitiativeService.hash(
                initiativeId + ":" + ownerUid + ":" + attemptSlot);
        DocumentReference attemptRef = store.collection("initiativeAttendanceAttempts").document(attemptId);
        try {
            return store.runTransaction(transaction -> {
                DocumentSnapshot initiativeSnapshot = transaction.get(initiativeRef).get();
                DocumentSnapshot participationSnapshot = transaction.get(participationRef).get();
                DocumentSnapshot attemptSnapshot = transaction.get(attemptRef).get();
                QuerySnapshot participations = transaction.get(store.collection("initiativeParticipations")
                        .whereEqualTo("initiativeId", initiativeId)).get();
                if (!initiativeSnapshot.exists()) {
                    throw new InitiativeService.InitiativeException("INITIATIVE_NOT_FOUND", "Activity was not found");
                }
                if (!participationSnapshot.exists()
                        || !"PARTICIPANT".equals(participationSnapshot.getString("role"))) {
                    throw new InitiativeService.InitiativeException(
                            "ATTENDANCE_NOT_PARTICIPANT", "Only a joined participant can use the organiser code");
                }
                Map<String, Object> initiative = new LinkedHashMap<>(initiativeSnapshot.getData());
                Map<String, Object> participation = new LinkedHashMap<>(participationSnapshot.getData());
                ParticipationSummary summary = participationSummary(participations.getDocuments());
                String organiserUid = String.valueOf(initiative.get("ownerUid"));
                String organiserLedgerId = organiserLedgerId(initiativeId, organiserUid);
                DocumentReference organiserLedgerRef = store.collection("pointsLedger").document(organiserLedgerId);
                DocumentSnapshot organiserLedgerSnapshot = transaction.get(organiserLedgerRef).get();

                String existingBasis = String.valueOf(participation.getOrDefault("attendanceBasis", ""));
                if ("ORGANISER_CODE_ATTESTED".equals(existingBasis)) {
                    return attendanceResult(
                            "ATTENDANCE_RECORDED", initiative, participation, summary, true, 0, 0, 5);
                }
                if (!existingBasis.isBlank()) {
                    throw new InitiativeService.InitiativeException(
                            "ATTENDANCE_ALREADY_RECORDED", "Attendance is already recorded with another method");
                }
                Instant startAt = requiredInstant(
                        initiative.get("startAt"), "The activity time could not be verified");
                Instant windowEndsAt = startAt.plus(InitiativeService.CODE_WINDOW);
                String status = String.valueOf(initiative.get("status"));
                if ((!"PUBLISHED".equals(status) && !"COMPLETED".equals(status))
                        || occurredAt.isBefore(startAt)
                        || occurredAt.isAfter(windowEndsAt)) {
                    throw new InitiativeService.InitiativeException(
                            "ATTENDANCE_CODE_WINDOW_CLOSED", "The organiser code is outside its three-hour window");
                }

                int failedAttempts = attemptSnapshot.exists()
                        && attemptSnapshot.getLong("failedAttempts") != null
                        ? attemptSnapshot.getLong("failedAttempts").intValue()
                        : 0;
                if (failedAttempts >= 5) {
                    return attendanceResult(
                            "ATTENDANCE_RATE_LIMITED", initiative, participation, summary, false, 0, 0, 0);
                }
                if (!codeAccepted) {
                    int updatedAttempts = failedAttempts + 1;
                    transaction.set(attemptRef, Map.of(
                            "attemptId", attemptId,
                            "initiativeIdHash", InitiativeService.hash(initiativeId),
                            "ownerUidHash", InitiativeService.hash(ownerUid),
                            "slot", attemptSlot,
                            "failedAttempts", updatedAttempts,
                            "updatedAt", occurredAt.toString(),
                            "schemaVersion", InitiativeService.ATTENDANCE_SCHEMA_VERSION));
                    return attendanceResult(
                            "ATTENDANCE_CODE_INVALID",
                            initiative,
                            participation,
                            summary,
                            false,
                            0,
                            0,
                            5 - updatedAttempts);
                }

                String eventId = attendanceEventId(initiativeId, ownerUid);
                String participantLedgerId = "pts_" + InitiativeService.hash(
                        initiativeId + ":INITIATIVE_ATTENDANCE_ORGANISER_CODE_ATTESTED:" + ownerUid);
                Map<String, Object> event = InitiativeService.attendanceEvent(
                        eventId,
                        initiativeId,
                        "INITIATIVE_ATTENDANCE_ORGANISER_CODE_ATTESTED",
                        ownerUid,
                        "ORGANISER_CODE_ATTESTED",
                        InitiativeService.CODE_ATTENDANCE_POINTS,
                        occurredAt);
                Map<String, Object> participantLedger = InitiativeService.rewardedLedger(
                        participantLedgerId,
                        initiativeId,
                        eventId,
                        ownerUid,
                        "INITIATIVE_ATTENDANCE_ORGANISER_CODE_ATTESTED",
                        InitiativeService.CODE_ATTENDANCE_POINTS,
                        occurredAt);
                Map<String, Object> updates = attendanceUpdates("ORGANISER_CODE_ATTESTED", occurredAt);
                int updatedCodeAttendance = summary.codeAttendance() + 1;
                boolean organiserEligible = "COMPLETED".equals(status)
                        && updatedCodeAttendance >= 2
                        && !organiserLedgerSnapshot.exists();
                int organiserPoints = 0;
                if (organiserEligible) {
                    organiserPoints = InitiativeService.ORGANISER_COMPLETION_POINTS;
                    createOrganiserReward(
                            transaction,
                            store,
                            initiativeRef,
                            initiativeId,
                            organiserUid,
                            organiserLedgerRef,
                            occurredAt);
                }
                transaction.update(participationRef, updates);
                transaction.create(initiativeRef.collection("events").document(eventId), event);
                transaction.create(store.collection("pointsLedger").document(participantLedgerId), participantLedger);
                participation.putAll(updates);
                ParticipationSummary updated = new ParticipationSummary(
                        summary.joiners(), summary.selfAttendance(), updatedCodeAttendance);
                return attendanceResult(
                        "ATTENDANCE_RECORDED",
                        initiative,
                        participation,
                        updated,
                        false,
                        InitiativeService.CODE_ATTENDANCE_POINTS,
                        organiserPoints,
                        5 - failedAttempts);
            }).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("INITIATIVE_STORE_INTERRUPTED", "Attendance could not be recorded", exception);
        } catch (ExecutionException exception) {
            Throwable cause = unwrap(exception);
            if (cause instanceof InitiativeService.InitiativeException initiativeException) throw initiativeException;
            throw failure("INITIATIVE_STORE_FAILED", "Attendance could not be recorded", cause);
        }
    }

    private static Map<String, Object> attendanceUpdates(String basis, Instant occurredAt) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("attendanceStatus", "I_ATTENDED");
        updates.put("attendanceBasis", basis);
        updates.put("attendanceReportedAt", occurredAt.toString());
        updates.put("attendanceSchemaVersion", InitiativeService.ATTENDANCE_SCHEMA_VERSION);
        updates.put("updatedAt", occurredAt.toString());
        return updates;
    }

    private static AttendanceResult attendanceResult(
            String status,
            Map<String, Object> initiative,
            Map<String, Object> participation,
            ParticipationSummary summary,
            boolean idempotentReplay,
            int participantPoints,
            int organiserPoints,
            int attemptsRemaining) {
        return new AttendanceResult(
                status,
                Map.copyOf(initiative),
                Map.copyOf(participation),
                summary.joiners(),
                summary.selfAttendance(),
                summary.codeAttendance(),
                idempotentReplay,
                participantPoints,
                organiserPoints,
                attemptsRemaining);
    }

    private static void createOrganiserReward(
            Transaction transaction,
            Firestore store,
            DocumentReference initiativeRef,
            String initiativeId,
            String organiserUid,
            DocumentReference ledgerRef,
            Instant occurredAt) {
        String eventType = "INITIATIVE_ORGANISER_COMPLETED_REWARDED";
        String eventId = "evt_" + InitiativeService.hash(initiativeId + ":" + eventType);
        Map<String, Object> event = InitiativeService.event(
                eventId, initiativeId, eventType, organiserUid, occurredAt);
        event.put("pointsAwarded", InitiativeService.ORGANISER_COMPLETION_POINTS);
        event.put("rewardPolicyVersion", InitiativeService.REWARD_POLICY_VERSION);
        Map<String, Object> ledger = InitiativeService.rewardedLedger(
                ledgerRef.getId(),
                initiativeId,
                eventId,
                organiserUid,
                eventType,
                InitiativeService.ORGANISER_COMPLETION_POINTS,
                occurredAt);
        transaction.create(initiativeRef.collection("events").document(eventId), event);
        transaction.create(store.collection("pointsLedger").document(ledgerRef.getId()), ledger);
    }

    private static String organiserLedgerId(String initiativeId, String organiserUid) {
        return "pts_" + InitiativeService.hash(
                initiativeId + ":INITIATIVE_ORGANISER_COMPLETED_REWARDED:" + organiserUid);
    }

    private static String attendanceEventId(String initiativeId, String ownerUid) {
        return "evt_" + InitiativeService.hash(initiativeId + ":INITIATIVE_ATTENDANCE:" + ownerUid);
    }

    private static ParticipationSummary participationSummary(Firestore store, String initiativeId)
            throws InterruptedException, ExecutionException {
        return participationSummary(store.collection("initiativeParticipations")
                .whereEqualTo("initiativeId", initiativeId)
                .get().get().getDocuments());
    }

    private static ParticipationSummary participationSummary(
            List<? extends DocumentSnapshot> participations) {
        int joiners = 0;
        int selfAttendance = 0;
        int codeAttendance = 0;
        for (DocumentSnapshot participation : participations) {
            if (!"PARTICIPANT".equals(participation.getString("role"))) continue;
            joiners++;
            String basis = participation.getString("attendanceBasis");
            if ("SELF_ATTESTED".equals(basis)) selfAttendance++;
            if ("ORGANISER_CODE_ATTESTED".equals(basis)) codeAttendance++;
        }
        return new ParticipationSummary(joiners, selfAttendance, codeAttendance);
    }

    private static Instant requiredInstant(Object value, String message) {
        try {
            return Instant.parse(String.valueOf(value));
        } catch (DateTimeParseException exception) {
            throw new InitiativeService.InitiativeException("INITIATIVE_STORE_FAILED", message);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof ExecutionException && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }

    private record ParticipationSummary(int joiners, int selfAttendance, int codeAttendance) {}

    private static InitiativeService.InitiativeException failure(String code, String message, Throwable cause) {
        return new InitiativeService.InitiativeException(code, message, cause);
    }
}
