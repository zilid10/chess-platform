package me.zilid.chessplatform.util;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock that stands still until a test moves it.
 */
public final class MutableClock extends Clock {
    private AtomicReference<Instant> now;

    public MutableClock(Instant now) {
        this.now = new AtomicReference<>(now);
    }

    public void advance(Duration duration) {
        now.getAndUpdate(now -> now.plus(duration));
    }

    public void set(Instant instant) {
        now.set(instant);
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("MutableClock is always UTC");
    }
}
