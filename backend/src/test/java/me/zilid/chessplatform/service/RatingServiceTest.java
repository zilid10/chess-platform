package me.zilid.chessplatform.service;

import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.TimeControl;
import me.zilid.chessplatform.exception.UserNotFoundException;
import me.zilid.chessplatform.model.dto.PlayerRatingResponse;
import me.zilid.chessplatform.model.entity.PlayerRating;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.rating.GameOutcome;
import me.zilid.chessplatform.rating.RatingChange;
import me.zilid.chessplatform.rating.elo.EloKFactorPolicy;
import me.zilid.chessplatform.rating.elo.EloRatingSystem;
import me.zilid.chessplatform.repository.PlayerRatingRepo;
import me.zilid.chessplatform.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RatingServiceTest {
    // UUIDs compare by their signed high bits, so LOW sorts before HIGH.
    private static final UUID LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HIGH_ID = UUID.fromString("7fffffff-0000-0000-0000-000000000001");

    private PlayerRatingRepo playerRatingRepo;
    private UserRepo userRepo;
    private RatingService service;

    @BeforeEach
    void setUp() {
        playerRatingRepo = mock(PlayerRatingRepo.class);
        userRepo = mock(UserRepo.class);
        service = new RatingService(playerRatingRepo, userRepo, new EloRatingSystem(new EloKFactorPolicy()));
    }

    @Test
    void winUpdatesBothPlayersRatingsGamesAndPeak() {
        PlayerRating white = rating(TimeControl.BLITZ);
        PlayerRating black = rating(TimeControl.BLITZ);
        when(playerRatingRepo.findForUpdate(LOW_ID, TimeControl.BLITZ)).thenReturn(Optional.of(white));
        when(playerRatingRepo.findForUpdate(HIGH_ID, TimeControl.BLITZ)).thenReturn(Optional.of(black));

        RatingChange change = service.applyResult(LOW_ID, HIGH_ID, TimeControl.BLITZ, GameStatus.CHECKMATE_WHITE_WINS);

        // Both are new players (K=40) at 1200, so the winner takes K/2.
        assertThat(change).isEqualTo(new RatingChange(LOW_ID, HIGH_ID, 1220, 1180, 20, -20));
        assertThat(white.getRating()).isEqualTo(1220);
        assertThat(white.getPeakRating()).isEqualTo(1220);
        assertThat(white.getGamesPlayed()).isEqualTo(1);
        assertThat(black.getRating()).isEqualTo(1180);
        assertThat(black.getPeakRating()).isEqualTo(1200);
        assertThat(black.getGamesPlayed()).isEqualTo(1);
    }

    @Test
    void drawBetweenEqualPlayersStillCountsAsAGamePlayed() {
        PlayerRating white = rating(TimeControl.RAPID);
        PlayerRating black = rating(TimeControl.RAPID);
        when(playerRatingRepo.findForUpdate(LOW_ID, TimeControl.RAPID)).thenReturn(Optional.of(white));
        when(playerRatingRepo.findForUpdate(HIGH_ID, TimeControl.RAPID)).thenReturn(Optional.of(black));

        RatingChange change = service.applyResult(LOW_ID, HIGH_ID, TimeControl.RAPID, GameStatus.STALEMATE);

        assertThat(change.whiteDelta()).isZero();
        assertThat(change.blackDelta()).isZero();
        assertThat(white.getGamesPlayed()).isEqualTo(1);
        assertThat(black.getGamesPlayed()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void rowsAreLockedInIdOrderWhateverTheColors(boolean lowIdIsWhite) {
        when(playerRatingRepo.findForUpdate(any(), any())).thenAnswer(invocation ->
                Optional.of(rating(invocation.getArgument(1))));
        UUID white = lowIdIsWhite ? LOW_ID : HIGH_ID;
        UUID black = lowIdIsWhite ? HIGH_ID : LOW_ID;

        service.applyResult(white, black, TimeControl.BULLET, GameStatus.DRAW_BY_AGREEMENT);

        InOrder order = inOrder(playerRatingRepo);
        order.verify(playerRatingRepo).findForUpdate(LOW_ID, TimeControl.BULLET);
        order.verify(playerRatingRepo).findForUpdate(HIGH_ID, TimeControl.BULLET);
    }

    @Test
    void missingRatingRowIsCreatedAtTheDefaultRating() {
        User legacyUser = new User("old@example.com", "old", "hash", null);
        when(userRepo.getReferenceById(LOW_ID)).thenReturn(legacyUser);
        when(playerRatingRepo.findForUpdate(LOW_ID, TimeControl.CLASSICAL)).thenReturn(Optional.empty());
        when(playerRatingRepo.save(any(PlayerRating.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRatingRepo.findForUpdate(HIGH_ID, TimeControl.CLASSICAL))
                .thenReturn(Optional.of(rating(TimeControl.CLASSICAL)));

        RatingChange change = service.applyResult(LOW_ID, HIGH_ID, TimeControl.CLASSICAL,
                GameStatus.RESIGNED_BLACK_WINS);

        assertThat(change.whiteAfter()).isEqualTo(1180);
        assertThat(change.blackAfter()).isEqualTo(1220);
    }

    @Test
    void unfinishedGameIsNotRated() {
        assertThatThrownBy(() -> service.applyResult(LOW_ID, HIGH_ID, TimeControl.RAPID, GameStatus.ONGOING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Game is not over");
        verifyNoInteractions(playerRatingRepo);
    }

    @Test
    void playerCannotBeRatedAgainstThemselves() {
        assertThatThrownBy(() -> service.applyResult(LOW_ID, LOW_ID, TimeControl.RAPID, GameStatus.STALEMATE))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(playerRatingRepo);
    }

    @ParameterizedTest
    @EnumSource(value = GameStatus.class, names = "ONGOING", mode = EnumSource.Mode.EXCLUDE)
    void everyFinishedStatusMapsToTheMatchingOutcome(GameStatus status) {
        GameOutcome expected = status.isWhiteWin() ? GameOutcome.WHITE_WINS
                : status.isBlackWin() ? GameOutcome.BLACK_WINS
                : GameOutcome.DRAW;

        assertThat(RatingService.outcomeOf(status)).isEqualTo(expected);
    }

    @Test
    void ratingsCoverEveryTimeControlInOrder() {
        PlayerRating blitz = rating(TimeControl.BLITZ);
        blitz.applyChanges(1260);
        when(userRepo.existsById(LOW_ID)).thenReturn(true);
        when(playerRatingRepo.findByUser_Id(LOW_ID)).thenReturn(List.of(blitz));

        List<PlayerRatingResponse> ratings = service.getRatings(LOW_ID);

        assertThat(ratings).extracting(PlayerRatingResponse::timeControl).containsExactly(TimeControl.values());
        assertThat(ratings.get(TimeControl.BLITZ.ordinal()))
                .isEqualTo(new PlayerRatingResponse(TimeControl.BLITZ, 1260, 1, 1260));
        assertThat(ratings.get(TimeControl.RAPID.ordinal()))
                .isEqualTo(new PlayerRatingResponse(TimeControl.RAPID, 1200, 0, 1200));
    }

    @Test
    void ratingsOfUnknownUserAreNotFound() {
        when(userRepo.existsById(LOW_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getRatings(LOW_ID)).isInstanceOf(UserNotFoundException.class);
    }

    private static PlayerRating rating(TimeControl timeControl) {
        return new PlayerRating(new User("p@example.com", "p", "hash", null), timeControl);
    }
}
