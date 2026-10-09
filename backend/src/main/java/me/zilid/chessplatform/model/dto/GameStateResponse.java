package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.chess.game.GameStatus;
import org.jspecify.annotations.Nullable;

/**
 * A response sent to the client to update the state of the game.
 *
 * @param whiteRemainingMillis White's time left when this response was built
 * @param blackRemainingMillis Black's time left when this response was built
 * @param clockRunning whether the side to move is losing time; false before Black's first move and once the game is
 *     over
 * @param firstMoveRemainingMillis before the clock starts, how long the side to move has left to make its first move
 *     before the game is aborted; null once the clock runs, while a seat is open, and after the game ends
 */
public record GameStateResponse(
        GameStatus gameStatus,
        String fen,
        @Nullable String lastMoveFrom,
        @Nullable String lastMoveTo,
        String turnColor,
        long whiteRemainingMillis,
        long blackRemainingMillis,
        boolean clockRunning,
        @Nullable Long firstMoveRemainingMillis) {}
