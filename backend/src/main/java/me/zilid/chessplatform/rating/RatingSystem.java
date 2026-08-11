package me.zilid.chessplatform.rating;

public interface RatingSystem {
    RatingChange apply(PlayerRatingDto whiteScore, PlayerRatingDto blackScore, GameOutcome outcome);
}
