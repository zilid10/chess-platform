package me.zilid.chessplatform.rating.elo;

import me.zilid.chessplatform.rating.PlayerRating;

public class EloKFactorPolicy implements KFactorPolicy {
    private static final int NEW_PLAYER_K = 40;
    private static final int NORMAL_PLAYER_K = 20;
    private static final int ELITE_PLAYER_K = 10;


    @Override
    public int kFor(PlayerRating playerRating) {
        if (playerRating.gamesPlayed() < 20) {
            return NEW_PLAYER_K;
        }
        if (playerRating.rating() < 2100) {
            return NORMAL_PLAYER_K;
        }
        return ELITE_PLAYER_K;
    }
}
