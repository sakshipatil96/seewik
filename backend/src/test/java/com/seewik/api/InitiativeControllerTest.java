package com.seewik.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
        };
        mvc = MockMvcBuilders.standaloneSetup(
                new InitiativeController(verifier, new InitiativeService(gateway))).build();
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
}
