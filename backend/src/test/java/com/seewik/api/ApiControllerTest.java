package com.seewik.api;

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
        ObjectMapper mapper = new ObjectMapper();
        CivicRouterService router = new CivicRouterService(mapper);
        GeminiService unusedGeminiService = new GeminiService(mapper, "seewik");
        return MockMvcBuilders.standaloneSetup(new ApiController(unusedGeminiService, router)).build();
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
                                {"issueType":"GARBAGE_SOLID_WASTE","prabhagId":"PRABHAG-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUPPORTED_ROUTE"))
                .andExpect(jsonPath("$.routeId").value("NMC-SWM-GARBAGE-v0.1"))
                .andExpect(jsonPath("$.prabhagId").value("PRABHAG-01"))
                .andExpect(jsonPath("$.resolutionMethod").value("SELF_REPORTED"))
                .andExpect(jsonPath("$.authority").value("Nandurbar Municipal Council"))
                .andExpect(jsonPath("$.sourceStatus").value("OFFICIAL_SOURCE"))
                .andExpect(jsonPath("$.reviewStatus").value("REVIEW_PENDING"))
                .andExpect(jsonPath("$.packVersion").value("v0.1"));
    }

    @Test
    void unknownIssueReturnsUnsupportedRoute() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("{\"issueType\":\"ALIEN_INVASION\",\"prabhagId\":\"PRABHAG-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSUPPORTED_ROUTE"))
                .andExpect(jsonPath("$.routeId").doesNotExist());
    }

    @Test
    void missingWardMappingReturnsUnsupportedRoute() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("{\"issueType\":\"STREETLIGHT\",\"prabhagId\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSUPPORTED_ROUTE"));
    }

    @Test
    void invalidPrabhagReturnsUnsupportedRoute() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("{\"issueType\":\"STREETLIGHT\",\"prabhagId\":\"PRABHAG-21\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSUPPORTED_ROUTE"));
    }

    @Test
    void legacyWardIdIsAcceptedAsCompatibilityAlias() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("{\"issueType\":\"STREETLIGHT\",\"wardId\":\"PRABHAG-02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUPPORTED_ROUTE"))
                .andExpect(jsonPath("$.prabhagId").value("PRABHAG-02"));
    }
}
