package com.seewik.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class PrabhagResolverService {
    public static final String DATASET_VERSION = "seewik-map-trace-v0.2";
    public static final String RESOLUTION_METHOD = "BIGQUERY_ST_COVERS";
    public static final String SNAPSHOT_RESOLUTION_METHOD = "SNAPSHOT_POINT_IN_POLYGON";
    public static final String MANUAL_RESOLUTION_METHOD = "MANUAL_SELECTION_REQUIRED";
    public static final String RESOLUTION_QUALITY = "APPROXIMATE_DIGITISED_MUNICIPAL_OFFICE_MAP_IMAGE";
    public static final String SOURCE_STATUS = "MUNICIPAL_OFFICE_WALL_MAP_PHOTO";
    public static final String REVIEW_STATUS = "NOT_AUTHORITY_VERIFIED";
    private final PrabhagBoundaryGateway boundaryGateway;
    private final LastKnownGoodPrabhagSnapshot snapshot;
    private final PrabhagCircuitBreaker circuitBreaker;
    private final OperationalMetrics metrics;

    @Autowired
    public PrabhagResolverService(
            PrabhagBoundaryGateway boundaryGateway,
            LastKnownGoodPrabhagSnapshot snapshot,
            PrabhagCircuitBreaker circuitBreaker,
            OperationalMetrics metrics) {
        this.boundaryGateway = boundaryGateway;
        this.snapshot = snapshot;
        this.circuitBreaker = circuitBreaker;
        this.metrics = metrics;
    }

    PrabhagResolverService(PrabhagBoundaryGateway boundaryGateway) {
        ObjectMapper json = new ObjectMapper();
        this.boundaryGateway = boundaryGateway;
        this.snapshot = new LastKnownGoodPrabhagSnapshot(json);
        this.circuitBreaker = new PrabhagCircuitBreaker(3, java.time.Duration.ofSeconds(30), System::nanoTime);
        this.metrics = new OperationalMetrics(json, "test");
    }

    public PrabhagResolution resolve(PrabhagResolutionRequest request) {
        Double latitude = request == null ? null : request.latitude();
        Double longitude = request == null ? null : request.longitude();
        if (!validCoordinates(latitude, longitude)) {
            metrics.increment("prabhag.invalid_coordinates");
            return PrabhagResolution.invalid();
        }
        if (!snapshot.available()) {
            metrics.increment("prabhag.snapshot_unavailable");
            metrics.increment("prabhag.manual_resolution_required");
            return PrabhagResolution.manualSelectionRequired();
        }

        long startedAt = System.nanoTime();
        PrabhagCircuitBreaker.Permit permit = circuitBreaker.acquire();
        if (!permit.callDependency()) {
            metrics.increment("bigquery.circuit_open_fallback");
            return snapshotFallback(latitude, longitude, startedAt, "CIRCUIT_OPEN");
        }
        try {
            Optional<PrabhagBoundaryGateway.BoundaryMatch> match =
                    boundaryGateway.findCoveringBoundary(latitude, longitude);
            long latencyMs = elapsedMillis(startedAt);
            circuitBreaker.success(permit);
            metrics.increment("bigquery.success");
            metrics.recordLatency("bigquery.resolution", latencyMs);
            if (match.isEmpty()) {
                metrics.increment("prabhag.outside_supported_area");
                return PrabhagResolution.outside(
                        latencyMs, RESOLUTION_METHOD, circuitBreaker.state().name(), null, null, null);
            }
            PrabhagBoundaryGateway.BoundaryMatch boundary = match.get();
            GoogleBigQueryPrabhagGateway.validate(boundary);
            metrics.increment("prabhag.bigquery_resolution");
            return PrabhagResolution.candidate(
                    boundary, RESOLUTION_METHOD, latencyMs, circuitBreaker.state().name(), null, null, null,
                    "Approximate map-trace suggestion only. Confirm the prabhag or choose it manually.");
        } catch (GoogleBigQueryPrabhagGateway.BoundaryTimeoutException error) {
            circuitBreaker.failure(permit);
            metrics.increment("bigquery.timeout");
            metrics.recordLatency("bigquery.resolution", elapsedMillis(startedAt));
            return snapshotFallback(latitude, longitude, startedAt, "BIGQUERY_TIMEOUT");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            circuitBreaker.failure(permit);
            metrics.increment("bigquery.failure");
            return snapshotFallback(latitude, longitude, startedAt, "BIGQUERY_INTERRUPTED");
        } catch (GoogleBigQueryPrabhagGateway.InvalidBoundaryResponseException error) {
            circuitBreaker.failure(permit);
            metrics.increment("bigquery.invalid_response");
            return snapshotFallback(latitude, longitude, startedAt, "BIGQUERY_INVALID_RESPONSE");
        } catch (RuntimeException error) {
            circuitBreaker.failure(permit);
            metrics.increment("bigquery.failure");
            return snapshotFallback(latitude, longitude, startedAt, "BIGQUERY_UNAVAILABLE");
        }
    }

    private PrabhagResolution snapshotFallback(
            double latitude, double longitude, long requestStartedAt, String fallbackReason) {
        long snapshotStartedAt = System.nanoTime();
        if (snapshot.coveringBoundaryCount(latitude, longitude) > 1) {
            metrics.increment("prabhag.snapshot_multi_match");
        }
        Optional<PrabhagBoundaryGateway.BoundaryMatch> match = snapshot.findCoveringBoundary(latitude, longitude);
        long snapshotLatencyMs = elapsedMillis(snapshotStartedAt);
        long totalLatencyMs = elapsedMillis(requestStartedAt);
        metrics.increment("bigquery.fallback");
        metrics.recordLatency("snapshot.resolution", snapshotLatencyMs);
        if (match.isEmpty()) {
            metrics.increment("prabhag.snapshot_outside");
            metrics.increment("prabhag.outside_supported_area");
            return PrabhagResolution.outside(
                    totalLatencyMs,
                    SNAPSHOT_RESOLUTION_METHOD,
                    circuitBreaker.state().name(),
                    fallbackReason,
                    LastKnownGoodPrabhagSnapshot.CHECKSUM,
                    LastKnownGoodPrabhagSnapshot.PROVENANCE);
        }
        metrics.increment("prabhag.snapshot_resolution");
        return PrabhagResolution.candidate(
                match.get(),
                SNAPSHOT_RESOLUTION_METHOD,
                totalLatencyMs,
                circuitBreaker.state().name(),
                fallbackReason,
                LastKnownGoodPrabhagSnapshot.CHECKSUM,
                LastKnownGoodPrabhagSnapshot.PROVENANCE,
                "BigQuery is temporarily unavailable. This approximate map-trace suggestion must be confirmed or replaced with a manual prabhag selection.");
    }

    private static boolean validCoordinates(Double latitude, Double longitude) {
        return latitude != null
                && longitude != null
                && Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90.0
                && latitude <= 90.0
                && longitude >= -180.0
                && longitude <= 180.0;
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, Math.round((System.nanoTime() - startedAt) / 1_000_000.0));
    }

    public record PrabhagResolutionRequest(Double latitude, Double longitude) {}

    public record PrabhagResolution(
            String status,
            String prabhagId,
            String prabhagName,
            String resolutionMethod,
            String resolutionQuality,
            boolean requiresCitizenConfirmation,
            String sourceReference,
            String sourceStatus,
            String reviewStatus,
            String datasetVersion,
            Long queryLatencyMs,
            String circuitState,
            String fallbackReason,
            String snapshotChecksum,
            String snapshotProvenance,
            String message) {
        static PrabhagResolution candidate(
                PrabhagBoundaryGateway.BoundaryMatch boundary,
                String method,
                long latencyMs,
                String circuitState,
                String fallbackReason,
                String snapshotChecksum,
                String snapshotProvenance,
                String message) {
            return new PrabhagResolution(
                    "CANDIDATE_PRABHAG", boundary.prabhagId(), boundary.prabhagName(), method,
                    boundary.resolutionQuality(), true, boundary.sourceReference(), boundary.sourceStatus(),
                    boundary.reviewStatus(), boundary.datasetVersion(), latencyMs, circuitState, fallbackReason,
                    snapshotChecksum, snapshotProvenance, message);
        }

        static PrabhagResolution invalid() {
            return new PrabhagResolution(
                    "INVALID_COORDINATES", null, null, null, null, false, null, null, null,
                    DATASET_VERSION, null, null, null, null, null,
                    "Provide valid latitude and longitude values.");
        }

        static PrabhagResolution manualSelectionRequired() {
            return new PrabhagResolution(
                    "MANUAL_SELECTION_REQUIRED", null, null, MANUAL_RESOLUTION_METHOD, null,
                    false, null, null, null, DATASET_VERSION, null,
                    null, "SNAPSHOT_UNAVAILABLE", null, null,
                    "Automatic prabhag suggestion is unavailable. Select Prabhag 1-20 manually.");
        }

        static PrabhagResolution outside(
                long latencyMs,
                String method,
                String circuitState,
                String fallbackReason,
                String snapshotChecksum,
                String snapshotProvenance) {
            return new PrabhagResolution(
                    "OUTSIDE_SUPPORTED_AREA", null, null, method, RESOLUTION_QUALITY,
                    false, null, SOURCE_STATUS, REVIEW_STATUS, DATASET_VERSION, latencyMs,
                    circuitState, fallbackReason, snapshotChecksum, snapshotProvenance,
                    "The coordinates are outside the approximate mapped extent. Select a prabhag manually if appropriate.");
        }
    }
}
