package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PrabhagResolverServiceTest {
    @Test
    void bigQueryFailureReturnsControlledManualFallback() {
        PrabhagResolverService service = new PrabhagResolverService((latitude, longitude) -> {
            throw new IllegalStateException("simulated BigQuery failure");
        });

        PrabhagResolverService.PrabhagResolution result = service.resolve(
                new PrabhagResolverService.PrabhagResolutionRequest(21.363778, 74.2411418));

        assertEquals("RESOLUTION_UNAVAILABLE", result.status());
        assertNull(result.prabhagId());
        assertEquals("synthetic-v0.1", result.datasetVersion());
        assertEquals("SYNTHETIC_BOUNDARY", result.resolutionQuality());
    }
}
