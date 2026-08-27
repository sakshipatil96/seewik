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
        assertFalse(GoogleBigQueryPrabhagGateway.LOOKUP_SQL.contains("ST_DISTANCE"));
    }

    @Test
    void invalidRowsCannotInventPrabhagsOrRelaxSyntheticLimitations() {
        var invalid = new PrabhagBoundaryGateway.BoundaryMatch(
                "PRABHAG-21", "Prabhag 21", "SYNTHETIC_BOUNDARY", true,
                "source", "UNSOURCED", "REVIEW_PENDING", "synthetic-v0.1");
        org.junit.jupiter.api.Assertions.assertThrows(
                GoogleBigQueryPrabhagGateway.InvalidBoundaryResponseException.class,
                () -> GoogleBigQueryPrabhagGateway.validate(invalid));
    }
}
