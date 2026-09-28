package me.zilid.chessplatform.repository.game;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.Move;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.TimeControl;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Redis representation of a game in progress.
 */
record ActiveGameState(
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
