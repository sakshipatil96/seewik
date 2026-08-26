package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
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

        var citizen = new FirebaseCitizenIdentityVerifier(firebase).verifyBearer("Bearer valid-token");
        assertEquals("owner-1", citizen.uid());
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
