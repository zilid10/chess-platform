package me.zilid.chessplatform.chess.game;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/**
 * A starting time and a per-move increment, written like "5+3". The starting time is at most 180 minutes in steps of 15
 * seconds, and the increment is at most 180 whole seconds.
 */
public record ClockSetting(Duration initial, Duration increment) {
    private static final Duration MAX_INITIAL = Duration.ofHours(3);
    private static final Duration MAX_INCREMENT = Duration.ofMinutes(3);
    private static final Duration INITIAL_STEP = Duration.ofSeconds(15);

    public ClockSetting {
        Objects.requireNonNull(initial, "initial cannot be null");
        Objects.requireNonNull(increment, "increment cannot be null");

        if (initial.isNegative() || increment.isNegative()) {
            throw new IllegalArgumentException("initial and increment cannot be negative");
        }
        if (initial.isZero() && increment.isZero()) {
            throw new IllegalArgumentException("initial and increment cannot both be zero");
        }
        if (initial.compareTo(MAX_INITIAL) > 0 || increment.compareTo(MAX_INCREMENT) > 0) {
            throw new IllegalArgumentException(
                    "initial cannot exceed 180 minutes and increment cannot exceed 180 seconds");
        }
        if (initial.toMillis() % INITIAL_STEP.toMillis() != 0) {
            throw new IllegalArgumentException("initial must be a multiple of 15 seconds");
        }
        if (increment.toMillis() % 1000 != 0) {
            throw new IllegalArgumentException("increment must be whole seconds");
        }
    }

    public static ClockSetting ofMinutes(int minutes, int incrementSeconds) {
        return new ClockSetting(Duration.ofMinutes(minutes), Duration.ofSeconds(incrementSeconds));
    }

    /**
     * Read a setting written like {@link #toString()}: minutes, which may be fractional, then "+" and increment
     * seconds, such as "5+3" or "0.5+0".
     *
     * @throws IllegalArgumentException if {@code text} is malformed or describes an invalid setting
     */
    public static ClockSetting parse(String text) {
        String[] parts = text.split("\\+", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("clock setting must look like 5+3: " + text);
        }
        try {
            long initialSeconds = new BigDecimal(parts[0].strip())
                    .multiply(BigDecimal.valueOf(60))
                    .longValueExact();
            long incrementSeconds = Long.parseLong(parts[1].strip());
            return new ClockSetting(Duration.ofSeconds(initialSeconds), Duration.ofSeconds(incrementSeconds));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("initial time must be whole seconds: " + text, e);
        }
    }

    /** The rating category, from the estimated length of a 40-move game. */
    public TimeControl category() {
        long estimatedSeconds = initial.toSeconds() + 40 * increment.toSeconds();
        if (estimatedSeconds < 180) {
            return TimeControl.BULLET;
        } else if (estimatedSeconds < 480) {
            return TimeControl.BLITZ;
        } else if (estimatedSeconds < 1500) {
            return TimeControl.RAPID;
        }
        return TimeControl.CLASSICAL;
    }

    /**
     * Minutes plus increment seconds, such as "5+3" or "0.5+0". Distinct settings give distinct strings, so this can
     * serve as a matchmaking queue key.
     */
    @Override
    public String toString() {
        // A multiple of 15 seconds is always a whole number of quarter minutes, so the division is exact.
        String minutes = BigDecimal.valueOf(initial.toSeconds())
                .divide(BigDecimal.valueOf(60))
                .stripTrailingZeros()
                .toPlainString();
        return minutes + "+" + increment.toSeconds();
    }
}
