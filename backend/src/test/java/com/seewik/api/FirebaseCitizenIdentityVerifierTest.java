package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FirebaseCitizenIdentityVerifierTest {
    @Test
    void missingBearerTokenIsRejectedBeforeFirebaseIsInitialized() {
        FirebaseAdminProvider firebase = mock(FirebaseAdminProvider.class);
        var verifier = new FirebaseCitizenIdentityVerifier(firebase);
        assertThrows(
                CitizenIdentityVerifier.AuthenticationException.class,
                () -> verifier.verifyBearer(null));
        verify(firebase, never()).auth();
    }

    @Test
    void verifiedFirebaseTokenReturnsItsUid() throws Exception {
        FirebaseAdminProvider firebase = mock(FirebaseAdminProvider.class);
        FirebaseAuth auth = mock(FirebaseAuth.class);
        FirebaseToken token = mock(FirebaseToken.class);
        when(firebase.auth()).thenReturn(auth);
        when(auth.verifyIdToken("valid-token")).thenReturn(token);
        when(token.getUid()).thenReturn("owner-1");
        when(token.getClaims()).thenReturn(Map.of());

        var citizen = new FirebaseCitizenIdentityVerifier(firebase).verifyBearer("Bearer valid-token");
        assertEquals("owner-1", citizen.uid());
        assertFalse(citizen.googleLinked());
    }

    @Test
    void linkedGoogleIdentityIsReadFromFirebaseIdentitiesClaim() throws Exception {
        FirebaseAdminProvider firebase = mock(FirebaseAdminProvider.class);
        FirebaseAuth auth = mock(FirebaseAuth.class);
        FirebaseToken token = mock(FirebaseToken.class);
        when(firebase.auth()).thenReturn(auth);
        when(auth.verifyIdToken("linked-token")).thenReturn(token);
        when(token.getUid()).thenReturn("owner-linked");
        when(token.getClaims()).thenReturn(Map.of(
                "firebase", Map.of("identities", Map.of("google.com", List.of("google-subject")))));

        var citizen = new FirebaseCitizenIdentityVerifier(firebase).verifyBearer("Bearer linked-token");
        assertEquals("owner-linked", citizen.uid());
        assertTrue(citizen.googleLinked());
    }

    @Test
    void anonymousIdentityCannotCrossLinkedWriteBoundary() {
        var citizen = new CitizenIdentityVerifier.AuthenticatedCitizen("anonymous-owner", false);
        assertThrows(
                CitizenIdentityVerifier.LinkedIdentityRequiredException.class,
                () -> CitizenIdentityVerifier.requireGoogleLinked(citizen));
    }

    @Test
    void tokenWithoutAUidIsRejected() throws Exception {
        FirebaseAdminProvider firebase = mock(FirebaseAdminProvider.class);
        FirebaseAuth auth = mock(FirebaseAuth.class);
        FirebaseToken token = mock(FirebaseToken.class);
        when(firebase.auth()).thenReturn(auth);
        when(auth.verifyIdToken("valid-token")).thenReturn(token);
        when(token.getUid()).thenReturn(" ");

        assertThrows(
                CitizenIdentityVerifier.AuthenticationException.class,
                () -> new FirebaseCitizenIdentityVerifier(firebase).verifyBearer("Bearer valid-token"));
    }
}
