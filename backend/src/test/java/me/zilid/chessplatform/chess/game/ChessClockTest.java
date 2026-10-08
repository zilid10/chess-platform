package me.zilid.chessplatform.chess.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import me.zilid.chessplatform.chess.Color;
import org.junit.jupiter.api.Test;

class ChessClockTest {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final ClockSetting FIVE_PLUS_THREE = ClockSetting.ofMinutes(5, 3);

    private static Instant at(long seconds) {
        return T0.plusSeconds(seconds);
    }

    // Both first moves at T0, so White's clock runs from T0 with 5:03.
    private static ChessClock startedClock(ClockSetting setting) {
        ChessClock clock = new ChessClock(setting);
        assertThat(clock.punch(T0)).isTrue();
        assertThat(clock.punch(T0)).isTrue();
        return clock;
    }

    @Test
    void clockDoesNotRunBeforeBothFirstMoves() {
        ChessClock clock = new ChessClock(FIVE_PLUS_THREE);

        assertThat(clock.remaining(Color.WHITE, at(3600))).isEqualTo(Duration.ofMinutes(5));
        assertThat(clock.hasFlagged(at(3600))).isFalse();

        clock.punch(at(3600));
        assertThat(clock.isRunning()).isFalse();
        assertThat(clock.remaining(Color.BLACK, at(7200))).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void firstMovesUseNoTimeButEarnIncrement() {
        ChessClock clock = new ChessClock(FIVE_PLUS_THREE);

        clock.punch(T0);
        clock.punch(at(10));

        Duration fiveThree = Duration.ofSeconds(303);
        assertThat(clock.getWhiteRemaining()).isEqualTo(fiveThree);
        assertThat(clock.getBlackRemaining()).isEqualTo(fiveThree);
        assertThat(clock.getTurnColor()).isEqualTo(Color.WHITE);
        assertThat(clock.getTurnStartAt()).isEqualTo(at(10));
    }

    @Test
    void onlySideToMoveLosesTime() {
        ChessClock clock = startedClock(FIVE_PLUS_THREE);

        assertThat(clock.remaining(Color.WHITE, at(20))).isEqualTo(Duration.ofSeconds(283));
        assertThat(clock.remaining(Color.BLACK, at(20))).isEqualTo(Duration.ofSeconds(303));
    }

    @Test
    void punchDeductsElapsedTimeAndAddsIncrement() {
        ChessClock clock = startedClock(FIVE_PLUS_THREE);

        assertThat(clock.punch(at(20))).isTrue();

        assertThat(clock.getWhiteRemaining()).isEqualTo(Duration.ofSeconds(303 - 20 + 3));
        assertThat(clock.getTurnColor()).isEqualTo(Color.BLACK);
        assertThat(clock.getTurnStartAt()).isEqualTo(at(20));
    }

    @Test
    void flagsAtExactlyZero() {
        ChessClock clock = startedClock(ClockSetting.ofMinutes(1, 0));

        assertThat(clock.hasFlagged(at(60).minusMillis(1))).isFalse();
        assertThat(clock.hasFlagged(at(60))).isTrue();
    }

    @Test
    void punchAfterFlagIsRefusedAndLeavesClockUnchanged() {
        ChessClock clock = startedClock(FIVE_PLUS_THREE);

        assertThat(clock.punch(at(303))).isFalse();

        assertThat(clock.getWhiteRemaining()).isEqualTo(Duration.ofSeconds(303));
        assertThat(clock.getTurnColor()).isEqualTo(Color.WHITE);
        assertThat(clock.getTurnStartAt()).isEqualTo(T0);
    }

    @Test
    void timeBeforeTurnStartDoesNotAddTime() {
        ChessClock clock = startedClock(FIVE_PLUS_THREE);

        assertThat(clock.remaining(Color.WHITE, T0.minusSeconds(5))).isEqualTo(Duration.ofSeconds(303));

        clock.punch(T0.minusSeconds(5));
        assertThat(clock.getWhiteRemaining()).isEqualTo(Duration.ofSeconds(306));
    }

    @Test
    void stopFreezesRemainingTime() {
        ChessClock clock = startedClock(FIVE_PLUS_THREE);

        clock.stop(at(30));

        assertThat(clock.isStopped()).isTrue();
        assertThat(clock.isRunning()).isFalse();
        assertThat(clock.remaining(Color.WHITE, at(3600))).isEqualTo(Duration.ofSeconds(273));
        assertThat(clock.hasFlagged(at(3600))).isFalse();

        clock.stop(at(60));
        assertThat(clock.getWhiteRemaining()).isEqualTo(Duration.ofSeconds(273));
    }

    @Test
    void stopAfterFlagLeavesZero() {
        ChessClock clock = startedClock(FIVE_PLUS_THREE);

        clock.stop(at(400));

        assertThat(clock.getWhiteRemaining()).isZero();
    }

    @Test
    void stoppedClockRejectsChanges() {
        ChessClock clock = startedClock(FIVE_PLUS_THREE);
        clock.stop(at(30));

        assertThatThrownBy(() -> clock.punch(at(31))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> clock.addTime(Color.BLACK, Duration.ofSeconds(15)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void addTimeRequiresPositiveDuration() {
        ChessClock clock = startedClock(FIVE_PLUS_THREE);

        clock.addTime(Color.BLACK, Duration.ofSeconds(15));
        assertThat(clock.getBlackRemaining()).isEqualTo(Duration.ofSeconds(318));

        assertThatThrownBy(() -> clock.addTime(Color.BLACK, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> clock.addTime(Color.BLACK, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoredClockBehavesLikeOriginal() {
        ChessClock original = startedClock(FIVE_PLUS_THREE);
        original.punch(at(20));

        ChessClock restored = ChessClock.restore(
                original.getClockSetting(),
                original.getWhiteRemaining(),
                original.getBlackRemaining(),
                original.getTurnColor(),
                original.getTurnStartAt(),
                original.isStopped());

        for (Color color : Color.values()) {
            assertThat(restored.remaining(color, at(50))).isEqualTo(original.remaining(color, at(50)));
        }
        assertThat(restored.punch(at(50))).isEqualTo(original.punch(at(50)));
        assertThat(restored.getBlackRemaining()).isEqualTo(original.getBlackRemaining());
    }

    @Test
    void restoredStoppedClockStaysStopped() {
        ChessClock restored = ChessClock.restore(
                FIVE_PLUS_THREE, Duration.ofSeconds(10), Duration.ofSeconds(20), Color.WHITE, null, true);

        assertThat(restored.hasFlagged(at(3600))).isFalse();
        assertThatThrownBy(() -> restored.punch(at(3600))).isInstanceOf(IllegalStateException.class);
    }
}
