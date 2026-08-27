package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PrabhagCircuitBreakerTest {
    @Test
    void closedOpensAfterThresholdThenAllowsOneHalfOpenProbeAndRecovers() {
        AtomicLong now = new AtomicLong();
        PrabhagCircuitBreaker breaker = new PrabhagCircuitBreaker(3, Duration.ofSeconds(30), now::get);
        var first = breaker.acquire();
        assertTrue(first.callDependency());
        breaker.failure(first);
        breaker.failure(breaker.acquire());
        assertEquals(PrabhagCircuitBreaker.State.CLOSED, breaker.state());
        breaker.failure(breaker.acquire());
        assertEquals(PrabhagCircuitBreaker.State.OPEN, breaker.state());
        assertFalse(breaker.acquire().callDependency());
        now.addAndGet(Duration.ofSeconds(30).toNanos());
        var probe = breaker.acquire();
        assertEquals(PrabhagCircuitBreaker.State.HALF_OPEN, probe.state());
        assertFalse(breaker.acquire().callDependency());
        breaker.success(probe);
        assertEquals(PrabhagCircuitBreaker.State.CLOSED, breaker.state());
        assertTrue(breaker.acquire().callDependency());
    }

    @Test
    void failedHalfOpenProbeReopensForFullRecoveryPeriod() {
        AtomicLong now = new AtomicLong();
        PrabhagCircuitBreaker breaker = new PrabhagCircuitBreaker(1, Duration.ofSeconds(30), now::get);
        breaker.failure(breaker.acquire());
        now.addAndGet(Duration.ofSeconds(30).toNanos());
        var probe = breaker.acquire();
        assertTrue(probe.callDependency());
        breaker.failure(probe);
        assertEquals(PrabhagCircuitBreaker.State.OPEN, breaker.state());
        assertFalse(breaker.acquire().callDependency());
    }

    @Test
    void lateSuccessFromAnOlderConcurrentGenerationCannotCloseAnOpenedCircuit() {
        AtomicLong now = new AtomicLong();
        PrabhagCircuitBreaker breaker = new PrabhagCircuitBreaker(1, Duration.ofSeconds(30), now::get);
        var failingCall = breaker.acquire();
        var slowerSuccessfulCall = breaker.acquire();
        breaker.failure(failingCall);
        breaker.success(slowerSuccessfulCall);
        assertEquals(PrabhagCircuitBreaker.State.OPEN, breaker.state());
    }
}
