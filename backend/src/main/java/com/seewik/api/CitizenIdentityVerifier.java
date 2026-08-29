package com.seewik.api;

public interface CitizenIdentityVerifier {
    AuthenticatedCitizen verifyBearer(String authorizationHeader);

    static AuthenticatedCitizen requireGoogleLinked(AuthenticatedCitizen citizen) {
        if (citizen == null || !citizen.googleLinked()) {
            throw new LinkedIdentityRequiredException(
                    "Connect Google before making this change. Existing saved work remains available.");
        }
        return citizen;
    }

    record AuthenticatedCitizen(String uid, boolean googleLinked) {
        public AuthenticatedCitizen(String uid) {
            this(uid, true);
        }
    }

    final class LinkedIdentityRequiredException extends RuntimeException {
        public LinkedIdentityRequiredException(String message) {
            super(message);
        }
    }

    final class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }

        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
