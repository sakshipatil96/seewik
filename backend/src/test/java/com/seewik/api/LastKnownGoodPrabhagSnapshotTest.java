package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class LastKnownGoodPrabhagSnapshotTest {
    private final LastKnownGoodPrabhagSnapshot snapshot = new LastKnownGoodPrabhagSnapshot(new ObjectMapper());

    @Test
    void packagedSnapshotHasFrozenIntegrityAndTwentyPolygons() {
        assertTrue(snapshot.available());
        assertEquals(20, snapshot.boundaryCount());
        assertEquals("e386a77bd824e8eac91e6051b8be2428a2d70ecbc8954c0d03f4f37fb4c645dd",
                LastKnownGoodPrabhagSnapshot.CHECKSUM);
    }

    @Test
    void pointInPolygonReturnsApproximateCandidateWithoutNearestGuess() {
        var match = snapshot.findCoveringBoundary(21.363778, 74.2411418);
        assertTrue(match.isPresent());
        assertEquals("PRABHAG-18", match.orElseThrow().prabhagId());
        assertEquals("MUNICIPAL_OFFICE_WALL_MAP_PHOTO", match.orElseThrow().sourceStatus());
        assertTrue(match.orElseThrow().requiresCitizenConfirmation());
        assertTrue(snapshot.findCoveringBoundary(20.9042, 74.7749).isEmpty());
    }

    @Test
    void multiMatchUsesTheLowestPrabhagIdDeterministically() {
        assertEquals(2, snapshot.coveringBoundaryCount(21.383790, 74.239480));
        assertEquals("PRABHAG-01", snapshot.findCoveringBoundary(21.383790, 74.239480).orElseThrow().prabhagId());
    }

    @Test
    void missingSnapshotEnablesDegradedManualModeInsteadOfFailingStartup() {
        LastKnownGoodPrabhagSnapshot unavailable = new LastKnownGoodPrabhagSnapshot(
                new ObjectMapper(), "/missing-prabhag-snapshot.geojson", LastKnownGoodPrabhagSnapshot.CHECKSUM);

        assertFalse(unavailable.available());
        assertEquals(0, unavailable.boundaryCount());
        assertTrue(unavailable.findCoveringBoundary(21.363778, 74.2411418).isEmpty());
    }

    @Test
    void corruptOrWrongSnapshotEnablesTheSameDegradedManualMode() {
        LastKnownGoodPrabhagSnapshot unavailable = new LastKnownGoodPrabhagSnapshot(
                new ObjectMapper(), "/civic-pack-v0.2.json", LastKnownGoodPrabhagSnapshot.CHECKSUM);

        assertFalse(unavailable.available());
        assertEquals(0, unavailable.boundaryCount());
    }
}
