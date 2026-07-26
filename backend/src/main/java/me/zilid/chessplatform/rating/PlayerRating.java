package me.zilid.chessplatform.rating;

public record PlayerRating(int rating,
                           int gamesPlayed,
                           int peakRating // reserved, used for Glicko-2 rating system in the future
) {
}
