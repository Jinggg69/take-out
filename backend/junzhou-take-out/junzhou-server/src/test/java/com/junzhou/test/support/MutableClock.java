package com.junzhou.test.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Settable clock so calendar-day boundaries can be exercised without waiting for a real midnight. */
public final class MutableClock extends Clock {
    private final ZoneId zone;
    private volatile Instant instant;

    public MutableClock(LocalDateTime shanghaiTime) {
        this(shanghaiTime, ZoneId.of("Asia/Shanghai"));
    }

    public MutableClock(LocalDateTime time, ZoneId zone) {
        this.zone = zone;
        this.instant = time.atZone(zone).toInstant();
    }

    public void set(LocalDateTime time) {
        this.instant = time.atZone(zone).toInstant();
    }

    public LocalDate today() {
        return LocalDateTime.ofInstant(instant, zone).toLocalDate();
    }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId other) { return Clock.fixed(instant, other); }
    @Override public Instant instant() { return instant; }
}
