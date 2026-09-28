package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.game.TimeControl;

import java.util.UUID;

/**
 * @param clockSetting initial minutes and increment seconds, such as "5+3"
 * @param timeControl  the rating category of {@code clockSetting}
 */
public record GameCreatedResponse(UUID gameId, Color color, String clockSetting, TimeControl timeControl, String fen,
                                  String socketUrl) {
}
