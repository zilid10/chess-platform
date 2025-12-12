package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.engine.GameStatus;
import me.zilid.chessplatform.engine.Position;

public record GameStateResponse(GameStatus gameStatus, String fen, String lastMoveFrom, String lastMoveTo, String turnColor) {
}
