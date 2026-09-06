package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PrabhagResolverServiceTest {
    @Test
    void bigQueryExceptionFallsBackToApproximateMapSnapshot() {
        PrabhagResolverService service = service((latitude, longitude) -> {
            throw new IllegalStateException("simulated dependency failure");
        }, new PrabhagCircuitBreaker(3, Duration.ofSeconds(30), System::nanoTime));

        PrabhagResolverService.PrabhagResolution result = service.resolve(
                new PrabhagResolverService.PrabhagResolutionRequest(21.363778, 74.2411418));

        assertEquals("CANDIDATE_PRABHAG", result.status());
        assertEquals("PRABHAG-18", result.prabhagId());
        assertEquals("SNAPSHOT_POINT_IN_POLYGON", result.resolutionMethod());
        assertEquals("BIGQUERY_UNAVAILABLE", result.fallbackReason());
        assertEquals("seewik-map-trace-v0.2", result.datasetVersion());
        assertEquals("APPROXIMATE_DIGITISED_MUNICIPAL_OFFICE_MAP_IMAGE", result.resolutionQuality());
        assertEquals("MUNICIPAL_OFFICE_WALL_MAP_PHOTO", result.sourceStatus());
        assertEquals("NOT_AUTHORITY_VERIFIED", result.reviewStatus());
        assertTrue(result.requiresCitizenConfirmation());
    }

    @Test
    void bigQueryTimeoutFallsBackWithinControlledContract() {
        PrabhagResolverService service = service((latitude, longitude) -> {
            throw new GoogleBigQueryPrabhagGateway.BoundaryTimeoutException(
                    Duration.ofMillis(1500), new java.util.concurrent.TimeoutException());
        }, new PrabhagCircuitBreaker(3, Duration.ofSeconds(30), System::nanoTime));

        var result = service.resolve(new PrabhagResolverService.PrabhagResolutionRequest(
                21.363778, 74.2411418));

        assertEquals("CANDIDATE_PRABHAG", result.status());
        assertEquals("BIGQUERY_TIMEOUT", result.fallbackReason());
        assertEquals(LastKnownGoodPrabhagSnapshot.CHECKSUM, result.snapshotChecksum());
    }

    @Test
    void invalidOrEmptyBoundaryResponseFallsBackWithoutFabrication() {
        PrabhagResolverService service = service((latitude, longitude) -> {
            throw new GoogleBigQueryPrabhagGateway.InvalidBoundaryResponseException(
                    "simulated empty result object");
        }, new PrabhagCircuitBreaker(3, Duration.ofSeconds(30), System::nanoTime));

        var inside = service.resolve(new PrabhagResolverService.PrabhagResolutionRequest(
                21.363778, 74.2411418));
        var outside = service.resolve(new PrabhagResolverService.PrabhagResolutionRequest(
                20.9042, 74.7749));

        assertEquals("BIGQUERY_INVALID_RESPONSE", inside.fallbackReason());
        assertEquals("OUTSIDE_SUPPORTED_AREA", outside.status());
        assertNull(outside.prabhagId());
        assertEquals("SNAPSHOT_POINT_IN_POLYGON", outside.resolutionMethod());
    }

    @Test
    void unavailableSnapshotStartsDegradedAndRequiresManualSelectionWithoutGuessing() {
        AtomicInteger primaryCalls = new AtomicInteger();
        ObjectMapper json = new ObjectMapper();
        OperationalMetrics metrics = new OperationalMetrics(json, "test");
        LastKnownGoodPrabhagSnapshot unavailable = new LastKnownGoodPrabhagSnapshot(
                json, "/missing-prabhag-snapshot.geojson", LastKnownGoodPrabhagSnapshot.CHECKSUM);
        PrabhagResolverService service = new PrabhagResolverService(
                (latitude, longitude) -> {
                    primaryCalls.incrementAndGet();
                    throw new IllegalStateException("primary unavailable");
                },
                unavailable,
                new PrabhagCircuitBreaker(3, Duration.ofSeconds(30), System::nanoTime),
                metrics);

        var result = service.resolve(new PrabhagResolverService.PrabhagResolutionRequest(
                21.363778, 74.2411418));

        assertEquals("MANUAL_SELECTION_REQUIRED", result.status());
        assertEquals("MANUAL_SELECTION_REQUIRED", result.resolutionMethod());
        assertNull(result.prabhagId());
        assertEquals("SNAPSHOT_UNAVAILABLE", result.fallbackReason());
        assertEquals(0, primaryCalls.get());
        @SuppressWarnings("unchecked")
        var counters = (java.util.Map<String, Long>) metrics.snapshot().get("counters");
        assertEquals(1L, counters.get("prabhag.snapshot_unavailable"));
        assertEquals(1L, counters.get("prabhag.manual_resolution_required"));
    }

    @Test
    void repeatedFailuresOpenCircuitAndSkipDependency() {
        AtomicInteger calls = new AtomicInteger();
        PrabhagResolverService service = service((latitude, longitude) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("unavailable");
        }, new PrabhagCircuitBreaker(3, Duration.ofSeconds(30), System::nanoTime));

        for (int request = 0; request < 4; request++) {
            var result = service.resolve(new PrabhagResolverService.PrabhagResolutionRequest(
                    21.363778, 74.2411418));
            assertEquals("CANDIDATE_PRABHAG", result.status());
        }

        assertEquals(3, calls.get());
    }

    @Test
    void healthyOutsideResultDoesNotOpenCircuit() {
        AtomicInteger calls = new AtomicInteger();
        PrabhagResolverService service = service((latitude, longitude) -> {
            calls.incrementAndGet();
            return Optional.empty();
        }, new PrabhagCircuitBreaker(1, Duration.ofSeconds(30), System::nanoTime));

        var first = service.resolve(new PrabhagResolverService.PrabhagResolutionRequest(20.9042, 74.7749));
        var second = service.resolve(new PrabhagResolverService.PrabhagResolutionRequest(20.9042, 74.7749));

        assertEquals("OUTSIDE_SUPPORTED_AREA", first.status());
        assertEquals("BIGQUERY_ST_COVERS", first.resolutionMethod());
        assertEquals("OUTSIDE_SUPPORTED_AREA", second.status());
        assertEquals(2, calls.get());
    }

    @Test
    void successfulBigQueryMatchRemainsApproximateAndConfirmationRequired() {
        PrabhagResolverService service = service((latitude, longitude) -> Optional.of(match()),
                new PrabhagCircuitBreaker(3, Duration.ofSeconds(30), System::nanoTime));

        var result = service.resolve(new PrabhagResolverService.PrabhagResolutionRequest(
                21.363778, 74.2411418));

        assertEquals("CANDIDATE_PRABHAG", result.status());
        assertEquals("BIGQUERY_ST_COVERS", result.resolutionMethod());
        assertEquals("PRABHAG-11", result.prabhagId());
        assertTrue(result.requiresCitizenConfirmation());
        assertNull(result.fallbackReason());
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

    private static PrabhagBoundaryGateway.BoundaryMatch match() {
        return new PrabhagBoundaryGateway.BoundaryMatch(
                "PRABHAG-11", "Prabhag 11", "APPROXIMATE_DIGITISED_MUNICIPAL_OFFICE_MAP_IMAGE", true,
                "Nandurbar municipal-office 2025 wall-map photograph", "MUNICIPAL_OFFICE_WALL_MAP_PHOTO",
                "NOT_AUTHORITY_VERIFIED", "seewik-map-trace-v0.2");
    }
}
