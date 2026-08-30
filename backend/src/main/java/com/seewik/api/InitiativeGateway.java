package com.seewik.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface InitiativeGateway {
    Map<String, Object> create(
            String ownerUid,
            String initiativeId,
            Map<String, Object> initiative,
            Map<String, Object> event,
            Map<String, Object> participation,
            Map<String, Object> ledgerEntry);

    List<CitizenInitiative> listPublished(String ownerUid);

    List<CitizenInitiative> listForCitizen(String ownerUid);

    JoinResult join(String ownerUid, String initiativeId, Instant occurredAt);

    TransitionResult transition(
            String ownerUid,
            String initiativeId,
            String targetStatus,
            String cancellationReason,
            Instant occurredAt);

    default AttendanceContext attendanceContext(String ownerUid, String initiativeId) {
        throw new UnsupportedOperationException("Attendance is not implemented by this gateway");
    }

    default AttendanceResult recordSelfAttendance(String ownerUid, String initiativeId, Instant occurredAt) {
        throw new UnsupportedOperationException("Attendance is not implemented by this gateway");
    }

    default AttendanceResult recordCodeAttendance(
            String ownerUid,
            String initiativeId,
            Instant occurredAt,
            long attemptSlot,
            boolean codeAccepted) {
        throw new UnsupportedOperationException("Attendance is not implemented by this gateway");
    }

    record JoinResult(Map<String, Object> initiative, boolean alreadyJoined) {}

    record CitizenInitiative(
            Map<String, Object> initiative,
            String role,
            Map<String, Object> participation,
            int joinerCount,
            int selfAttendanceCount,
            int codeAttendanceCount) {
        CitizenInitiative(Map<String, Object> initiative, String role) {
            this(initiative, role, Map.of(), 0, 0, 0);
        }
    }

    record TransitionResult(Map<String, Object> initiative, boolean idempotentReplay, int pointsAwarded) {
        TransitionResult(Map<String, Object> initiative, boolean idempotentReplay) {
            this(initiative, idempotentReplay, 0);
        }
    }

    record AttendanceContext(
            Map<String, Object> initiative,
            Map<String, Object> participation,
            int joinerCount,
            int selfAttendanceCount,
            int codeAttendanceCount) {}

    record AttendanceResult(
            String status,
            Map<String, Object> initiative,
            Map<String, Object> participation,
            int joinerCount,
            int selfAttendanceCount,
            int codeAttendanceCount,
            boolean idempotentReplay,
            int participantPointsAwarded,
            int organiserPointsAwarded,
            int attemptsRemaining) {}
}
