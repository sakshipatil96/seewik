package com.seewik.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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

    @Test
    void rewardMutationRequiresGoogleLinking() throws Exception {
        mvc(header -> new CitizenIdentityVerifier.AuthenticatedCitizen("anonymous", false))
                .perform(post("/api/recognition/me/rewards/claims")
                        .contentType("application/json")
                        .content("""
                                {"couponId":"coupon-juthalal-100"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("GOOGLE_LINK_REQUIRED"));
    }

    @Test
    void linkedRewardMutationUsesTheRewardRateLimitBoundary() throws Exception {
        AtomicReference<String> protectedEndpoint = new AtomicReference<>();
        MockMvc mvc = mvc(
                header -> new CitizenIdentityVerifier.AuthenticatedCitizen("linked-owner", true),
                (uid, endpoint) -> protectedEndpoint.set(uid + ":" + endpoint));

        mvc.perform(post("/api/recognition/me/rewards/claims")
                        .contentType("application/json")
                        .content("""
                                {"couponId":"coupon-juthalal-100"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimStatus").value("CLAIMED"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "linked-owner:" + PaidEndpointRateLimiter.REWARD_CLAIMS,
                protectedEndpoint.get());
    }

    private static MockMvc mvc(CitizenIdentityVerifier verifier) {
        return mvc(verifier, (uid, endpoint) -> {});
    }

    private static MockMvc mvc(CitizenIdentityVerifier verifier, PaidEndpointRateLimiter rateLimiter) {
        return MockMvcBuilders.standaloneSetup(new RecognitionController(
                verifier, new StubRecognitionService(), rateLimiter)).build();
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

        @Override
        public RewardClaimResponse claimReward(String ownerUid, RewardClaimRequest request) {
            Instant now = Instant.parse("2026-09-01T00:00:00Z");
            return new RewardClaimResponse(
                    "REWARD_CLAIM_CREATED",
                    "reward-claim-test",
                    request.couponId(),
                    "business-juthalal",
                    "SEE-TEST-CODE",
                    now,
                    now.plusSeconds(30L * 24 * 60 * 60),
                    null,
                    "CLAIMED",
                    COUPON_CLAIM_SCHEMA_VERSION,
                    REWARD_TIER_SCHEMA_VERSION);
        }
    }
}
