package com.seewik.api;

public interface CitizenIdentityVerifier {
    AuthenticatedCitizen verifyBearer(String authorizationHeader);

    record AuthenticatedCitizen(String uid) {}

    final class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }

        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
