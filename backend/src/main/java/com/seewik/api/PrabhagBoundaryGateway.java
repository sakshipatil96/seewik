package com.seewik.api;

import java.util.Optional;

public interface PrabhagBoundaryGateway {
    Optional<BoundaryMatch> findCoveringBoundary(double latitude, double longitude) throws InterruptedException;

    record BoundaryMatch(
            String prabhagId,
            String prabhagName,
            String resolutionQuality,
            boolean requiresCitizenConfirmation,
            String sourceReference,
            String sourceStatus,
            String reviewStatus,
            String datasetVersion) {}
}
