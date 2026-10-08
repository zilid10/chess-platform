package me.zilid.chessplatform.rating.elo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import me.zilid.chessplatform.rating.PlayerRating;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class EloKFactorPolicyTest {
    @ParameterizedTest
    @CsvSource({
        // gamesPlayed < 20 → new player, K=40
        "1000, 0,  1000, 40",
        "2500, 19, 2500, 40", // elite rating but still provisional
        // experienced + rating < 2100 → K=20
        "400, 20, 500, 20",
        "1000, 21, 1200, 20",
        "2099, 100, 2099, 20",
        // experienced + rating >= 2100 → elite, K=10
        "2100, 21, 2100, 10",
        "2500, 500, 2600, 10"
    })
    void testEloKFactoryPolicy(int rating, int gamesPlayed, int peakRating, int expectedK) {
        KFactorPolicy kFactorPolicy = new EloKFactorPolicy();
        PlayerRating playerRating = new PlayerRating(UUID.randomUUID(), rating, gamesPlayed, peakRating);
        int k = kFactorPolicy.kFor(playerRating);
        assertThat(k).isEqualTo(expectedK);
    }
}
