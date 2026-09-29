package me.zilid.chessplatform.repository.game;

import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.Player;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
class ActiveGameStateConverter {

    private static @Nullable StoredPlayer store(@Nullable Player player) {
        return player == null ? null : StoredPlayer.of(player);
    }

    private static @Nullable Player load(@Nullable StoredPlayer player) {
        return player == null ? null : player.toPlayer();
    }

    ActiveGameState toState(Game game) {
        synchronized (game) {
            return new ActiveGameState(
                    new ArrayList<>(game.getMoves()),
                    game.getClockSetting(),
                    game.getWhiteRemaining(),
                    game.getBlackRemaining(),
                    game.getTurnStartAt(),
                    game.getTurnColor(),
                    game.getStartTime(),
                    game.getEndTime(),
                    game.getStatus(),
                    store(game.getWhitePlayer()),
                    store(game.getBlackPlayer()),
                    game.getDrawOfferedBy(),
                    game.getFirstMoveDeadline()
            );
        }
    }

    Game toGame(ActiveGameState state) {
        return Game.restore(state.moves(), state.clockSetting(), state.whiteRemaining(), state.blackRemaining(),
                state.turnStartAt(), state.turnColor(), state.startTime(), state.endTime(), state.status(),
                load(state.whitePlayer()), load(state.blackPlayer()), state.drawOfferedBy(), state.firstMoveDeadline());
    }
}
