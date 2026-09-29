package me.zilid.chessplatform.repository;

import jakarta.persistence.EntityManager;
import me.zilid.chessplatform.chess.game.TimeControl;
import me.zilid.chessplatform.model.entity.MatchRecord;
import me.zilid.chessplatform.model.entity.Rating;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.rating.RatingChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:postgresql:.*")
class RatingRepoIntegrationTest {

    @Autowired
    private RatingRepo ratingRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private MatchRecordRepo matchRecordRepo;

    @Autowired
    private EntityManager entityManager;

    @Test
    void registeringCreatesADefaultRatingForEveryTimeControl() {
        User user = saveUser("alice");
        entityManager.flush();
        entityManager.clear();

        assertThat(ratingRepo.findByUser_Id(user.getId()))
                .extracting(Rating::getTimeControl)
                .containsExactlyInAnyOrder(TimeControl.values());
        assertThat(ratingRepo.findByUser_Id(user.getId()))
                .allSatisfy(rating -> {
                    assertThat(rating.getRating()).isEqualTo(Rating.DEFAULT_RATING);
                    assertThat(rating.getGamesPlayed()).isZero();
                });
    }

    @Test
    void lockedRatingUpdatesArePersisted() {
        User user = saveUser("bob");
        entityManager.flush();
        entityManager.clear();

        Rating blitz = ratingRepo.findForUpdate(user.getId(), TimeControl.BLITZ).orElseThrow();
        blitz.applyChanges(1234);
        entityManager.flush();
        entityManager.clear();

        Rating reloaded = ratingRepo.findForUpdate(user.getId(), TimeControl.BLITZ).orElseThrow();
        assertThat(reloaded.getRating()).isEqualTo(1234);
        assertThat(reloaded.getPeakRating()).isEqualTo(1234);
        assertThat(reloaded.getGamesPlayed()).isEqualTo(1);
        assertThat(ratingRepo.findForUpdate(UUID.randomUUID(), TimeControl.BLITZ)).isEmpty();
    }

    @Test
    void matchRecordKeepsItsRatingSnapshot() {
        User white = saveUser("carol");
        User black = saveUser("dave");
        MatchRecord match = new MatchRecord(white, black, "1-0", "CHECKMATE", "1. e4 e5",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:30:00Z"));
        match.setTimeControl(TimeControl.RAPID);
        match.setRatingChange(new RatingChange(white.getId(), black.getId(), 1220, 1180, 20, -20));
        matchRecordRepo.save(match);
        entityManager.flush();
        entityManager.clear();

        MatchRecord reloaded = matchRecordRepo.findById(match.getId()).orElseThrow();
        assertThat(reloaded.getTimeControl()).isEqualTo(TimeControl.RAPID);
        assertThat(reloaded.getWhiteRating()).isEqualTo(1220);
        assertThat(reloaded.getBlackRating()).isEqualTo(1180);
        assertThat(reloaded.getWhiteRatingChange()).isEqualTo(20);
        assertThat(reloaded.getBlackRatingChange()).isEqualTo(-20);
    }

    private User saveUser(String name) {
        String uniqueName = name + "-" + UUID.randomUUID();
        return userRepo.save(new User(uniqueName + "@example.com", uniqueName, "hash", null));
    }
}
