package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.ImportUserRecord;
import com.google.firebase.auth.UserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionDay13RewardsIT {
    private static final String PROJECT_ID = "seewik";

    @Test
    void productionFirestoreClaimUseOwnershipAndCleanup() throws Exception {
        FirebaseAdminProvider firebase = new FirebaseAdminProvider(PROJECT_ID);
        FirestoreRecognitionGateway gateway = new FirestoreRecognitionGateway(firebase);
        RecognitionService service = new RecognitionService(gateway, null, Clock.systemUTC(), Set.of());
        String runId = UUID.randomUUID().toString().replace("-", "");
        String ownerUid = "day13owner" + runId.substring(0, 20);
        String otherUid = "day13other" + runId.substring(0, 20);
        try {
            importLinkedUser(firebase, ownerUid, "owner", runId);
            importLinkedUser(firebase, otherUid, "other", runId);
            seedAward(firebase, ownerUid, runId, "fix", "FIX_VERIFIED", 60);
            seedAward(firebase, ownerUid, runId, "organiser", "INITIATIVE_ORGANISER_COMPLETED_REWARDED", 40);

            assertEquals(100, service.rewardOverview(ownerUid).lifetimePoints());
            RecognitionService.RewardClaimResponse claim = service.claimReward(
                    ownerUid,
                    new RecognitionService.RewardClaimRequest("coupon-juthalal-100"));
            RecognitionService.RewardClaimResponse replay = service.claimReward(
                    ownerUid,
                    new RecognitionService.RewardClaimRequest("coupon-juthalal-100"));

            assertEquals("REWARD_CLAIM_CREATED", claim.status());
            assertEquals(claim.claimId(), replay.claimId());
            assertEquals(claim.code(), replay.code());
            assertTrue(claim.code().matches("SEE-[0-9A-F]{4}-[0-9A-F]{4}"));
            assertTrue(Duration.between(claim.claimedAt(), claim.expiresAt()).equals(Duration.ofDays(30)));
            assertEquals(1, claims(firebase, ownerUid).size());
            assertEquals(1, events(firebase, ownerUid).size());
            assertFalse(events(firebase, ownerUid).getFirst().getData().containsKey("code"));

            RecognitionService.RecognitionException forbidden = assertThrows(
                    RecognitionService.RecognitionException.class,
                    () -> service.useRewardClaim(otherUid, claim.claimId()));
            assertEquals("REWARD_CLAIM_FORBIDDEN", forbidden.code());

            RecognitionService.RewardClaimResponse used = service.useRewardClaim(ownerUid, claim.claimId());
            RecognitionService.RewardClaimResponse usedReplay = service.useRewardClaim(ownerUid, claim.claimId());
            assertEquals("USED", used.claimStatus());
            assertEquals(used.usedAt().toEpochMilli(), usedReplay.usedAt().toEpochMilli());
            assertEquals(2, events(firebase, ownerUid).size());
            assertEquals(100, service.rewardOverview(ownerUid).lifetimePoints());
        } finally {
            cleanupByOwner(firebase, "recognitionRewardEvents", ownerUid);
            cleanupByOwner(firebase, "recognitionRewardClaims", ownerUid);
            cleanupByOwner(firebase, "pointsLedger", ownerUid);
            deleteUserIfPresent(firebase, ownerUid);
            deleteUserIfPresent(firebase, otherUid);
            for (FirebaseApp app : FirebaseApp.getApps()) {
                if ("seewik-backend".equals(app.getName())) app.delete();
            }
        }
    }

    private static void importLinkedUser(
            FirebaseAdminProvider firebase,
            String uid,
            String role,
            String runId) throws Exception {
        String email = "day13-" + role + "-" + runId + "@example.invalid";
        ImportUserRecord record = ImportUserRecord.builder()
                .setUid(uid)
                .setEmail(email)
                .setEmailVerified(true)
                .addUserProvider(UserProvider.builder()
                        .setProviderId("google.com")
                        .setUid("day13-google-" + role + "-" + runId)
                        .setEmail(email)
                        .build())
                .build();
        assertEquals(0, firebase.auth().importUsers(List.of(record)).getFailureCount());
        assertTrue(java.util.Arrays.stream(firebase.auth().getUser(uid).getProviderData())
                .anyMatch(provider -> "google.com".equals(provider.getProviderId())));
    }

    private static void seedAward(
            FirebaseAdminProvider firebase,
            String ownerUid,
            String runId,
            String suffix,
            String reason,
            int points) throws Exception {
        String id = "day13-reward-" + suffix + "-" + runId;
        firebase.firestore().collection("pointsLedger").document(id).set(Map.ofEntries(
                Map.entry("ledgerEntryId", id),
                Map.entry("ownerUid", ownerUid),
                Map.entry("sourceType", "DAY13_PRODUCTION_TEST"),
                Map.entry("sourceId", "day13-source-" + suffix + "-" + runId),
                Map.entry("reason", reason),
                Map.entry("awardedPoints", points),
                Map.entry("policyStatus", "AWARDED"),
                Map.entry("occurredAt", Instant.now().toString()),
                Map.entry("schemaVersion", InitiativeService.LEDGER_SCHEMA_VERSION),
                Map.entry("rewardPolicyVersion", InitiativeService.REWARD_POLICY_VERSION),
                Map.entry("demoMode", false),
                Map.entry("testFixture", true))).get();
    }

    private static List<com.google.cloud.firestore.QueryDocumentSnapshot> claims(
            FirebaseAdminProvider firebase,
            String ownerUid) throws Exception {
        return firebase.firestore().collection("recognitionRewardClaims")
                .whereEqualTo("ownerUid", ownerUid).get().get().getDocuments();
    }

    private static List<com.google.cloud.firestore.QueryDocumentSnapshot> events(
            FirebaseAdminProvider firebase,
            String ownerUid) throws Exception {
        return firebase.firestore().collection("recognitionRewardEvents")
                .whereEqualTo("ownerUid", ownerUid).get().get().getDocuments();
    }

    private static void cleanupByOwner(
            FirebaseAdminProvider firebase,
            String collection,
            String ownerUid) throws Exception {
        for (var document : firebase.firestore().collection(collection)
                .whereEqualTo("ownerUid", ownerUid).get().get().getDocuments()) {
            document.getReference().delete().get();
        }
    }

    private static void deleteUserIfPresent(FirebaseAdminProvider firebase, String uid) throws Exception {
        try {
            assertNotNull(firebase.auth().getUser(uid));
            firebase.auth().deleteUser(uid);
        } catch (com.google.firebase.auth.FirebaseAuthException error) {
            if (!"user-not-found".equals(error.getAuthErrorCode().name().toLowerCase().replace('_', '-'))) throw error;
        }
    }
}
