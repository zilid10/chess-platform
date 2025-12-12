package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.engine.pieces.Piece;

import java.util.UUID;

public record GameCreatedResponse(UUID gameId, Piece.Color color, String fen, String socketUrl) {
}
