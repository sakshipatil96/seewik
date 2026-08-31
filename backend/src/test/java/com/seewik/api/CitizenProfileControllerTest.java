package com.seewik.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CitizenProfileControllerTest {
    @Test
    void profileSyncRequiresGoogleLinking() throws Exception {
        mvc(header -> new CitizenIdentityVerifier.AuthenticatedCitizen("anonymous", false))
                .perform(post("/api/profile/sync"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("GOOGLE_LINK_REQUIRED"));
    }

    @Test
    void linkedOwnerReceivesOnlyTheirPrivateAccountIdentity() throws Exception {
        mvc(header -> new CitizenIdentityVerifier.AuthenticatedCitizen("owner", true))
                .perform(post("/api/profile/sync").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privateGoogleName").value("Private Name"))
                .andExpect(jsonPath("$.privateGoogleEmail").value("private@example.com"))
                .andExpect(jsonPath("$.photoURL").doesNotExist());
    }

    private static MockMvc mvc(CitizenIdentityVerifier verifier) {
        return MockMvcBuilders.standaloneSetup(new CitizenProfileController(verifier, new StubProfiles())).build();
    }

    private static final class StubProfiles extends CitizenProfileService {
        private StubProfiles() {
            super(uid -> null, null, Clock.systemUTC());
        }

        @Override
        public PrivateProfileResponse sync(String ownerUid) {
            return new PrivateProfileResponse(
                    "PRIVATE_PROFILE_READY", "Private Name", "private@example.com", PROFILE_SCHEMA_VERSION);
        }
    }
}
