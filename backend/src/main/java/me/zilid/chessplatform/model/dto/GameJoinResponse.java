package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.TimeControl;

import java.util.UUID;

public record GameJoinResponse(UUID gameId, String role, TimeControl timeControl, String fen, GameStatus status,
                               String currentTurn) {
}
