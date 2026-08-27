package com.seewik.api;

import java.time.Duration;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public final class PrabhagCircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openNanos;
    private final LongSupplier nanoTime;
    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openUntil;
    private boolean halfOpenProbeInProgress;
    private long generation;

    @Autowired
    public PrabhagCircuitBreaker(
            @Value("${seewik.bigquery.circuit-failure-threshold:3}") int failureThreshold,
            @Value("${seewik.bigquery.circuit-open-ms:30000}") long openMs) {
        this(failureThreshold, Duration.ofMillis(openMs), System::nanoTime);
    }

    PrabhagCircuitBreaker(int failureThreshold, Duration openDuration, LongSupplier nanoTime) {
        if (failureThreshold < 1 || openDuration == null || openDuration.isNegative() || openDuration.isZero()) {
            throw new IllegalArgumentException("Circuit breaker settings must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openNanos = openDuration.toNanos();
        this.nanoTime = nanoTime;
    }

    public synchronized Permit acquire() {
        long now = nanoTime.getAsLong();
        if (state == State.CLOSED) return new Permit(true, State.CLOSED, generation);
        if (state == State.OPEN && now >= openUntil && !halfOpenProbeInProgress) {
            state = State.HALF_OPEN;
            halfOpenProbeInProgress = true;
            return new Permit(true, State.HALF_OPEN, generation);
        }
        return new Permit(false, state, generation);
    }

    public synchronized void success(Permit permit) {
        if (permit == null || permit.generation() != generation || !permit.callDependency()) return;
        state = State.CLOSED;
        consecutiveFailures = 0;
        halfOpenProbeInProgress = false;
        openUntil = 0L;
    }

    public synchronized void failure(Permit permit) {
        if (permit == null || permit.generation() != generation || !permit.callDependency()) return;
        if (state == State.HALF_OPEN) {
            open();
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) open();
    }

    public synchronized State state() {
        return state;
    }

    private void open() {
        generation++;
        state = State.OPEN;
        openUntil = nanoTime.getAsLong() + openNanos;
        halfOpenProbeInProgress = false;
    }

    public record Permit(boolean callDependency, State state, long generation) {}
}
