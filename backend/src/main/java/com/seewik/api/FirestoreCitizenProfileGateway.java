package com.seewik.api;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Component;

@Component
public class FirestoreCitizenProfileGateway implements CitizenProfileGateway {
    private final FirebaseAdminProvider firebase;

    public FirestoreCitizenProfileGateway(FirebaseAdminProvider firebase) {
        this.firebase = firebase;
    }

    @Override
    public PrivateProfile find(String ownerUid) {
        try {
            DocumentSnapshot document = firebase.firestore().collection("profiles").document(ownerUid).get().get();
            if (!document.exists()) return null;
            return profile(document, ownerUid);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("The private profile could not be loaded", exception);
        } catch (ExecutionException exception) {
            throw failure("The private profile could not be loaded", exception.getCause());
        }
    }

    @Override
    public PrivateProfile upsertPrivateIdentity(
            String ownerUid,
            String privateGoogleName,
            String privateGoogleEmail,
            Instant updatedAt) {
        Firestore store = firebase.firestore();
        var reference = store.collection("profiles").document(ownerUid);
        try {
            store.runTransaction(transaction -> {
                DocumentSnapshot existing = transaction.get(reference).get();
                Map<String, Object> updates = new LinkedHashMap<>();
                updates.put("ownerUid", ownerUid);
                updates.put("authProvider", "GOOGLE");
                updates.put("recoverable", true);
                updates.put("privateGoogleName", privateGoogleName);
                updates.put("privateGoogleEmail", privateGoogleEmail);
                updates.put("schemaVersion", CitizenProfileService.PROFILE_SCHEMA_VERSION);
                updates.put("updatedAt", FieldValue.serverTimestamp());
                if (!existing.exists() || !existing.contains("createdAt")) {
                    updates.put("createdAt", FieldValue.serverTimestamp());
                }
                transaction.set(reference, updates, com.google.cloud.firestore.SetOptions.merge());
                return null;
            }).get();
            return new PrivateProfile(
                    ownerUid,
                    privateGoogleName,
                    privateGoogleEmail,
                    CitizenProfileService.PROFILE_SCHEMA_VERSION,
                    updatedAt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("The private profile could not be saved", exception);
        } catch (ExecutionException exception) {
            throw failure("The private profile could not be saved", exception.getCause());
        }
    }

    private static PrivateProfile profile(DocumentSnapshot document, String ownerUid) {
        Instant updatedAt = document.getTimestamp("updatedAt") == null
                ? Instant.EPOCH
                : document.getTimestamp("updatedAt").toDate().toInstant();
        return new PrivateProfile(
                ownerUid,
                clean(document.getString("privateGoogleName")),
                clean(document.getString("privateGoogleEmail")),
                clean(document.getString("schemaVersion")),
                updatedAt);
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }

    private static CitizenProfileService.ProfileException failure(String message, Throwable cause) {
        return new CitizenProfileService.ProfileException("PROFILE_STORE_FAILED", message, cause);
    }
}
