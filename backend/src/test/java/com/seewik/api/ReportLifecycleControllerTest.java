package com.seewik.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReportLifecycleControllerTest {
    private CitizenIdentityVerifier verifier;
    private ReportLifecycleService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        verifier = mock(CitizenIdentityVerifier.class);
        service = mock(ReportLifecycleService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ReportLifecycleController(verifier, service)).build();
    }

    @Test
    void missingFirebaseTokenReturnsUnauthorizedWithoutCallingTheService() throws Exception {
        when(verifier.verifyBearer(null))
                .thenThrow(new CitizenIdentityVerifier.AuthenticationException("A Firebase ID token is required"));
        mvc.perform(post("/api/reports/report-1/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filedRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
        verify(service, never()).transition(any(), any(), any());
    }

    @Test
    void verifiedFirebaseUidIsUsedForTheOwnerTransition() throws Exception {
        when(verifier.verifyBearer("Bearer valid-token"))
                .thenReturn(new CitizenIdentityVerifier.AuthenticatedCitizen("owner-1"));
        when(service.transition(eq("owner-1"), eq("report-1"), any()))
                .thenReturn(response());
        mvc.perform(post("/api/reports/report-1/transitions")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRANSITION_RECORDED"))
                .andExpect(jsonPath("$.toStatus").value("FILED"))
                .andExpect(jsonPath("$.idempotentReplay").value(false));
        verify(service).transition(eq("owner-1"), eq("report-1"), any());
    }

    @Test
    void anonymousFirebaseTokenCannotBypassLifecycleWriteGate() throws Exception {
        when(verifier.verifyBearer("Bearer anonymous-token"))
                .thenReturn(new CitizenIdentityVerifier.AuthenticatedCitizen("anonymous-owner", false));
        mvc.perform(post("/api/reports/report-1/transitions")
                        .header("Authorization", "Bearer anonymous-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filedRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("GOOGLE_LINK_REQUIRED"));
        verify(service, never()).transition(any(), any(), any());
    }

    @Test
    void crossOwnerTransitionReturnsForbidden() throws Exception {
        when(verifier.verifyBearer("Bearer valid-token"))
                .thenReturn(new CitizenIdentityVerifier.AuthenticatedCitizen("owner-2"));
        when(service.transition(eq("owner-2"), eq("report-1"), any()))
                .thenThrow(new ReportLifecycleService.LifecycleException(
                        "REPORT_FORBIDDEN", "The authenticated user does not own this report"));
        mvc.perform(post("/api/reports/report-1/transitions")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filedRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("REPORT_FORBIDDEN"));
    }

    @Test
    void invalidTransitionReturnsConflict() throws Exception {
        when(verifier.verifyBearer("Bearer valid-token"))
                .thenReturn(new CitizenIdentityVerifier.AuthenticatedCitizen("owner-1"));
        when(service.transition(eq("owner-1"), eq("report-1"), any()))
                .thenThrow(new ReportLifecycleService.LifecycleException(
                        "INVALID_TRANSITION", "Lifecycle transition is not allowed"));
        mvc.perform(post("/api/reports/report-1/transitions")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filedRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSITION"));
    }

    @Test
    void unverifiedOverdueReturnsUnprocessableEntity() throws Exception {
        when(verifier.verifyBearer("Bearer valid-token"))
                .thenReturn(new CitizenIdentityVerifier.AuthenticatedCitizen("owner-1"));
        when(service.transition(eq("owner-1"), eq("report-1"), any()))
                .thenThrow(new ReportLifecycleService.LifecycleException(
                        "OVERDUE_NOT_ELIGIBLE", "No verified due date exists"));
        mvc.perform(post("/api/reports/report-1/transitions")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"OVERDUE\",\"idempotencyKey\":\"overdue-once\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("OVERDUE_NOT_ELIGIBLE"));
    }

    private static String filedRequest() {
        return "{\"toStatus\":\"FILED\",\"idempotencyKey\":\"file-once\"}";
    }

    private static ReportLifecycleService.TransitionResponse response() {
        return new ReportLifecycleService.TransitionResponse(
                "TRANSITION_RECORDED",
                "evt_1",
                "report-1",
                "DRAFT",
                "FILED",
                "REPORT_FILED",
                "CITIZEN_ATTESTATION",
                "report-lifecycle-v0.1",
                "v0.2",
                "route-hash",
                "2026-08-25T12:00:00Z",
                false);
    }
}
