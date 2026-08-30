package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InitiativeAttendanceServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-30T12:15:00Z");
    private static final String SECRET = "attendance-test-secret-with-at-least-32-bytes";

    private AttendanceGateway gateway;
    private InitiativeService service;

    @BeforeEach
    void setUp() {
        gateway = new AttendanceGateway();
        service = new InitiativeService(
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new AttendanceCodeService(SECRET));
    }

    @Test
    void organiserCanViewOnlyTheActiveServerGeneratedCode() {
        var response = service.attendanceCode("organiser", "init-1");
        assertEquals("ATTENDANCE_CODE_ACTIVE", response.status());
        assertTrue(response.code().matches("^[0-9]{6}$"));
        assertEquals("2026-08-30T12:20:00Z", response.rotatesAt());
        assertEquals("2026-08-30T15:00:00Z", response.codeWindowEndsAt());

        gateway.role = "PARTICIPANT";
        assertEquals("INITIATIVE_FORBIDDEN", assertThrows(
                InitiativeService.InitiativeException.class,
                () -> service.attendanceCode("participant", "init-1")).code());
    }

    @Test
    void correctCodeRecordsTwentyPointsAndWrongCodeFailsWithoutLeakingIt() {
        gateway.role = "PARTICIPANT";
        AttendanceCodeService codes = new AttendanceCodeService(SECRET);
        String correct = codes.codeFor("init-1", NOW);

        var response = service.codeAttend(
                "participant", "init-1", new InitiativeService.AttendanceCodeRequest(correct));
        assertTrue(gateway.lastCodeAccepted);
        assertEquals(20, response.participantPointsAwarded());
        assertEquals("ORGANISER_CODE_ATTESTED", response.attendanceBasis());

        gateway.codeStatus = "ATTENDANCE_CODE_INVALID";
        assertEquals("ATTENDANCE_CODE_INVALID", assertThrows(
                InitiativeService.InitiativeException.class,
                () -> service.codeAttend(
                        "participant", "init-1", new InitiativeService.AttendanceCodeRequest("000000"))).code());
        assertFalse(gateway.lastCodeAccepted);
    }

    @Test
    void selfAttendanceIsRecordedAsZeroPointSelfAttestation() {
        gateway.role = "PARTICIPANT";
        var response = service.selfAttend("participant", "init-1");
        assertEquals("SELF_ATTESTED", response.attendanceBasis());
        assertEquals(0, response.participantPointsAwarded());
        assertEquals(3, response.joinerCount());
        assertEquals(2, response.selfAttendanceCount());
        assertEquals("reward-policy-v0.2", response.rewardPolicyVersion());
    }

    @Test
    void participantUiPrioritisesCodeThenRevealsSelfAttendanceAfterTheCodeWindow() {
        gateway.role = "PARTICIPANT";
        var duringCodeWindow = service.mine("participant").initiatives().getFirst();
        assertTrue(duringCodeWindow.canUseOrganiserCode());
        assertFalse(duringCodeWindow.canSelfAttend());

        InitiativeService afterWindow = new InitiativeService(
                gateway,
                Clock.fixed(Instant.parse("2026-08-30T15:00:01Z"), ZoneOffset.UTC),
                new AttendanceCodeService(SECRET));
        var afterCodeWindow = afterWindow.mine("participant").initiatives().getFirst();
        assertFalse(afterCodeWindow.canUseOrganiserCode());
        assertTrue(afterCodeWindow.canSelfAttend());
    }

    private static final class AttendanceGateway implements InitiativeGateway {
        String role = "ORGANISER";
        boolean lastCodeAccepted;
        String codeStatus = "ATTENDANCE_RECORDED";

        private Map<String, Object> initiative() {
            return Map.ofEntries(
                    Map.entry("initiativeId", "init-1"),
                    Map.entry("ownerUid", "organiser"),
                    Map.entry("title", "Cleanup"),
                    Map.entry("category", "CLEANUP"),
                    Map.entry("description", "Clean together"),
                    Map.entry("startAt", "2026-08-30T12:00:00Z"),
                    Map.entry("completedAt", "2026-08-30T12:05:00Z"),
                    Map.entry("placeName", "Public square"),
                    Map.entry("needs", ""),
                    Map.entry("status", "COMPLETED"),
                    Map.entry("participantCount", 4),
                    Map.entry("schemaVersion", "initiative-v0.2"));
        }

        private Map<String, Object> participation(String basis) {
            return Map.of(
                    "participationId", "part-1",
                    "initiativeId", "init-1",
                    "ownerUid", "participant",
                    "role", role,
                    "status", "JOINED",
                    "attendanceStatus", "I_ATTENDED",
                    "attendanceBasis", basis,
                    "attendanceReportedAt", NOW.toString());
        }

        @Override
        public AttendanceContext attendanceContext(String ownerUid, String initiativeId) {
            return new AttendanceContext(initiative(), Map.of("role", role), 3, 1, 1);
        }

        @Override
        public AttendanceResult recordSelfAttendance(String ownerUid, String initiativeId, Instant occurredAt) {
            return new AttendanceResult(
                    "ATTENDANCE_RECORDED", initiative(), participation("SELF_ATTESTED"),
                    3, 2, 1, false, 0, 0, 5);
        }

        @Override
        public AttendanceResult recordCodeAttendance(
                String ownerUid,
                String initiativeId,
                Instant occurredAt,
                long attemptSlot,
                boolean codeAccepted) {
            lastCodeAccepted = codeAccepted;
            return new AttendanceResult(
                    codeStatus, initiative(), participation("ORGANISER_CODE_ATTESTED"),
                    3, 1, 2, false, codeAccepted ? 20 : 0, 0, codeAccepted ? 5 : 4);
        }

        @Override
        public Map<String, Object> create(
                String ownerUid,
                String initiativeId,
                Map<String, Object> initiative,
                Map<String, Object> event,
                Map<String, Object> participation,
                Map<String, Object> ledgerEntry) {
            return initiative;
        }

        @Override
        public List<CitizenInitiative> listPublished(String ownerUid) {
            return List.of();
        }

        @Override
        public List<CitizenInitiative> listForCitizen(String ownerUid) {
            return List.of(new CitizenInitiative(initiative(), role, Map.of(), 3, 1, 1));
        }

        @Override
        public JoinResult join(String ownerUid, String initiativeId, Instant occurredAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransitionResult transition(
                String ownerUid,
                String initiativeId,
                String targetStatus,
                String cancellationReason,
                Instant occurredAt) {
            throw new UnsupportedOperationException();
        }
    }
}
