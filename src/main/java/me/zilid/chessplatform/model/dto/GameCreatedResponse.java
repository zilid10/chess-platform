package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.chess.Color;

import java.util.UUID;

public record GameCreatedResponse(UUID gameId, Color color, String fen, String socketUrl) {
}
