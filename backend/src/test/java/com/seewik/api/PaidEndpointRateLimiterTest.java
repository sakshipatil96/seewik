package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PaidEndpointRateLimiterTest {
    @Test
    void acceptsCallsBelowLimitAndExhaustsBurstWithSafeRetryAfter() {
        Harness limiter = new Harness();
        for (int call = 0; call < 4; call++) limiter.check("user-a", PaidEndpointRateLimiter.CLASSIFICATION);
        var error = assertThrows(PaidEndpointRateLimiter.RateLimitedException.class,
                () -> limiter.check("user-a", PaidEndpointRateLimiter.CLASSIFICATION));
        assertEquals("per_user", error.scope());
        assertTrue(error.retryAfterSeconds() >= 1 && error.retryAfterSeconds() <= 10);
    }

    @Test
    void classificationAndDraftingHaveSeparateUserLimits() {
        Harness limiter = new Harness();
        for (int call = 0; call < 4; call++) limiter.check("user-a", PaidEndpointRateLimiter.CLASSIFICATION);
        limiter.check("user-a", PaidEndpointRateLimiter.DRAFTING);
    }

    @Test
    void separateUsersAreIsolatedWhileProjectCeilingRemainsShared() {
        Harness limiter = new Harness();
        for (int call = 0; call < 4; call++) limiter.check("user-a", PaidEndpointRateLimiter.CLASSIFICATION);
        for (int call = 0; call < 4; call++) limiter.check("user-b", PaidEndpointRateLimiter.CLASSIFICATION);
        limiter.check("user-c", PaidEndpointRateLimiter.CLASSIFICATION);
        assertThrows(PaidEndpointRateLimiter.RateLimitedException.class,
                () -> limiter.check("user-a", PaidEndpointRateLimiter.CLASSIFICATION));
    }

    @Test
    void globalBurstCeilingRejectsFreshAnonymousIdentities() {
        Harness limiter = new Harness();
        for (int call = 0; call < 12; call++) {
            limiter.check("user-" + call, call % 2 == 0
                    ? PaidEndpointRateLimiter.CLASSIFICATION : PaidEndpointRateLimiter.DRAFTING);
        }
        var error = assertThrows(PaidEndpointRateLimiter.RateLimitedException.class,
                () -> limiter.check("user-13", PaidEndpointRateLimiter.CLASSIFICATION));
        assertEquals("global", error.scope());
    }

    @Test
    void rollingWindowRecoversAfterExpiry() {
        Harness limiter = new Harness();
        for (int call = 0; call < 4; call++) limiter.check("user-a", PaidEndpointRateLimiter.CLASSIFICATION);
        assertThrows(PaidEndpointRateLimiter.RateLimitedException.class,
                () -> limiter.check("user-a", PaidEndpointRateLimiter.CLASSIFICATION));
        limiter.nowMs += RateLimitPolicy.BURST_WINDOW_MS + 1;
        limiter.check("user-a", PaidEndpointRateLimiter.CLASSIFICATION);
    }

    @Test
    void concurrentRequestsCannotPassTheGlobalCeiling() throws Exception {
        Harness limiter = new Harness();
        int requests = 40;
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(requests)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int call = 0; call < requests; call++) {
                int index = call;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        limiter.check("concurrent-" + index, PaidEndpointRateLimiter.CLASSIFICATION);
                        accepted.incrementAndGet();
                    } catch (PaidEndpointRateLimiter.RateLimitedException expected) {
                        assertEquals("global", expected.scope());
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            for (var future : futures) future.get(3, TimeUnit.SECONDS);
        }
        assertEquals(12, accepted.get());
    }

    @Test
    void storedUserKeyIsHashedAndContainsNoRawUid() {
        String uid = "private-firebase-user-id";
        String hash = FirestorePaidEndpointRateLimiter.hashUid(uid);
        assertEquals(64, hash.length());
        assertFalse(hash.contains(uid));
        assertEquals(hash, FirestorePaidEndpointRateLimiter.hashUid(uid));
    }

    @Test
    void rewardGlobalLimitIsIsolatedWithoutChangingTheExistingPaidEndpointCeiling() {
        assertEquals("project-paid-endpoints", FirestorePaidEndpointRateLimiter.globalDocumentId(
                PaidEndpointRateLimiter.CLASSIFICATION));
        assertEquals("project-paid-endpoints", FirestorePaidEndpointRateLimiter.globalDocumentId(
                PaidEndpointRateLimiter.DRAFTING));
        assertEquals("project-rewardClaims", FirestorePaidEndpointRateLimiter.globalDocumentId(
                PaidEndpointRateLimiter.REWARD_CLAIMS));
    }

    private static final class Harness implements PaidEndpointRateLimiter {
        private final Map<String, List<Long>> users = new HashMap<>();
        private List<Long> global = List.of();
        private long nowMs = 1_800_000_000_000L;

        @Override
        public synchronized void check(String uid, String endpoint) {
            String key = uid + ":" + endpoint;
            RateLimitPolicy.Evaluation user = RateLimitPolicy.evaluate(
                    users.getOrDefault(key, List.of()), nowMs, RateLimitPolicy.USER_LIMITS);
            if (!user.allowed()) throw new RateLimitedException("per_user", user.retryAfterSeconds());
            RateLimitPolicy.Evaluation project = RateLimitPolicy.evaluate(
                    global, nowMs, RateLimitPolicy.GLOBAL_LIMITS);
            if (!project.allowed()) throw new RateLimitedException("global", project.retryAfterSeconds());
            users.put(key, user.activeEvents());
            global = project.activeEvents();
        }
    }
}
