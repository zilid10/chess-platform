package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.chess.game.TimeControl;

public record PlayerRatingResponse(TimeControl timeControl, int rating, int gamesPlayed, int peakRating) {
}
