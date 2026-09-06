package com.seewik.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiControllerTest {
    private MockMvc mvc() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CivicRouterService router = new CivicRouterService(mapper);
        PrabhagBoundaryGateway gateway = (latitude, longitude) -> {
            if (latitude < 21.2037780 || latitude > 21.5237780
                    || longitude < 74.0811418 || longitude > 74.4011418) {
                return Optional.empty();
            }
            return Optional.of(new PrabhagBoundaryGateway.BoundaryMatch(
                    "PRABHAG-11",
                    "Prabhag 11",
                    "APPROXIMATE_DIGITISED_MUNICIPAL_OFFICE_MAP_IMAGE",
                    true,
                    "Nandurbar municipal-office 2025 wall-map photograph",
                    "MUNICIPAL_OFFICE_WALL_MAP_PHOTO",
                    "NOT_AUTHORITY_VERIFIED",
                    "seewik-map-trace-v0.2"));
        };
        PrabhagResolverService resolver = new PrabhagResolverService(gateway);
        return MockMvcBuilders.standaloneSetup(new ApiController(router, resolver)).build();
    }

    @Test
    void healthReturnsOk() throws Exception {
        mvc().perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void legacyGeneralPurposeGeminiSmokeEndpointIsNotExposed() throws Exception {
        mvc().perform(post("/api/gemini/smoke")).andExpect(status().isNotFound());
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
                .andExpect(jsonPath("$.routeId").value("NMC-SWM-GARBAGE-v0.2"))
                .andExpect(jsonPath("$.prabhagId").value("PRABHAG-01"))
                .andExpect(jsonPath("$.resolutionMethod").value("SELF_REPORTED"))
                .andExpect(jsonPath("$.authority").value("Nandurbar Municipal Council"))
                .andExpect(jsonPath("$.department.displayName").value("Health and Sanitation Department"))
                .andExpect(jsonPath("$.department.status").value("TYPICAL_STRUCTURE_UNVERIFIED"))
                .andExpect(jsonPath("$.officialChannels.length()").value(3))
                .andExpect(jsonPath("$.informationalLinks.length()").value(1))
                .andExpect(jsonPath("$.sourceStatus").value("OFFICIAL_SOURCE"))
                .andExpect(jsonPath("$.reviewStatus").value("REVIEW_PENDING"))
                .andExpect(jsonPath("$.packVersion").value("v0.2"));
    }

    @Test
    void publicAreaCleanlinessIsSupportedInV02() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("""
                                {"issueType":"PUBLIC_AREA_CLEANLINESS","prabhagId":"PRABHAG-05"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUPPORTED_ROUTE"))
                .andExpect(jsonPath("$.routeId").value("NMC-SWM-AREA-CLEAN-v0.2"))
                .andExpect(jsonPath("$.department.departmentId").value("AROGYA"))
                .andExpect(jsonPath("$.packVersion").value("v0.2"));
    }

    @Test
    void affectedRouteCarriesCitizenVisibleLimitation() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("""
                                {"issueType":"POTHOLE_ROAD_DAMAGE","prabhagId":"PRABHAG-03"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUPPORTED_ROUTE"))
                .andExpect(jsonPath("$.department.displayName").value("Public Works Department"))
                .andExpect(jsonPath("$.knownLimitations[0].code").value("ROAD_OWNERSHIP_UNKNOWN"))
                .andExpect(jsonPath("$.knownLimitations[0].requiresCitizenAttention").value(true));
    }

    @Test
    void bigQueryBoundaryIsOnlyACandidateAndCarriesTrustMetadata() throws Exception {
        mvc().perform(post("/api/civic/resolve-prabhag")
                        .contentType("application/json")
                        .content("{\"latitude\":21.363778,\"longitude\":74.2411418}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANDIDATE_PRABHAG"))
                .andExpect(jsonPath("$.prabhagId").value("PRABHAG-11"))
                .andExpect(jsonPath("$.resolutionMethod").value("BIGQUERY_ST_COVERS"))
                .andExpect(jsonPath("$.resolutionQuality").value("APPROXIMATE_DIGITISED_MUNICIPAL_OFFICE_MAP_IMAGE"))
                .andExpect(jsonPath("$.requiresCitizenConfirmation").value(true))
                .andExpect(jsonPath("$.sourceStatus").value("MUNICIPAL_OFFICE_WALL_MAP_PHOTO"))
                .andExpect(jsonPath("$.reviewStatus").value("NOT_AUTHORITY_VERIFIED"))
                .andExpect(jsonPath("$.datasetVersion").value("seewik-map-trace-v0.2"))
                .andExpect(jsonPath("$.queryLatencyMs").isNumber());
    }

    @Test
    void coordinatesOutsideNandurbarApproximateExtentAreRejected() throws Exception {
        mvc().perform(post("/api/civic/resolve-prabhag")
                        .contentType("application/json")
                        .content("{\"latitude\":20.9042,\"longitude\":74.7749}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUTSIDE_SUPPORTED_AREA"))
                .andExpect(jsonPath("$.prabhagId").doesNotExist())
                .andExpect(jsonPath("$.resolutionQuality").value("APPROXIMATE_DIGITISED_MUNICIPAL_OFFICE_MAP_IMAGE"));
    }

    @Test
    void missingCoordinatesAreRejectedBeforeLookup() throws Exception {
        mvc().perform(post("/api/civic/resolve-prabhag")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVALID_COORDINATES"))
                .andExpect(jsonPath("$.queryLatencyMs").doesNotExist());
    }

    @Test
    void approximateCandidateCannotRouteBeforeCitizenConfirmation() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("""
                                {"issueType":"STREETLIGHT","prabhagId":"PRABHAG-11",
                                 "resolutionMethod":"BIGQUERY_ST_COVERS","citizenConfirmed":false,
                                 "boundaryDatasetVersion":"seewik-map-trace-v0.2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.routeId").doesNotExist())
                .andExpect(jsonPath("$.citizenConfirmationRecorded").value(false));
    }

    @Test
    void confirmedApproximateCandidateCanReachDeterministicRouter() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("""
                                {"issueType":"STREETLIGHT","prabhagId":"PRABHAG-11",
                                 "resolutionMethod":"BIGQUERY_ST_COVERS","citizenConfirmed":true,
                                 "boundaryDatasetVersion":"seewik-map-trace-v0.2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUPPORTED_ROUTE"))
                .andExpect(jsonPath("$.resolutionMethod").value("CITIZEN_CONFIRMED_APPROXIMATE_BOUNDARY"))
                .andExpect(jsonPath("$.citizenConfirmationRecorded").value(true))
                .andExpect(jsonPath("$.boundaryDatasetVersion").value("seewik-map-trace-v0.2"))
                .andExpect(jsonPath("$.authority").value("Nandurbar Municipal Council"));
    }

    @Test
    void confirmedSnapshotCandidateUsesTheSameApproximateConfirmationGuard() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("""
                                {"issueType":"STREETLIGHT","prabhagId":"PRABHAG-11",
                                 "resolutionMethod":"SNAPSHOT_POINT_IN_POLYGON","citizenConfirmed":true,
                                 "boundaryDatasetVersion":"seewik-map-trace-v0.2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUPPORTED_ROUTE"))
                .andExpect(jsonPath("$.resolutionMethod").value("CITIZEN_CONFIRMED_APPROXIMATE_BOUNDARY"))
                .andExpect(jsonPath("$.citizenConfirmationRecorded").value(true));
    }

    @Test
    void unconfirmedSnapshotCandidateCannotRoute() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("""
                                {"issueType":"STREETLIGHT","prabhagId":"PRABHAG-11",
                                 "resolutionMethod":"SNAPSHOT_POINT_IN_POLYGON","citizenConfirmed":false,
                                 "boundaryDatasetVersion":"seewik-map-trace-v0.2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMATION_REQUIRED"));
    }

    @Test
    void staleApproximateCandidateMustBeResolvedAgain() throws Exception {
        mvc().perform(post("/api/civic/route")
                        .contentType("application/json")
                        .content("""
                                {"issueType":"STREETLIGHT","prabhagId":"PRABHAG-11",
                                 "resolutionMethod":"BIGQUERY_ST_COVERS","citizenConfirmed":true,
                                 "boundaryDatasetVersion":"seewik-map-trace-v0.1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMATION_REQUIRED"));
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
