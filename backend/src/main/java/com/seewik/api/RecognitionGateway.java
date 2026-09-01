package com.seewik.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface RecognitionGateway {
    Consent findConsent(String ownerUid);

    List<Consent> activeConsents();

    Consent saveConsent(Consent consent, ConsentEvent event);

    List<String> collidingOwnerUids(String normalizedDisplayName, String excludingOwnerUid);

    void recordNameCollision(NameCollisionEvent event);

    List<Map<String, Object>> awardedLedgerEntries();

    List<Map<String, Object>> ownerLedgerEntries(String ownerUid);

    List<RewardClaim> ownerRewardClaims(String ownerUid);

    RewardClaim findRewardClaim(String claimId);

    RewardClaim createRewardClaim(RewardClaim claim, RewardClaimEvent event);

    RewardClaim transitionRewardClaim(
            String claimId,
            String ownerUid,
            String expectedStatus,
            RewardClaim updated,
            RewardClaimEvent event);

    MonthSnapshot findMonthSnapshot(String monthKey);

    boolean saveMonthSnapshotIfChanged(MonthSnapshot snapshot);

    void recordAbuseReport(AbuseReport report);

    record Consent(
            String ownerUid,
            String publicDisplayName,
            String normalizedDisplayName,
            String status,
            Instant consentedAt,
            Instant withdrawnAt,
            Instant updatedAt,
            String schemaVersion) {}

    record ConsentEvent(
            String eventId,
            String ownerUid,
            String eventType,
            String publicDisplayNameHash,
            Instant occurredAt,
            String schemaVersion) {}

    record NameCollisionEvent(
            String eventId,
            String ownerUidHash,
            List<String> collidingOwnerUidHashes,
            String normalizedDisplayNameHash,
            Instant occurredAt,
            String schemaVersion) {}

    record SelectedCitizen(
            String ownerUid,
            String publicDisplayName,
            int monthlyPoints) {}

    record MonthSnapshot(
            String monthKey,
            Instant monthStart,
            Instant monthEndExclusive,
            List<SelectedCitizen> selectedCitizens,
            int candidateCount,
            String contentHash,
            Instant generatedAt,
            String schemaVersion,
            String rewardPolicyVersion) {}

    record AbuseReport(
            String reportId,
            String reporterUidHash,
            String targetOwnerUidHash,
            String targetDisplayNameHash,
            String monthKey,
            int targetPosition,
            String reason,
            String details,
            Instant occurredAt,
            String schemaVersion) {}

    record RewardBusiness(
            String businessId,
            String displayName,
            String category,
            String prabhag,
            boolean isExample,
            String statusLabel,
            String schemaVersion) {}

    record RewardCoupon(
            String couponId,
            String businessId,
            String publicDescription,
            int tierRequired,
            String status,
            boolean isExample,
            String schemaVersion) {}

    record RewardClaim(
            String claimId,
            String ownerUid,
            String couponId,
            String businessId,
            int tierRequired,
            String code,
            Instant claimedAt,
            Instant expiresAt,
            Instant usedAt,
            String claimStatus,
            String schemaVersion,
            String contractVersion) {}

    record RewardClaimEvent(
            String eventId,
            String claimId,
            String ownerUid,
            String eventType,
            String couponId,
            Instant occurredAt,
            String schemaVersion) {}
}
