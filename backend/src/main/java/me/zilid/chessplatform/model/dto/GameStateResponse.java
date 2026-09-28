package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.chess.game.GameStatus;
import org.jspecify.annotations.Nullable;

/**
 * @param whiteRemainingMillis White's time left when this response was built
 * @param blackRemainingMillis Black's time left when this response was built
 * @param clockRunning         whether the side to move is losing time; false before Black's first move and once the
 *                             game is over
 */
public record GameStateResponse(GameStatus gameStatus,
                                String fen,
                                @Nullable String lastMoveFrom,
                                @Nullable String lastMoveTo,
                                String turnColor,
                                long whiteRemainingMillis,
                                long blackRemainingMillis,
                                boolean clockRunning) {
}
