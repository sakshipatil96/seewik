package com.seewik.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecognitionControllerTest {
    @Test
    void publicPanelNeedsNoSignInAndReturnsNamesWithoutPrivateSelectionData() throws Exception {
        mvc(header -> { throw new CitizenIdentityVerifier.AuthenticationException("not used"); })
                .perform(get("/api/recognition/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names[0]").value("Asha"))
                .andExpect(jsonPath("$.uid").doesNotExist())
                .andExpect(jsonPath("$.points").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.selectedCitizens").doesNotExist());
    }

    @Test
    void privatePointsRequireAuthenticationAndSettingsRequireGoogleLinking() throws Exception {
        mvc(header -> { throw new CitizenIdentityVerifier.AuthenticationException("Sign in"); })
                .perform(get("/api/recognition/me/points"))
                .andExpect(status().isUnauthorized());

        mvc(header -> new CitizenIdentityVerifier.AuthenticatedCitizen("anonymous", false))
                .perform(get("/api/recognition/me/settings"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("GOOGLE_LINK_REQUIRED"));
    }

    @Test
    void reportingANameRequiresAGoogleLinkedCitizen() throws Exception {
        mvc(header -> new CitizenIdentityVerifier.AuthenticatedCitizen("anonymous", false))
                .perform(post("/api/recognition/reports")
                        .contentType("application/json")
                        .content("""
                                {"targetPosition":0,"targetDisplayName":"Asha","reason":"IMPERSONATION","details":"Review"}
                                """))
                .andExpect(status().isForbidden());
    }

    private static MockMvc mvc(CitizenIdentityVerifier verifier) {
        return MockMvcBuilders.standaloneSetup(new RecognitionController(verifier, new StubRecognitionService())).build();
    }

    private static final class StubRecognitionService extends RecognitionService {
        private StubRecognitionService() {
            super(null, null, Clock.systemUTC(), Set.of());
        }

        @Override
        public PublicPanelResponse publicPanel() {
            return new PublicPanelResponse(
                    "RECOGNITION_READY", "2026-08", "August 2026", List.of("Asha"),
                    "A Seewik thank-you.", RECOGNITION_SCHEMA_VERSION);
        }
    }
}
