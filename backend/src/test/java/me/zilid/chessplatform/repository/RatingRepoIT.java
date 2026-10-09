package me.zilid.chessplatform.repository;

import static me.zilid.chessplatform.util.EntityFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import me.zilid.chessplatform.chess.game.TimeControl;
import me.zilid.chessplatform.model.entity.Rating;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.util.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@RepositoryTest
class RatingRepoIT {

    @Autowired
    private RatingRepo ratingRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void registeringCreatesADefaultRatingForEveryTimeControl() {
        User user = user(entityManager, "alice");
        entityManager.flush();
        entityManager.clear();

        assertThat(ratingRepo.findByUser_Id(user.getId()))
                .extracting(Rating::getTimeControl)
                .containsExactlyInAnyOrder(TimeControl.values());
        assertThat(ratingRepo.findByUser_Id(user.getId())).allSatisfy(rating -> {
            assertThat(rating.getRating()).isEqualTo(Rating.DEFAULT_RATING);
            assertThat(rating.getPeakRating()).isEqualTo(Rating.DEFAULT_RATING);
            assertThat(rating.getGamesPlayed()).isZero();
        });
    }

    @Test
    void findForUpdateLoadsOnlyTheRequestedRating() {
        User user = user(entityManager, "bob");
        user(entityManager, "carol");
        entityManager.flush();
        entityManager.clear();

        Rating blitz = ratingRepo.findForUpdate(user.getId(), TimeControl.BLITZ).orElseThrow();

        assertThat(blitz.getUser().getId()).isEqualTo(user.getId());
        assertThat(blitz.getTimeControl()).isEqualTo(TimeControl.BLITZ);
        assertThat(ratingRepo.findForUpdate(UUID.randomUUID(), TimeControl.BLITZ))
                .isEmpty();
    }

    @Test
    void changesToALockedRatingArePersisted() {
        User user = user(entityManager, "dave");
        entityManager.flush();
        entityManager.clear();

        ratingRepo.findForUpdate(user.getId(), TimeControl.BLITZ).orElseThrow().applyChanges(1234);
        entityManager.flush();
        entityManager.clear();

        Rating reloaded =
                ratingRepo.findForUpdate(user.getId(), TimeControl.BLITZ).orElseThrow();
        assertThat(reloaded.getRating()).isEqualTo(1234);
        assertThat(reloaded.getPeakRating()).isEqualTo(1234);
        assertThat(reloaded.getGamesPlayed()).isEqualTo(1);
    }

    // Row locks only matter between transactions, so this test commits its own and cleans up after itself
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void findForUpdateMakesAConcurrentGameWaitForTheRatingRow() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID userId = Objects.requireNonNull(
                transaction.execute(status -> user(entityManager, "eve").getId()));
        try {
            transaction.executeWithoutResult(status -> {
                assertThat(ratingRepo.findForUpdate(userId, TimeControl.BLITZ)).isPresent();

                CompletableFuture<Void> concurrentGame =
                        CompletableFuture.runAsync(() -> transaction.executeWithoutResult(other -> {
                            // Fail fast instead of waiting for the first transaction to end
                            entityManager
                                    .getEntityManager()
                                    .createNativeQuery("SET LOCAL lock_timeout = '200ms'")
                                    .executeUpdate();
                            ratingRepo.findForUpdate(userId, TimeControl.BLITZ);
                        }));

                assertThatThrownBy(concurrentGame::join)
                        .isInstanceOf(CompletionException.class)
                        .hasCauseInstanceOf(PessimisticLockingFailureException.class);
            });
        } finally {
            transaction.executeWithoutResult(status -> userRepo.deleteById(userId));
        }
    }
}
