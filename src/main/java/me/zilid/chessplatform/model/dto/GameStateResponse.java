package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.chess.GameStatus;

public record GameStateResponse(GameStatus gameStatus, String fen, String lastMoveFrom, String lastMoveTo,
                                String turnColor) {
}
