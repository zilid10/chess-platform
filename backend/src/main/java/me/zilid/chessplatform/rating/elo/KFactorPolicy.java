package me.zilid.chessplatform.rating.elo;

import me.zilid.chessplatform.rating.PlayerRatingDto;

public interface KFactorPolicy {
    int kFor(PlayerRatingDto playerRating);
}
