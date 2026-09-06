package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GoogleBigQueryPrabhagGatewayTest {
    @Test
    void runtimeQueryUsesPolygonCoverageAndNoNearestBoundaryGuess() {
        assertTrue(GoogleBigQueryPrabhagGateway.LOOKUP_SQL.contains("ST_COVERS"));
        assertTrue(GoogleBigQueryPrabhagGateway.LOOKUP_SQL.contains("@latitude"));
        assertTrue(GoogleBigQueryPrabhagGateway.LOOKUP_SQL.contains("@longitude"));
        assertTrue(GoogleBigQueryPrabhagGateway.LOOKUP_SQL.contains("COUNT(*) OVER() AS coveringMatchCount"));
        assertTrue(GoogleBigQueryPrabhagGateway.LOOKUP_SQL.contains("ORDER BY prabhagId"));
        assertTrue(GoogleBigQueryPrabhagGateway.LOOKUP_SQL.contains("LIMIT 1"));
        assertFalse(GoogleBigQueryPrabhagGateway.LOOKUP_SQL.contains("ST_DISTANCE"));
    }

    @Test
    void invalidRowsCannotInventPrabhagsOrRelaxApproximateBoundaryLimitations() {
        var invalid = new PrabhagBoundaryGateway.BoundaryMatch(
                "PRABHAG-21", "Prabhag 21", "APPROXIMATE_DIGITISED_MUNICIPAL_OFFICE_MAP_IMAGE", true,
                "source", "MUNICIPAL_OFFICE_WALL_MAP_PHOTO", "NOT_AUTHORITY_VERIFIED", "seewik-map-trace-v0.2");
        org.junit.jupiter.api.Assertions.assertThrows(
                GoogleBigQueryPrabhagGateway.InvalidBoundaryResponseException.class,
                () -> GoogleBigQueryPrabhagGateway.validate(invalid));
    }
}
