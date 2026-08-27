package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrabhagForcedFailureLatencyTest {
    @Test
    void recordsBoundedTimeoutAndOpenCircuitFallbackLatency() throws Exception {
        List<Long> timeoutFallback = new ArrayList<>();
        for (int sample = 0; sample < 5; sample++) {
            PrabhagResolverService service = service((latitude, longitude) -> {
                Thread.sleep(1_500);
                throw new GoogleBigQueryPrabhagGateway.BoundaryTimeoutException(
                        Duration.ofMillis(1_500), new java.util.concurrent.TimeoutException());
            }, new PrabhagCircuitBreaker(3, Duration.ofSeconds(30), System::nanoTime));
            long started = System.nanoTime();
            var result = service.resolve(new PrabhagResolverService.PrabhagResolutionRequest(
                    21.363778, 74.2411418));
            timeoutFallback.add(elapsed(started));
            assertEquals("BIGQUERY_TIMEOUT", result.fallbackReason());
            assertEquals("PRABHAG-11", result.prabhagId());
        }

        PrabhagResolverService openService = service((latitude, longitude) -> {
            throw new IllegalStateException("forced unavailable dependency");
        }, new PrabhagCircuitBreaker(3, Duration.ofSeconds(30), System::nanoTime));
        for (int failure = 0; failure < 3; failure++) {
            openService.resolve(new PrabhagResolverService.PrabhagResolutionRequest(21.363778, 74.2411418));
        }
        List<Long> openFallback = new ArrayList<>();
        for (int sample = 0; sample < 20; sample++) {
            long started = System.nanoTime();
            var result = openService.resolve(new PrabhagResolverService.PrabhagResolutionRequest(
                    21.363778, 74.2411418));
            openFallback.add(elapsed(started));
            assertEquals("CIRCUIT_OPEN", result.fallbackReason());
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("evidenceType", "LOCAL_FORCED_DEPENDENCY_STUBS");
        evidence.put("bigQueryContractTimeoutMs", 1_500);
        evidence.put("timeoutToSnapshotFallback", stats(timeoutFallback));
        evidence.put("openCircuitSnapshotFallback", stats(openFallback));
        evidence.put("timeoutCount", 5);
        evidence.put("openCircuitFallbackCount", 20);
        evidence.put("snapshotCandidateCount", 25);
        evidence.put("outsideAreaCount", 0);
        System.out.println("PRABHAG_FORCED_FAILURE_EVIDENCE="
                + new ObjectMapper().writeValueAsString(evidence));

        assertTrue(Collections.max(timeoutFallback) < 2_000);
        assertTrue(Collections.max(openFallback) < 100);
    }

    private static PrabhagResolverService service(
            PrabhagBoundaryGateway gateway, PrabhagCircuitBreaker breaker) {
        ObjectMapper json = new ObjectMapper();
        return new PrabhagResolverService(
                gateway,
                new LastKnownGoodPrabhagSnapshot(json),
                breaker,
                new OperationalMetrics(json, "test"));
    }

    private static Map<String, Long> stats(List<Long> samples) {
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        return Map.of(
                "samples", (long) sorted.size(),
                "min", sorted.getFirst(),
                "p50", percentile(sorted, 0.50),
                "p95", percentile(sorted, 0.95),
                "max", sorted.getLast());
    }

    private static long percentile(List<Long> sorted, double value) {
        int index = Math.max(0, (int) Math.ceil(sorted.size() * value) - 1);
        return sorted.get(index);
    }

    private static long elapsed(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }
}
