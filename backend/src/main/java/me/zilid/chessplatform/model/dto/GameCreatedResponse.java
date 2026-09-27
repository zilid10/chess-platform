package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.game.TimeControl;

import java.util.UUID;

public record GameCreatedResponse(UUID gameId, Color color, TimeControl timeControl, String fen, String socketUrl) {
}
