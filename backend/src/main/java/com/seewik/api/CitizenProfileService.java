package com.seewik.api;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CitizenProfileService {
    static final String PROFILE_SCHEMA_VERSION = "citizen-profile-v0.2";

    private final CitizenAccountDirectory accounts;
    private final CitizenProfileGateway profiles;
    private final Clock clock;

    @Autowired
    public CitizenProfileService(CitizenAccountDirectory accounts, CitizenProfileGateway profiles) {
        this(accounts, profiles, Clock.systemUTC());
    }

    CitizenProfileService(
            CitizenAccountDirectory accounts,
            CitizenProfileGateway profiles,
            Clock clock) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.clock = clock;
    }

    public PrivateProfileResponse sync(String ownerUid) {
        CitizenAccountDirectory.GoogleIdentity identity = accounts.googleIdentity(ownerUid);
        if (identity.privateGoogleEmail() == null || identity.privateGoogleEmail().isBlank()) {
            throw new ProfileException(
                    "PROFILE_EMAIL_UNAVAILABLE",
                    "Google did not provide the private account email required for this profile");
        }
        CitizenProfileGateway.PrivateProfile saved = profiles.upsertPrivateIdentity(
                ownerUid,
                clean(identity.privateGoogleName()),
                identity.privateGoogleEmail().strip(),
                clock.instant());
        return response(saved);
    }

    public PrivateProfileResponse get(String ownerUid) {
        CitizenProfileGateway.PrivateProfile profile = profiles.find(ownerUid);
        if (profile == null
                || !PROFILE_SCHEMA_VERSION.equals(profile.schemaVersion())
                || profile.privateGoogleEmail() == null
                || profile.privateGoogleEmail().isBlank()) return sync(ownerUid);
        return response(profile);
    }

    private static PrivateProfileResponse response(CitizenProfileGateway.PrivateProfile profile) {
        return new PrivateProfileResponse(
                "PRIVATE_PROFILE_READY",
                profile.privateGoogleName(),
                profile.privateGoogleEmail(),
                profile.schemaVersion());
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    public record PrivateProfileResponse(
            String status,
            String privateGoogleName,
            String privateGoogleEmail,
            String schemaVersion) {}

    public static final class ProfileException extends RuntimeException {
        private final String code;

        public ProfileException(String code, String message) {
            super(message);
            this.code = code;
        }

        public ProfileException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
