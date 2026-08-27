package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class LastKnownGoodPrabhagSnapshotTest {
    private final LastKnownGoodPrabhagSnapshot snapshot = new LastKnownGoodPrabhagSnapshot(new ObjectMapper());

    @Test
    void packagedSnapshotHasFrozenIntegrityAndTwentyPolygons() {
        assertEquals(20, snapshot.boundaryCount());
        assertEquals("059533c8988334e7a268482c83bac9693e74783081c5b3a8cb51061bda4e100a",
                LastKnownGoodPrabhagSnapshot.CHECKSUM);
    }

    @Test
    void pointInPolygonReturnsSyntheticCandidateWithoutNearestGuess() {
        var match = snapshot.findCoveringBoundary(21.363778, 74.2411418);
        assertTrue(match.isPresent());
        assertEquals("PRABHAG-11", match.orElseThrow().prabhagId());
        assertEquals("UNSOURCED", match.orElseThrow().sourceStatus());
        assertTrue(match.orElseThrow().requiresCitizenConfirmation());
        assertTrue(snapshot.findCoveringBoundary(20.9042, 74.7749).isEmpty());
    }
}
