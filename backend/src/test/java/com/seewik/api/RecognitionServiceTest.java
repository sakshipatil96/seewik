package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecognitionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-15T06:00:00Z");
    private FakeGateway gateway;
    private RecognitionService service;

    @BeforeEach
    void setUp() {
        gateway = new FakeGateway();
        CitizenProfileGateway profileGateway = new MemoryProfiles();
        CitizenProfileService profiles = new CitizenProfileService(
                uid -> new CitizenAccountDirectory.GoogleIdentity("Google " + uid, uid + "@example.com"),
                profileGateway,
                Clock.fixed(NOW, ZoneOffset.UTC));
        service = new RecognitionService(gateway, profiles, Clock.fixed(NOW, ZoneOffset.UTC), Set.of("test-owner"));
    }

    @Test
    void monthBoundaryUsesInclusiveStartAndExclusiveEndInIst() {
        var boundary = RecognitionService.monthBoundary(YearMonth.of(2026, 8));

        assertEquals(Instant.parse("2026-07-31T18:30:00Z"), boundary.start());
        assertEquals(Instant.parse("2026-08-31T18:30:00Z"), boundary.endExclusive());
        gateway.ledger.add(award("start", "a", "s1", "REPORT_FILED", 5, boundary.start()));
        gateway.ledger.add(award("end", "b", "s2", "REPORT_FILED", 5, boundary.endExclusive()));

        assertEquals(List.of("a"), service.validActiveAwards(gateway.ledger, boundary).stream()
                .map(RecognitionService.ValidAward::ownerUid).toList());
    }

    @Test
    void selectionUsesOnlyActiveDeduplicatedAwardsAndOptedInCitizens() {
        consent("one", "Zara", "OPTED_IN");
        consent("two", "Asha", "OPTED_IN");
        consent("three", "Meera", "OPTED_IN");
        consent("four", "Private Person", "PRIVATE");
        consent("test-owner", "Excluded Test", "OPTED_IN");
        gateway.ledger.add(award("1", "one", "report-1", "FIX_VERIFIED", 60, NOW));
        gateway.ledger.add(award("1-duplicate", "one", "report-1", "FIX_VERIFIED", 60, NOW));
        gateway.ledger.add(award("2", "two", "initiative-2", "INITIATIVE_ORGANISER_COMPLETED_REWARDED", 40, NOW));
        gateway.ledger.add(award("3", "three", "initiative-3", "INITIATIVE_ORGANISER_COMPLETED_REWARDED", 40, NOW));
        gateway.ledger.add(award("4", "four", "report-4", "FIX_VERIFIED", 60, NOW));
        gateway.ledger.add(award("5", "test-owner", "report-5", "FIX_VERIFIED", 60, NOW));
        Map<String, Object> demo = award("demo", "two", "demo", "FIX_VERIFIED", 60, NOW);
        demo.put("demoMode", true);
        gateway.ledger.add(demo);
        Map<String, Object> invalid = award("invalid", "two", "bad", "REPORT_FILED", 99, NOW);
        gateway.ledger.add(invalid);

        var response = service.publicPanel();

        assertEquals(List.of("Zara", "Asha", "Meera"), response.names());
        assertEquals(3, response.names().size());
        assertFalse(response.toString().contains("one"));
        assertFalse(response.toString().contains("60"));
    }

    @Test
    void equalTotalsUseAlphabeticalNamesAndPartialPanelsRemainHonest() {
        consent("b", "Bhavna", "OPTED_IN");
        consent("a", "Anita", "OPTED_IN");
        gateway.ledger.add(award("b1", "b", "source-b", "REPORT_FILED", 5, NOW));
        gateway.ledger.add(award("a1", "a", "source-a", "REPORT_FILED", 5, NOW));

        assertEquals(List.of("Anita", "Bhavna"), service.publicPanel().names());

        consent("b", "Bhavna", "WITHDRAWN");
        assertEquals(List.of("Anita"), service.publicPanel().names());
    }

    @Test
    void optInEditCollisionAndWithdrawalDoNotRewritePoints() {
        gateway.ledger.add(award("points", "owner", "source", "REPORT_FILED", 5, NOW));
        gateway.ledger.add(award("other", "other", "source-2", "REPORT_FILED", 5, NOW));
        consent("other", "Shared Name", "OPTED_IN");

        var optedIn = service.updateSettings("owner", new RecognitionService.RecognitionSettingsRequest("Shared Name", true));
        assertTrue(optedIn.recognitionActive());
        assertEquals(1, gateway.collisions.size());
        assertNotEquals("owner", gateway.collisions.getFirst().ownerUidHash());
        assertEquals(5, service.privatePoints("owner").lifetimePoints());

        var edited = service.updateSettings("owner", new RecognitionService.RecognitionSettingsRequest("New Public Name", true));
        assertEquals("New Public Name", edited.publicDisplayName());
        var withdrawn = service.updateSettings("owner", new RecognitionService.RecognitionSettingsRequest("x", false));
        assertEquals("WITHDRAWN", withdrawn.recognitionStatus());
        assertEquals("New Public Name", withdrawn.publicDisplayName());
        assertEquals(5, service.privatePoints("owner").lifetimePoints());
        assertFalse(service.publicPanel().names().contains("New Public Name"));
    }

    @Test
    void identicalCalculationIsIdempotentButConsentChangesReplaceTheSnapshot() {
        consent("owner", "Citizen Name", "OPTED_IN");
        gateway.ledger.add(award("entry", "owner", "source", "REPORT_FILED", 5, NOW));

        service.publicPanel();
        service.publicPanel();
        assertEquals(1, gateway.snapshotWrites);

        consent("owner", "Changed Name", "OPTED_IN");
        service.publicPanel();
        assertEquals(2, gateway.snapshotWrites);
    }

    @Test
    void privateSummaryKeepsLegitimateHistoryButCurrentMonthUsesActivePolicy() {
        gateway.ledger.add(award("current", "owner", "source-1", "REPORT_FILED", 5, NOW));
        Map<String, Object> historical = award("historical", "owner", "source-2", "LEGACY_REWARD", 7,
                Instant.parse("2025-04-01T00:00:00Z"));
        historical.put("schemaVersion", "points-ledger-v0.1");
        historical.put("rewardPolicyVersion", "reward-policy-v0.1");
        gateway.ledger.add(historical);

        var response = service.privatePoints("owner");

        assertEquals(12, response.lifetimePoints());
        assertEquals(5, response.currentMonthPoints());
        assertEquals("August 2026", response.monthLabel());
    }

    @Test
    void unsafeDisplayNamesAreRejectedAndNamesAreNotForcedUnique() {
        var defaults = service.settings("new-owner");
        assertEquals("PRIVATE", defaults.recognitionStatus());
        assertFalse(defaults.recognitionActive());
        assertEquals("Google new-owner", defaults.publicDisplayName());

        assertEquals("DISPLAY_NAME_CONTACT_DETAILS", assertThrows(
                RecognitionService.RecognitionException.class,
                () -> RecognitionService.validateDisplayName("Citizen 9876543210")).code());
        assertEquals("DISPLAY_NAME_RESERVED_TITLE", assertThrows(
                RecognitionService.RecognitionException.class,
                () -> RecognitionService.validateDisplayName("Nagar Parishad Officer")).code());
        assertEquals("Citizen Name", RecognitionService.validateDisplayName("  Citizen   Name "));
    }

    @Test
    void displayedNameReportsResolveServerSideAndStoreOnlyHashes() {
        consent("target", "Displayed Citizen", "OPTED_IN");
        gateway.ledger.add(award("entry", "target", "source", "REPORT_FILED", 5, NOW));

        service.reportDisplayedName("reporter", new RecognitionService.AbuseReportRequest(
                0, "Displayed Citizen", "IMPERSONATION", "Please review"));

        assertEquals(64, gateway.abuseReport.reporterUidHash().length());
        assertEquals(64, gateway.abuseReport.targetOwnerUidHash().length());
        assertNotEquals("reporter", gateway.abuseReport.reporterUidHash());
        assertNotEquals("target", gateway.abuseReport.targetOwnerUidHash());
        assertNotEquals("Displayed Citizen", gateway.abuseReport.targetDisplayNameHash());

        assertEquals("RECOGNITION_TARGET_UNAVAILABLE", assertThrows(
                RecognitionService.RecognitionException.class,
                () -> service.reportDisplayedName("reporter", new RecognitionService.AbuseReportRequest(
                        0, "A different name", "IMPERSONATION", "Please review"))).code());
    }

    private void consent(String uid, String name, String status) {
        gateway.consents.put(uid, new RecognitionGateway.Consent(
                uid, name, RecognitionService.normalizedName(name), status,
                "OPTED_IN".equals(status) ? NOW : null,
                "WITHDRAWN".equals(status) ? NOW : null,
                NOW,
                RecognitionService.CONSENT_SCHEMA_VERSION));
    }

    private static Map<String, Object> award(
            String id, String uid, String source, String reason, int points, Instant occurredAt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ledgerEntryId", id);
        result.put("ownerUid", uid);
        result.put("sourceId", source);
        result.put("reason", reason);
        result.put("awardedPoints", points);
        result.put("policyStatus", "AWARDED");
        result.put("schemaVersion", InitiativeService.LEDGER_SCHEMA_VERSION);
        result.put("rewardPolicyVersion", InitiativeService.REWARD_POLICY_VERSION);
        result.put("occurredAt", occurredAt);
        result.put("demoMode", false);
        return result;
    }

    private static final class MemoryProfiles implements CitizenProfileGateway {
        private final Map<String, PrivateProfile> profiles = new LinkedHashMap<>();

        @Override
        public PrivateProfile find(String ownerUid) {
            return profiles.get(ownerUid);
        }

        @Override
        public PrivateProfile upsertPrivateIdentity(
                String ownerUid, String privateGoogleName, String privateGoogleEmail, Instant updatedAt) {
            PrivateProfile profile = new PrivateProfile(
                    ownerUid, privateGoogleName, privateGoogleEmail,
                    CitizenProfileService.PROFILE_SCHEMA_VERSION, updatedAt);
            profiles.put(ownerUid, profile);
            return profile;
        }
    }

    private static final class FakeGateway implements RecognitionGateway {
        private final List<Map<String, Object>> ledger = new ArrayList<>();
        private final Map<String, Consent> consents = new LinkedHashMap<>();
        private final List<NameCollisionEvent> collisions = new ArrayList<>();
        private MonthSnapshot snapshot;
        private int snapshotWrites;
        private AbuseReport abuseReport;

        @Override public Consent findConsent(String ownerUid) { return consents.get(ownerUid); }
        @Override public List<Consent> activeConsents() { return consents.values().stream().filter(item -> "OPTED_IN".equals(item.status())).toList(); }
        @Override public Consent saveConsent(Consent consent, ConsentEvent event) { consents.put(consent.ownerUid(), consent); return consent; }
        @Override public List<String> collidingOwnerUids(String normalizedDisplayName, String excludingOwnerUid) {
            return consents.values().stream()
                    .filter(item -> item.normalizedDisplayName().equals(normalizedDisplayName))
                    .map(Consent::ownerUid).filter(uid -> !uid.equals(excludingOwnerUid)).toList();
        }
        @Override public void recordNameCollision(NameCollisionEvent event) { collisions.add(event); }
        @Override public List<Map<String, Object>> awardedLedgerEntries() { return List.copyOf(ledger); }
        @Override public List<Map<String, Object>> ownerLedgerEntries(String ownerUid) {
            return ledger.stream().filter(item -> ownerUid.equals(item.get("ownerUid"))).toList();
        }
        @Override public MonthSnapshot findMonthSnapshot(String monthKey) { return snapshot; }
        @Override public boolean saveMonthSnapshotIfChanged(MonthSnapshot candidate) {
            if (snapshot != null && snapshot.contentHash().equals(candidate.contentHash())) return false;
            snapshot = candidate;
            snapshotWrites++;
            return true;
        }
        @Override public void recordAbuseReport(AbuseReport report) { abuseReport = report; }
    }
}
