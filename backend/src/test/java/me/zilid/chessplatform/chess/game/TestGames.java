package me.zilid.chessplatform.chess.game;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Builds and copies games for tests.
 */
public final class TestGames {
    public static final ClockSetting TEN_MINUTES = ClockSetting.ofMinutes(10, 0);

    private TestGames() {
    }

    public static Game game(@Nullable Player white, @Nullable Player black) {
        return new Game(white, black, TEN_MINUTES);
    }

    /**
     * An independent copy, as if the game had been stored and loaded again.
     */
    public static Game copy(Game game) {
        synchronized (game) {
            return Game.restore(game.getMoves(), game.getClockSetting(), game.getWhiteRemaining(),
                    game.getBlackRemaining(), game.getTurnStartAt(), game.getTurnColor(), game.getStartTime(),
                    game.getEndTime(), game.getStatus(), game.getWhitePlayer(), game.getBlackPlayer(),
                    game.getDrawOfferedBy(), game.getFirstMoveDeadline());
        }
    }

    public static Duration seconds(long seconds) {
        return Duration.ofSeconds(seconds);
    }
}
