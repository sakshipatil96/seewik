package com.seewik.api;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.springframework.stereotype.Component;

@Component
public class FirebaseCitizenAccountDirectory implements CitizenAccountDirectory {
    private final FirebaseAdminProvider firebase;

    public FirebaseCitizenAccountDirectory(FirebaseAdminProvider firebase) {
        this.firebase = firebase;
    }

    @Override
    public GoogleIdentity googleIdentity(String ownerUid) {
        try {
            UserRecord user = firebase.auth().getUser(ownerUid);
            return new GoogleIdentity(clean(user.getDisplayName()), clean(user.getEmail()));
        } catch (FirebaseAuthException exception) {
            throw new CitizenProfileService.ProfileException(
                    "PROFILE_IDENTITY_UNAVAILABLE",
                    "The private Google account details could not be verified",
                    exception);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
