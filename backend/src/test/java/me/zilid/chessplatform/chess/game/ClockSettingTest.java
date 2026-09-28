package me.zilid.chessplatform.chess.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClockSettingTest {

    private static ClockSetting setting(long initialSeconds, long incrementSeconds) {
        return new ClockSetting(Duration.ofSeconds(initialSeconds), Duration.ofSeconds(incrementSeconds));
    }

    @ParameterizedTest
    @CsvSource({
            "-15, 0",
            "0, -1",
            "0, 0",
            "10815, 0", // 180 min 15 s
            "60, 181",
            "20, 0", // not a multiple of 15 s
    })
    void rejectsInvalidSettings(long initialSeconds, long incrementSeconds) {
        assertThatThrownBy(() -> setting(initialSeconds, incrementSeconds))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFractionalIncrement() {
        assertThatThrownBy(() -> new ClockSetting(Duration.ofMinutes(1), Duration.ofMillis(1500)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsLimitsAndZeroInitialWithIncrement() {
        assertThat(setting(0, 1).initial()).isZero();
        assertThat(setting(180 * 60, 180).category()).isEqualTo(TimeControl.CLASSICAL);
    }

    @ParameterizedTest
    @CsvSource({
            // estimated seconds = initial + 40 * increment, on each side of every threshold
            "135, 1, BULLET", // 175
            "180, 0, BLITZ", // 180
            "435, 1, BLITZ", // 475
            "480, 0, RAPID", // 480
            "1455, 1, RAPID", // 1495
            "1500, 0, CLASSICAL", // 1500
    })
    void categoryFollowsEstimatedGameLength(long initialSeconds, long incrementSeconds, TimeControl expected) {
        assertThat(setting(initialSeconds, incrementSeconds).category()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "300, 3, 5+3",
            "60, 0, 1+0",
            "30, 0, 0.5+0",
            "90, 2, 1.5+2",
            "15, 0, 0.25+0",
    })
    void toStringIsDistinctPerSetting(long initialSeconds, long incrementSeconds, String expected) {
        assertThat(setting(initialSeconds, incrementSeconds)).hasToString(expected);
    }

    @Test
    void ofMinutesTakesIncrementInSeconds() {
        assertThat(ClockSetting.ofMinutes(5, 3)).isEqualTo(setting(300, 3));
    }
}
