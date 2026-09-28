package me.zilid.chessplatform.chess.game;

import me.zilid.chessplatform.chess.Color;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Objects;

/**
 * Both players' remaining time. Nothing ticks: the side to move's remaining time is its stored time minus the time
 * since its turn started, worked out whenever it is read. Callers pass the current time in.
 * <p>
 * The clock starts when Black makes the first move, so each side's first move is free. Every move, including those,
 * earns the increment. A player whose remaining time reaches zero has flagged.
 */
public class ChessClock {
    private final ClockSetting clockSetting;
    private final EnumMap<Color, Duration> remainings = new EnumMap<>(Color.class);
    private Color turnColor;
    // null until the clock starts, and again once it is stopped
    private @Nullable Instant turnStartAt;
    private boolean stopped;

    public ChessClock(ClockSetting clockSetting) {
        this.clockSetting = clockSetting;
        remainings.put(Color.WHITE, clockSetting.initial());
        remainings.put(Color.BLACK, clockSetting.initial());
        turnColor = Color.WHITE;
    }

    /**
     * Rebuild a clock from values read with its getters.
     */
    public static ChessClock restore(
            ClockSetting clockSetting,
            Duration whiteRemaining,
            Duration blackRemaining,
            Color turnColor,
            @Nullable Instant turnStartAt,
            boolean stopped
    ) {
        ChessClock chessClock = new ChessClock(clockSetting);
        chessClock.remainings.put(Color.WHITE, whiteRemaining);
        chessClock.remainings.put(Color.BLACK, blackRemaining);
        chessClock.turnColor = turnColor;
        chessClock.turnStartAt = turnStartAt;
        chessClock.stopped = stopped;
        return chessClock;
    }

    // Instances' clocks can disagree slightly; never let that give a player extra time.
    private static Duration elapsedSince(Instant start, Instant now) {
        Duration elapsed = Duration.between(start, now);
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }

    /**
     * The time {@code color} has left at {@code now}. Only the side to move loses time, and only once the clock has
     * started. The result is negative after a flag.
     */
    public Duration remaining(Color color, Instant now) {
        Duration stored = Objects.requireNonNull(remainings.get(color));
        if (turnStartAt == null || color != turnColor) {
            return stored;
        }
        return stored.minus(elapsedSince(turnStartAt, now));
    }

    public boolean hasFlagged(Instant now) {
        return isRunning() && !remaining(turnColor, now).isPositive();
    }

    /**
     * Record a move by the side to move and hand the turn to the other side.
     *
     * @return {@code false}, leaving the clock unchanged, if the side to move has already flagged
     * @throws IllegalStateException if the clock has been stopped
     */
    public boolean punch(Instant now) {
        requireNotStopped();
        if (hasFlagged(now)) {
            return false;
        }
        remainings.put(turnColor, remaining(turnColor, now).plus(clockSetting.increment()));
        if (isRunning() || turnColor == Color.BLACK) {
            turnStartAt = now;
        }
        turnColor = turnColor.opposite();
        return true;
    }

    /**
     * Freeze both players' time when the game ends. A flagged player is left with zero. Stopping twice has no
     * further effect.
     */
    public void stop(Instant now) {
        if (stopped) {
            return;
        }
        Duration remaining = remaining(turnColor, now);
        remainings.put(turnColor, remaining.isNegative() ? Duration.ZERO : remaining);
        turnStartAt = null;
        stopped = true;
    }

    public void addTime(Color color, Duration time) {
        requireNotStopped();
        if (!time.isPositive()) {
            throw new IllegalArgumentException("added time must be positive");
        }
        remainings.merge(color, time, Duration::plus);
    }

    public boolean isRunning() {
        return turnStartAt != null;
    }

    public boolean isStopped() {
        return stopped;
    }

    public ClockSetting getClockSetting() {
        return clockSetting;
    }

    public Color getTurnColor() {
        return turnColor;
    }

    public @Nullable Instant getTurnStartAt() {
        return turnStartAt;
    }

    /**
     * White's time as of White's last move or the clock stopping. Use {@link #remaining} for the live value.
     */
    public Duration getWhiteRemaining() {
        return Objects.requireNonNull(remainings.get(Color.WHITE));
    }

    /**
     * Black's time as of Black's last move or the clock stopping. Use {@link #remaining} for the live value.
     */
    public Duration getBlackRemaining() {
        return Objects.requireNonNull(remainings.get(Color.BLACK));
    }

    private void requireNotStopped() {
        if (stopped) {
            throw new IllegalStateException("Clock has been stopped");
        }
    }
}
