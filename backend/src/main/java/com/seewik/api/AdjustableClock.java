package com.seewik.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

final class AdjustableClock extends Clock {
    private final Clock base;
    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    AdjustableClock() {
        this(Clock.systemUTC());
    }

    AdjustableClock(Clock base) {
        this.base = base;
    }

    void setOffsetDays(long days) {
        offset.set(Duration.ofDays(days));
    }

    long offsetDays() {
        return offset.get().toDays();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException("The local E2E clock is UTC-only");
        return this;
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }
}
