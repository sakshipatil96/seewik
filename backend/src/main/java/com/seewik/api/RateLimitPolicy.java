package com.seewik.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class RateLimitPolicy {
    static final Limits USER_LIMITS = new Limits(4, 20, 60);
    static final Limits GLOBAL_LIMITS = new Limits(12, 60, 300);
    static final long BURST_WINDOW_MS = Duration.ofSeconds(10).toMillis();
    static final long MINUTE_WINDOW_MS = Duration.ofMinutes(1).toMillis();
    static final long ROLLING_WINDOW_MS = Duration.ofHours(1).toMillis();

    private RateLimitPolicy() {}

    static Evaluation evaluate(List<Long> storedEvents, long nowMs, Limits limits) {
        List<Long> active = new ArrayList<>();
        for (Long value : storedEvents == null ? List.<Long>of() : storedEvents) {
            if (value != null && value <= nowMs && value > nowMs - ROLLING_WINDOW_MS) active.add(value);
        }
        active.sort(Long::compareTo);
        Evaluation violation = violation(active, nowMs, BURST_WINDOW_MS, limits.burst());
        if (violation != null) return violation;
        violation = violation(active, nowMs, MINUTE_WINDOW_MS, limits.minute());
        if (violation != null) return violation;
        violation = violation(active, nowMs, ROLLING_WINDOW_MS, limits.rolling());
        if (violation != null) return violation;
        active.add(nowMs);
        return new Evaluation(true, 0L, List.copyOf(active));
    }

    private static Evaluation violation(List<Long> active, long nowMs, long windowMs, int limit) {
        List<Long> inWindow = active.stream().filter(value -> value > nowMs - windowMs).toList();
        if (inWindow.size() < limit) return null;
        long retryMs = Math.max(1L, inWindow.getFirst() + windowMs - nowMs);
        return new Evaluation(false, Math.max(1L, (retryMs + 999L) / 1_000L), List.copyOf(active));
    }

    record Limits(int burst, int minute, int rolling) {}
    record Evaluation(boolean allowed, long retryAfterSeconds, List<Long> activeEvents) {}
}
