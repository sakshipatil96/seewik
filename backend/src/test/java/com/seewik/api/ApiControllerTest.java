package com.seewik.api;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiControllerTest {
    private MockMvc mvc() throws Exception {
        CivicRouterService router = new CivicRouterService(new ObjectMapper());
        return MockMvcBuilders.standaloneSetup(new ApiController(mock(GeminiService.class), router)).build();
    }

    @Test
    void healthReturnsOk() throws Exception {
        mvc().perform(get("/healthz")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void supportedRouteIsDeterministicAndKeepsIndependentStatuses() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("""
                                {"issueType":"GARBAGE_SOLID_WASTE","wardId":"PRABHAG-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUPPORTED_ROUTE"))
                .andExpect(jsonPath("$.routeId").value("NMC-SWM-GARBAGE-v0.1"))
                .andExpect(jsonPath("$.wardId").value("PRABHAG-01"))
                .andExpect(jsonPath("$.authority").value("Nandurbar Municipal Council"))
                .andExpect(jsonPath("$.sourceStatus").value("OFFICIAL_SOURCE"))
                .andExpect(jsonPath("$.reviewStatus").value("REVIEW_PENDING"))
                .andExpect(jsonPath("$.packVersion").value("v0.1"));
    }

    @Test
    void unknownIssueReturnsUnsupportedRoute() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("{\"issueType\":\"ALIEN_INVASION\",\"wardId\":\"PRABHAG-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSUPPORTED_ROUTE"))
                .andExpect(jsonPath("$.routeId").doesNotExist());
    }

    @Test
    void missingWardMappingReturnsUnsupportedRoute() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("{\"issueType\":\"STREETLIGHT\",\"wardId\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSUPPORTED_ROUTE"));
    }
}
