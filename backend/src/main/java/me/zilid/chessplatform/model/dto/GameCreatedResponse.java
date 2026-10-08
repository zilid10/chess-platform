package me.zilid.chessplatform.model.dto;

import java.util.UUID;
import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.game.TimeControl;

/**
 * @param clockSetting initial minutes and increment seconds, such as "5+3"
 * @param timeControl the rating category of {@code clockSetting}
 */
public record GameCreatedResponse(
        UUID gameId, Color color, String clockSetting, TimeControl timeControl, String fen, String socketUrl) {}
