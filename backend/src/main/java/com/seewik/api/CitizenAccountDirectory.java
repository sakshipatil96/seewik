package com.seewik.api;

public interface CitizenAccountDirectory {
    GoogleIdentity googleIdentity(String ownerUid);

    record GoogleIdentity(String privateGoogleName, String privateGoogleEmail) {}
}
