package com.seewik.api;

import java.time.Instant;

public interface CitizenProfileGateway {
    PrivateProfile find(String ownerUid);

    PrivateProfile upsertPrivateIdentity(
            String ownerUid,
            String privateGoogleName,
            String privateGoogleEmail,
            Instant updatedAt);

    record PrivateProfile(
            String ownerUid,
            String privateGoogleName,
            String privateGoogleEmail,
            String schemaVersion,
            Instant updatedAt) {}
}
