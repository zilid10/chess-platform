package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.chess.game.GameStatus;
import org.jspecify.annotations.Nullable;

public record GameStateResponse(GameStatus gameStatus,
                                String fen,
                                @Nullable String lastMoveFrom,
                                @Nullable String lastMoveTo,
                                String turnColor) {
}
