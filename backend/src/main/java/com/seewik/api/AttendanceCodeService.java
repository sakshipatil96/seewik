package com.seewik.api;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class AttendanceCodeService {
    static final long SLOT_SECONDS = 10 * 60;
    static final long GRACE_SECONDS = 2 * 60;
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    AttendanceCodeService(String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    boolean available() {
        return secret.length >= 32;
    }

    long slot(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), SLOT_SECONDS);
    }

    String codeFor(String initiativeId, Instant now) {
        return codeForSlot(initiativeId, slot(now));
    }

    boolean matches(String initiativeId, Instant now, String submittedCode) {
        if (submittedCode == null || !submittedCode.matches("^[0-9]{6}$")) return false;
        long currentSlot = slot(now);
        if (sameCode(submittedCode, codeForSlot(initiativeId, currentSlot))) return true;
        long secondsIntoSlot = Math.floorMod(now.getEpochSecond(), SLOT_SECONDS);
        return secondsIntoSlot < GRACE_SECONDS
                && sameCode(submittedCode, codeForSlot(initiativeId, currentSlot - 1));
    }

    private String codeForSlot(String initiativeId, long slot) {
        if (!available()) {
            throw new InitiativeService.InitiativeException(
                    "ATTENDANCE_CONFIGURATION_UNAVAILABLE",
                    "Attendance codes are temporarily unavailable");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] digest = mac.doFinal((initiativeId + ":" + slot).getBytes(StandardCharsets.UTF_8));
            long value = ByteBuffer.wrap(digest, 0, Long.BYTES).getLong() & Long.MAX_VALUE;
            return String.format(Locale.ROOT, "%06d", value % 1_000_000L);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Attendance code generation is unavailable", exception);
        }
    }

    private static boolean sameCode(String submitted, String expected) {
        return MessageDigest.isEqual(
                submitted.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
    }
}
