package me.zilid.chessplatform.rating.elo;

import me.zilid.chessplatform.rating.GameOutcome;
import me.zilid.chessplatform.rating.PlayerRating;
import me.zilid.chessplatform.rating.RatingChange;
import me.zilid.chessplatform.rating.RatingSystem;

public class EloRatingSystem implements RatingSystem {
    private static final double SCALE = 400;
    private static final int FLOOR = 100;
    private final KFactorPolicy kFactorPolicy;

    public EloRatingSystem(KFactorPolicy kFactorPolicy) {
        this.kFactorPolicy = kFactorPolicy;
    }

    @Override
    public RatingChange apply(PlayerRating whitePlayerRating, PlayerRating blackPlayerRating, GameOutcome outcome) {
        int whiteK = kFactorPolicy.kFor(whitePlayerRating);
        int blackK = kFactorPolicy.kFor(blackPlayerRating);
        int whiteRating = whitePlayerRating.rating();
        int blackRating = blackPlayerRating.rating();
        double whiteScore = outcome.whiteScore();
        double blackScore = outcome.blackScore();
        double whiteExpected = 1.0 / (1 + Math.pow(10.0, (blackRating - whiteRating) / SCALE));
        double blackExpected = 1 - whiteExpected;
        int whiteDelta = (int) Math.round(whiteK * (whiteScore - whiteExpected));
        int blackDelta = (int) Math.round(blackK * (blackScore - blackExpected));

        int newWhiteRating = Math.max(whiteRating + whiteDelta, FLOOR);
        int newBlackRating = Math.max(blackRating + blackDelta, FLOOR);
        return new RatingChange(
                whitePlayerRating.playerId(),
                blackPlayerRating.playerId(),
                newWhiteRating,
                newBlackRating,
                newWhiteRating - whiteRating,
                newBlackRating - blackRating
        );
    }
}
