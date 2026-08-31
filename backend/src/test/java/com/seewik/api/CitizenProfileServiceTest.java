package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CitizenProfileServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");

    @Test
    void syncMigratesTheExistingUidAndStoresOnlyPrivateGoogleIdentity() {
        FakeProfiles profiles = new FakeProfiles();
        CitizenProfileService service = new CitizenProfileService(
                uid -> new CitizenAccountDirectory.GoogleIdentity("  Sakshi   Patil ", "sakshi@example.com"),
                profiles,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var response = service.sync("existing-uid");

        assertEquals("existing-uid", profiles.saved.ownerUid());
        assertEquals("Sakshi Patil", response.privateGoogleName());
        assertEquals("sakshi@example.com", response.privateGoogleEmail());
        assertEquals("citizen-profile-v0.2", response.schemaVersion());
        assertEquals(NOW, profiles.saved.updatedAt());
    }

    @Test
    void oldProfilesAreMigratedInPlaceAndCurrentProfilesAreReadWithoutRewrite() {
        FakeProfiles profiles = new FakeProfiles();
        profiles.byUid.put("old", new CitizenProfileGateway.PrivateProfile(
                "old", "", "", "citizen-profile-v0.1", Instant.EPOCH));
        profiles.byUid.put("current", new CitizenProfileGateway.PrivateProfile(
                "current", "Current Name", "current@example.com", "citizen-profile-v0.2", NOW));
        CitizenProfileService service = new CitizenProfileService(
                uid -> new CitizenAccountDirectory.GoogleIdentity("Migrated Name", uid + "@example.com"),
                profiles,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals("Migrated Name", service.get("old").privateGoogleName());
        int writesAfterMigration = profiles.writes;
        assertEquals("Current Name", service.get("current").privateGoogleName());
        assertEquals(writesAfterMigration, profiles.writes);
    }

    @Test
    void aVerifiedPrivateEmailIsRequired() {
        CitizenProfileService service = new CitizenProfileService(
                uid -> new CitizenAccountDirectory.GoogleIdentity("Name", ""),
                new FakeProfiles(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var error = assertThrows(CitizenProfileService.ProfileException.class, () -> service.sync("uid"));
        assertEquals("PROFILE_EMAIL_UNAVAILABLE", error.code());
    }

    private static final class FakeProfiles implements CitizenProfileGateway {
        private final Map<String, PrivateProfile> byUid = new LinkedHashMap<>();
        private PrivateProfile saved;
        private int writes;

        @Override
        public PrivateProfile find(String ownerUid) {
            return byUid.get(ownerUid);
        }

        @Override
        public PrivateProfile upsertPrivateIdentity(
                String ownerUid, String privateGoogleName, String privateGoogleEmail, Instant updatedAt) {
            saved = new PrivateProfile(
                    ownerUid, privateGoogleName, privateGoogleEmail,
                    CitizenProfileService.PROFILE_SCHEMA_VERSION, updatedAt);
            byUid.put(ownerUid, saved);
            writes++;
            return saved;
        }
    }
}
