package me.zilid.chessplatform.rating.elo;

import me.zilid.chessplatform.rating.GameOutcome;
import me.zilid.chessplatform.rating.PlayerRatingDto;
import me.zilid.chessplatform.rating.RatingChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EloRatingSystemTest {

    // every player gets K=20, so tests don't depend on EloKFactorPolicy.
    private final EloRatingSystem elo = new EloRatingSystem(player -> 20);

    // gamesPlayed and peakRating don't matter here; only rating feeds the formula.
    private static PlayerRatingDto player(int rating) {
        return new PlayerRatingDto(UUID.randomUUID(), rating, 100, rating);
    }

    @ParameterizedTest
    @CsvSource({
            // white, black, outcome,     whiteDelta, blackDelta
            "1500,1500,WHITE_WINS,10,-10", // equal players: winner takes K/2
            "1500,1500,DRAW,0,0", // equal players, expected result: no change
            "2000,1800,WHITE_WINS,5,-5", // favorite wins: small shift
            "2000,1800,BLACK_WINS,-15,15", // upset: large shift
            "2000,1800,DRAW,-5,5", // favorite 'underperforms' by drawing
    })
    void ratingChangeMatchesHandComputedEloFormula(
            int whiteRating, int blackRating, GameOutcome outcome,
            int expectedWhiteDelta, int expectedBlackDelta) {
        RatingChange change = elo.apply(player(whiteRating), player(blackRating), outcome);

        assertThat(change.whiteDelta()).isEqualTo(expectedWhiteDelta);
        assertThat(change.blackDelta()).isEqualTo(expectedBlackDelta);
        assertThat(change.whiteAfter()).isEqualTo(whiteRating + expectedWhiteDelta);
        assertThat(change.blackAfter()).isEqualTo(blackRating + expectedBlackDelta);
    }

    @Test
    void ratingNeverDropsBelowFloor() {
        // 105-rated player loses 10 points -> would be 95, but the floor is 100.
        RatingChange change = elo.apply(player(105), player(105), GameOutcome.BLACK_WINS);

        assertThat(change.whiteAfter()).isEqualTo(100);
        // The reported delta must reflect the clamped rating, not the raw -10.
        assertThat(change.whiteDelta()).isEqualTo(-5);
        // The winner is unaffected by the loser's clamp.
        assertThat(change.blackAfter()).isEqualTo(115);
    }

    @Test
    void eachPlayerUsesTheirOwnKFactor() {
        // New players (few games) get K=40, established ones K=20.
        EloRatingSystem eloWithPerPlayerK =
                new EloRatingSystem(p -> p.gamesPlayed() < 20 ? 40 : 20);
        PlayerRatingDto newPlayer = new PlayerRatingDto(UUID.randomUUID(), 1500, 5, 1500);
        PlayerRatingDto establishedPlayer = new PlayerRatingDto(UUID.randomUUID(), 1500, 300, 1600);

        RatingChange change =
                eloWithPerPlayerK.apply(newPlayer, establishedPlayer, GameOutcome.WHITE_WINS);

        // Same expected score (0.5) but different K: 40*0.5 vs 20*0.5.
        assertThat(change.whiteDelta()).isEqualTo(20);
        assertThat(change.blackDelta()).isEqualTo(-10);
    }
}
