package me.zilid.chessplatform.rating;

import java.util.UUID;

public record PlayerRatingDto(
        UUID playerId,
        int rating,
        int gamesPlayed,
        int peakRating // reserved, used for Glicko-2 rating system in the future
) {
}
