package com.seewik.api;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class FirestorePaidEndpointRateLimiter implements PaidEndpointRateLimiter {
    private static final String COLLECTION = "operationalRateLimitsV1";
    private static final Set<String> ENDPOINTS = Set.of(CLASSIFICATION, DRAFTING, REWARD_CLAIMS);
    private final FirebaseAdminProvider firebase;
    private final Clock clock;

    @Autowired
    public FirestorePaidEndpointRateLimiter(FirebaseAdminProvider firebase) {
        this(firebase, Clock.systemUTC());
    }

    FirestorePaidEndpointRateLimiter(FirebaseAdminProvider firebase, Clock clock) {
        this.firebase = firebase;
        this.clock = clock;
    }

    @Override
    public void check(String uid, String endpoint) {
        if (uid == null || uid.isBlank() || !ENDPOINTS.contains(endpoint)) {
            throw new IllegalArgumentException("A verified user and protected endpoint are required");
        }
        long nowMs = clock.millis();
        Firestore store = firebase.firestore();
        DocumentReference userRef = store.collection(COLLECTION)
                .document("user-" + endpoint + "-" + hashUid(uid));
        DocumentReference globalRef = store.collection(COLLECTION).document(globalDocumentId(endpoint));
        try {
            store.runTransaction(transaction -> {
                DocumentSnapshot user = transaction.get(userRef).get();
                DocumentSnapshot global = transaction.get(globalRef).get();
                RateLimitPolicy.Evaluation userEvaluation = RateLimitPolicy.evaluate(
                        events(user), nowMs, RateLimitPolicy.USER_LIMITS);
                if (!userEvaluation.allowed()) {
                    throw new RateLimitedException("per_user", userEvaluation.retryAfterSeconds());
                }
                RateLimitPolicy.Evaluation globalEvaluation = RateLimitPolicy.evaluate(
                        events(global), nowMs, RateLimitPolicy.GLOBAL_LIMITS);
                if (!globalEvaluation.allowed()) {
                    throw new RateLimitedException("global", globalEvaluation.retryAfterSeconds());
                }
                Date expiry = Date.from(Instant.ofEpochMilli(nowMs).plus(Duration.ofHours(2)));
                transaction.set(userRef, Map.of(
                        "endpoint", endpoint,
                        "eventEpochMs", userEvaluation.activeEvents(),
                        "updatedAtEpochMs", nowMs,
                        "expiresAt", expiry));
                transaction.set(globalRef, Map.of(
                        "scope", "project",
                        "eventEpochMs", globalEvaluation.activeEvents(),
                        "updatedAtEpochMs", nowMs,
                        "expiresAt", expiry));
                return null;
            }).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LimiterUnavailableException(exception);
        } catch (ExecutionException exception) {
            Throwable cause = unwrap(exception);
            if (cause instanceof RateLimitedException rateLimited) throw rateLimited;
            throw new LimiterUnavailableException(cause);
        } catch (RateLimitedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LimiterUnavailableException(exception);
        }
    }

    private static List<Long> events(DocumentSnapshot snapshot) {
        Object value = snapshot.exists() ? snapshot.get("eventEpochMs") : null;
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Number.class::isInstance).map(Number.class::cast).map(Number::longValue).toList();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof ExecutionException || current.getClass().getName().contains("Transaction"))
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    static String hashUid(String uid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    ("seewik-paid-limit-v1:" + uid).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static String globalDocumentId(String endpoint) {
        return REWARD_CLAIMS.equals(endpoint) ? "project-rewardClaims" : "project-paid-endpoints";
    }
}
