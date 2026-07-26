package me.zilid.chessplatform.rating;

public interface RatingSystem {
    RatingChange apply(PlayerRating whiteScore, PlayerRating blackScore, GameOutcome outcome);
}
