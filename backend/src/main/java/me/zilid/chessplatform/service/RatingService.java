package me.zilid.chessplatform.service;

import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.clock.TimeControl;
import me.zilid.chessplatform.exception.UserNotFoundException;
import me.zilid.chessplatform.model.dto.PlayerRatingResponse;
import me.zilid.chessplatform.model.entity.PlayerRating;
import me.zilid.chessplatform.rating.GameOutcome;
import me.zilid.chessplatform.rating.PlayerRatingDto;
import me.zilid.chessplatform.rating.RatingChange;
import me.zilid.chessplatform.rating.RatingSystem;
import me.zilid.chessplatform.repository.PlayerRatingRepo;
import me.zilid.chessplatform.repository.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RatingService {
    private static final Logger logger = LoggerFactory.getLogger(RatingService.class);

    private final PlayerRatingRepo playerRatingRepo;
    private final UserRepo userRepo;
    private final RatingSystem ratingSystem;

    public RatingService(PlayerRatingRepo playerRatingRepo, UserRepo userRepo, RatingSystem ratingSystem) {
        this.playerRatingRepo = playerRatingRepo;
        this.userRepo = userRepo;
        this.ratingSystem = ratingSystem;
    }

    static GameOutcome outcomeOf(GameStatus status) {
        if (status.isWhiteWin()) {
            return GameOutcome.WHITE_WINS;
        }
        if (status.isBlackWin()) {
            return GameOutcome.BLACK_WINS;
        }
        if (status.isDraw()) {
            return GameOutcome.DRAW;
        }
        throw new IllegalArgumentException("Game is not over");
    }

    private static PlayerRatingDto toDto(UUID userId, PlayerRating rating) {
        return new PlayerRatingDto(userId, rating.getRating(), rating.getGamesPlayed(), rating.getPeakRating());
    }

    /**
     * Apply a finished game's result to both players' ratings for its time control.
     * Runs inside the caller's transaction so the rating update and the match record commit together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RatingChange applyResult(UUID whiteId, UUID blackId, TimeControl timeControl, GameStatus status) {
        if (whiteId.equals(blackId)) {
            throw new IllegalArgumentException("A player cannot be rated against themselves");
        }
        GameOutcome outcome = outcomeOf(status);

        // Lock rows in a fixed order so two games sharing both players cannot deadlock.
        PlayerRating white;
        PlayerRating black;
        if (whiteId.compareTo(blackId) < 0) {
            white = lockRating(whiteId, timeControl);
            black = lockRating(blackId, timeControl);
        } else {
            black = lockRating(blackId, timeControl);
            white = lockRating(whiteId, timeControl);
        }

        RatingChange change = ratingSystem.apply(toDto(whiteId, white), toDto(blackId, black), outcome);
        white.applyChanges(change.whiteAfter());
        black.applyChanges(change.blackAfter());
        logger.info("{} ratings updated: white {} ({}), black {} ({})", timeControl,
                change.whiteAfter(), change.whiteDelta(), change.blackAfter(), change.blackDelta());
        return change;
    }

    /**
     * Get a user's rating for every time control, including ones they have not played yet.
     */
    @Transactional(readOnly = true)
    public List<PlayerRatingResponse> getRatings(UUID userId) {
        if (!userRepo.existsById(userId)) {
            throw new UserNotFoundException("User not found!");
        }
        Map<TimeControl, PlayerRating> ratings = playerRatingRepo.findByUser_Id(userId).stream()
                .collect(Collectors.toMap(PlayerRating::getTimeControl, Function.identity()));
        return Arrays.stream(TimeControl.values())
                .map(timeControl -> {
                    PlayerRating rating = ratings.get(timeControl);
                    if (rating == null) {
                        return new PlayerRatingResponse(timeControl, PlayerRating.DEFAULT_RATING, 0,
                                PlayerRating.DEFAULT_RATING);
                    }
                    return new PlayerRatingResponse(timeControl, rating.getRating(), rating.getGamesPlayed(),
                            rating.getPeakRating());
                })
                .toList();
    }

    private PlayerRating lockRating(UUID userId, TimeControl timeControl) {
        // Every user gets their rating rows at registration; this covers accounts that predate them.
        return playerRatingRepo.findForUpdate(userId, timeControl)
                .orElseGet(() -> playerRatingRepo.save(
                        new PlayerRating(userRepo.getReferenceById(userId), timeControl)));
    }
}
