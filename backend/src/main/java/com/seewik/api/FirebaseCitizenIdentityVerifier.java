package com.seewik.api;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FirebaseCitizenIdentityVerifier implements CitizenIdentityVerifier {
    private static final String BEARER_PREFIX = "Bearer ";
    private final FirebaseAdminProvider firebase;

    public FirebaseCitizenIdentityVerifier(FirebaseAdminProvider firebase) {
        this.firebase = firebase;
    }

    @Override
    public AuthenticatedCitizen verifyBearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new AuthenticationException("A Firebase ID token is required");
        }
        String idToken = authorizationHeader.substring(BEARER_PREFIX.length()).strip();
        if (idToken.isEmpty()) throw new AuthenticationException("A Firebase ID token is required");
        try {
            FirebaseToken decoded = firebase.auth().verifyIdToken(idToken);
            if (decoded.getUid() == null || decoded.getUid().isBlank()) {
                throw new AuthenticationException("The Firebase ID token has no user identity");
            }
            return new AuthenticatedCitizen(decoded.getUid(), hasGoogleIdentity(decoded.getClaims()));
        } catch (FirebaseAuthException | IllegalArgumentException exception) {
            throw new AuthenticationException("The Firebase ID token is invalid or expired", exception);
        }
    }

    private static boolean hasGoogleIdentity(Map<String, Object> claims) {
        Object firebaseClaim = claims == null ? null : claims.get("firebase");
        if (!(firebaseClaim instanceof Map<?, ?> firebase)) return false;
        Object identitiesClaim = firebase.get("identities");
        if (!(identitiesClaim instanceof Map<?, ?> identities)) return false;
        Object googleIdentity = identities.get("google.com");
        if (googleIdentity instanceof Collection<?> values) return !values.isEmpty();
        if (googleIdentity != null && googleIdentity.getClass().isArray()) return Array.getLength(googleIdentity) > 0;
        return googleIdentity instanceof String value && !value.isBlank();
    }
}
