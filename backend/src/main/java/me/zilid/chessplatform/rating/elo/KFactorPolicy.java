package me.zilid.chessplatform.rating.elo;

import me.zilid.chessplatform.rating.PlayerRating;

public interface KFactorPolicy {
    int kFor(PlayerRating playerRating);
}
