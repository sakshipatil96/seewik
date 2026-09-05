package com.seewik.api;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FirebaseAdminProvider {
    private static final String APP_NAME = "seewik-backend";
    private final String projectId;
    private volatile FirebaseApp app;

    public FirebaseAdminProvider(@Value("${seewik.firebase.project-id:seewik}") String projectId) {
        this.projectId = projectId;
    }

    public FirebaseAuth auth() {
        return FirebaseAuth.getInstance(app());
    }

    public Firestore firestore() {
        return FirestoreClient.getFirestore(app());
    }

    private FirebaseApp app() {
        FirebaseApp current = app;
        if (current != null) return current;
        synchronized (this) {
            if (app == null) app = findOrInitialize();
            return app;
        }
    }

    private FirebaseApp findOrInitialize() {
        for (FirebaseApp candidate : FirebaseApp.getApps()) {
            if (APP_NAME.equals(candidate.getName())) return candidate;
        }
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials())
                    .setProjectId(projectId)
                    .build();
            return FirebaseApp.initializeApp(options, APP_NAME);
        } catch (IOException exception) {
            throw new IllegalStateException("Firebase Admin credentials are unavailable", exception);
        }
    }

    private static GoogleCredentials credentials() throws IOException {
        boolean emulatorMode = present(System.getenv("FIRESTORE_EMULATOR_HOST"))
                && present(System.getenv("FIREBASE_AUTH_EMULATOR_HOST"));
        if (emulatorMode) {
            return GoogleCredentials.create(new AccessToken(
                    "local-e2e-emulator-owner",
                    Date.from(Instant.now().plusSeconds(86_400))));
        }
        return GoogleCredentials.getApplicationDefault();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
