package me.zilid.chessplatform.chess.game;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.Move;
import me.zilid.chessplatform.chess.game.clock.TimeControl;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The minimal state needed to rebuild a {@link Game}: the position, undo history and repetition
 * counts are derived by replaying {@code history} from the starting position.
 */
public record GameSnapshot(
        // move history
        List<Move> history,

        // game metadata
        Instant startTime,
        @Nullable Instant endTime,
        TimeControl timeControl,
        GameStatus status,
        @Nullable UUID whitePlayerId,
        @Nullable UUID blackPlayerId,
        @Nullable Color drawOfferedBy
) {
}
