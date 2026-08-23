package com.seewik.api;

import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PrabhagResolverService {
    public static final String DATASET_VERSION = "synthetic-v0.1";
    public static final String RESOLUTION_METHOD = "BIGQUERY_ST_COVERS";
    public static final String RESOLUTION_QUALITY = "SYNTHETIC_BOUNDARY";
    private final PrabhagBoundaryGateway boundaryGateway;

    public PrabhagResolverService(PrabhagBoundaryGateway boundaryGateway) {
        this.boundaryGateway = boundaryGateway;
    }

    public PrabhagResolution resolve(PrabhagResolutionRequest request) {
        Double latitude = request == null ? null : request.latitude();
        Double longitude = request == null ? null : request.longitude();
        if (!validCoordinates(latitude, longitude)) {
            return PrabhagResolution.invalid();
        }

        long startedAt = System.nanoTime();
        try {
            Optional<PrabhagBoundaryGateway.BoundaryMatch> match =
                    boundaryGateway.findCoveringBoundary(latitude, longitude);
            long latencyMs = elapsedMillis(startedAt);
            if (match.isEmpty()) {
                return PrabhagResolution.outside(latencyMs);
            }
            PrabhagBoundaryGateway.BoundaryMatch boundary = match.get();
            return new PrabhagResolution(
                    "CANDIDATE_PRABHAG",
                    boundary.prabhagId(),
                    boundary.prabhagName(),
                    RESOLUTION_METHOD,
                    boundary.resolutionQuality(),
                    boundary.requiresCitizenConfirmation(),
                    boundary.sourceReference(),
                    boundary.sourceStatus(),
                    boundary.reviewStatus(),
                    boundary.datasetVersion(),
                    latencyMs,
                    "Synthetic candidate only. Confirm the prabhag or choose it manually.");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return PrabhagResolution.unavailable(elapsedMillis(startedAt));
        } catch (RuntimeException error) {
            return PrabhagResolution.unavailable(elapsedMillis(startedAt));
        }
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
            String message) {
        static PrabhagResolution invalid() {
            return new PrabhagResolution(
                    "INVALID_COORDINATES", null, null, null, null, false, null, null, null,
                    DATASET_VERSION, null, "Provide valid latitude and longitude values.");
        }

        static PrabhagResolution outside(long latencyMs) {
            return new PrabhagResolution(
                    "OUTSIDE_SUPPORTED_AREA", null, null, RESOLUTION_METHOD, RESOLUTION_QUALITY,
                    false, null, "UNSOURCED", "REVIEW_PENDING", DATASET_VERSION, latencyMs,
                    "The coordinates are outside the synthetic development extent. Select a prabhag manually if appropriate.");
        }

        static PrabhagResolution unavailable(long latencyMs) {
            return new PrabhagResolution(
                    "RESOLUTION_UNAVAILABLE", null, null, RESOLUTION_METHOD, RESOLUTION_QUALITY,
                    false, null, "UNSOURCED", "REVIEW_PENDING", DATASET_VERSION, latencyMs,
                    "Automatic resolution is temporarily unavailable. Select a prabhag manually.");
        }
    }
}
