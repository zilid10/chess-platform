package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.chess.GameStatus;

import java.util.UUID;

public record GameJoinResponse(UUID gameId, String role, String fen, GameStatus status, String currentTurn) {
}
