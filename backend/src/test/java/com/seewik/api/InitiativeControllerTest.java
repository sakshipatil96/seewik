package com.seewik.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InitiativeControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CitizenIdentityVerifier verifier = header -> {
            if ("Bearer anonymous".equals(header)) {
                return new CitizenIdentityVerifier.AuthenticatedCitizen("anonymous-1", false);
            }
            if (!"Bearer valid".equals(header)) {
                throw new CitizenIdentityVerifier.AuthenticationException("A Firebase ID token is required");
            }
            return new CitizenIdentityVerifier.AuthenticatedCitizen("owner-1");
        };
        InitiativeGateway gateway = new InitiativeGateway() {
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
                return List.of();
            }

            @Override
            public JoinResult join(String ownerUid, String initiativeId, Instant occurredAt) {
                return new JoinResult(Map.of("participantCount", 2), false);
            }

            @Override
            public TransitionResult transition(
                    String ownerUid,
                    String initiativeId,
                    String targetStatus,
                    String cancellationReason,
                    Instant occurredAt) {
                return new TransitionResult(Map.of("status", targetStatus), false);
            }

            @Override
            public AttendanceContext attendanceContext(String ownerUid, String initiativeId) {
                return new AttendanceContext(
                        Map.of(
                                "initiativeId", initiativeId,
                                "ownerUid", ownerUid,
                                "startAt", "2026-08-30T12:00:00Z",
                                "status", "COMPLETED"),
                        Map.of("role", "ORGANISER"),
                        2,
                        0,
                        2);
            }

            @Override
            public AttendanceResult recordSelfAttendance(
                    String ownerUid, String initiativeId, Instant occurredAt) {
                return attendanceResult(initiativeId, "SELF_ATTESTED", 0);
            }

            @Override
            public AttendanceResult recordCodeAttendance(
                    String ownerUid,
                    String initiativeId,
                    Instant occurredAt,
                    long attemptSlot,
                    boolean codeAccepted) {
                return attendanceResult(initiativeId, "ORGANISER_CODE_ATTESTED", 20);
            }

            private AttendanceResult attendanceResult(String initiativeId, String basis, int points) {
                return new AttendanceResult(
                        "ATTENDANCE_RECORDED",
                        Map.of("initiativeId", initiativeId),
                        Map.of(
                                "attendanceStatus", "I_ATTENDED",
                                "attendanceBasis", basis,
                                "attendanceReportedAt", "2026-08-30T12:15:00Z"),
                        2,
                        "SELF_ATTESTED".equals(basis) ? 1 : 0,
                        "ORGANISER_CODE_ATTESTED".equals(basis) ? 1 : 0,
                        false,
                        points,
                        0,
                        5);
            }
        };
        mvc = MockMvcBuilders.standaloneSetup(
                new InitiativeController(verifier, new InitiativeService(
                        gateway,
                        Clock.fixed(Instant.parse("2026-08-30T12:15:00Z"), ZoneOffset.UTC),
                        new AttendanceCodeService("attendance-test-secret-with-at-least-32-bytes")))).build();
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mvc.perform(post("/api/initiatives/nearby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":21.36,\"longitude\":74.24}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void validCitizenCanDiscoverAndJoin() throws Exception {
        mvc.perform(post("/api/initiatives/nearby")
                        .header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":21.36,\"longitude\":74.24}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEARBY_INITIATIVES"));

        mvc.perform(post("/api/initiatives/init-1/join")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("JOINED"))
                .andExpect(jsonPath("$.participantCount").value(2));
    }

    @Test
    void anonymousCitizenCanDiscoverButCannotBypassInitiativeWriteGate() throws Exception {
        mvc.perform(post("/api/initiatives/nearby")
                        .header("Authorization", "Bearer anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":21.36,\"longitude\":74.24}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/initiatives")
                        .header("Authorization", "Bearer anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("GOOGLE_LINK_REQUIRED"));

        mvc.perform(post("/api/initiatives/init-1/join")
                        .header("Authorization", "Bearer anonymous"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("GOOGLE_LINK_REQUIRED"));

        mvc.perform(post("/api/initiatives/init-1/cancel")
                        .header("Authorization", "Bearer anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Rain\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("GOOGLE_LINK_REQUIRED"));

        mvc.perform(post("/api/initiatives/init-1/complete")
                        .header("Authorization", "Bearer anonymous"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("GOOGLE_LINK_REQUIRED"));

        mvc.perform(post("/api/initiatives/init-1/attendance/self")
                        .header("Authorization", "Bearer anonymous"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("GOOGLE_LINK_REQUIRED"));

        mvc.perform(post("/api/initiatives/init-1/attendance/code")
                        .header("Authorization", "Bearer anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("GOOGLE_LINK_REQUIRED"));
    }

    @Test
    void organiserLifecycleEndpointsReturnZeroPointTransitions() throws Exception {
        mvc.perform(post("/api/initiatives/init-1/cancel")
                        .header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Heavy rain\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initiativeStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.pointsAwarded").value(0));

        mvc.perform(post("/api/initiatives/init-1/complete")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initiativeStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.pointsAwarded").value(0));
    }

    @Test
    void linkedCitizenCanUseOwnerScopedAttendanceEndpoints() throws Exception {
        mvc.perform(get("/api/initiatives/init-1/attendance/code")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATTENDANCE_CODE_ACTIVE"))
                .andExpect(jsonPath("$.code").isString());

        mvc.perform(post("/api/initiatives/init-1/attendance/self")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceBasis").value("SELF_ATTESTED"))
                .andExpect(jsonPath("$.participantPointsAwarded").value(0));

        String code = new AttendanceCodeService("attendance-test-secret-with-at-least-32-bytes")
                .codeFor("init-1", Instant.parse("2026-08-30T12:15:00Z"));
        mvc.perform(post("/api/initiatives/init-1/attendance/code")
                        .header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceBasis").value("ORGANISER_CODE_ATTESTED"))
                .andExpect(jsonPath("$.participantPointsAwarded").value(20));
    }
}
