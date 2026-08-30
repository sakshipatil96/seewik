package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AttendanceCodeServiceTest {
    private static final String SECRET = "attendance-test-secret-with-at-least-32-bytes";

    @Test
    void codesAreSixDigitsBoundToInitiativeAndTenMinuteSlot() {
        AttendanceCodeService codes = new AttendanceCodeService(SECRET);
        Instant firstSlot = Instant.parse("2026-08-30T12:01:00Z");
        Instant sameSlot = Instant.parse("2026-08-30T12:09:59Z");
        Instant nextSlot = Instant.parse("2026-08-30T12:10:00Z");

        String first = codes.codeFor("init-1", firstSlot);
        assertTrue(first.matches("^[0-9]{6}$"));
        assertTrue(codes.matches("init-1", sameSlot, first));
        assertFalse(codes.matches("init-2", sameSlot, first));
        assertNotEquals(first, codes.codeFor("init-1", nextSlot));
    }

    @Test
    void precedingCodeGetsOnlyTheTwoMinuteBoundaryGrace() {
        AttendanceCodeService codes = new AttendanceCodeService(SECRET);
        String previous = codes.codeFor("init-1", Instant.parse("2026-08-30T12:09:59Z"));

        assertTrue(codes.matches("init-1", Instant.parse("2026-08-30T12:11:59Z"), previous));
        assertFalse(codes.matches("init-1", Instant.parse("2026-08-30T12:12:00Z"), previous));
    }

    @Test
    void unavailableSecretFailsClosedWithoutExposingAWeakCode() {
        AttendanceCodeService codes = new AttendanceCodeService("short");
        assertFalse(codes.available());
        InitiativeService.InitiativeException error = assertThrows(
                InitiativeService.InitiativeException.class,
                () -> codes.codeFor("init-1", Instant.parse("2026-08-30T12:00:00Z")));
        assertTrue(error.code().contains("CONFIGURATION"));
    }
}
