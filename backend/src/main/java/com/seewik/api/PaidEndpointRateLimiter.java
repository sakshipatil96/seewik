package com.seewik.api;

public interface PaidEndpointRateLimiter {
    String CLASSIFICATION = "classification";
    String DRAFTING = "drafting";

    void check(String uid, String endpoint);

    final class RateLimitedException extends RuntimeException {
        private final String scope;
        private final long retryAfterSeconds;

        RateLimitedException(String scope, long retryAfterSeconds) {
            super("The request limit has been reached");
            this.scope = scope;
            this.retryAfterSeconds = Math.max(1L, Math.min(3_600L, retryAfterSeconds));
        }

        public String scope() {
            return scope;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    final class LimiterUnavailableException extends RuntimeException {
        LimiterUnavailableException(Throwable cause) {
            super("Paid request protection is temporarily unavailable", cause);
        }
    }
}
