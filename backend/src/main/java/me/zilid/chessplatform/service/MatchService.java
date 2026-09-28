package me.zilid.chessplatform.service;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.Player;
import me.zilid.chessplatform.chess.game.TimeControl;
import me.zilid.chessplatform.exception.GameIsOverException;
import me.zilid.chessplatform.exception.GameNotFoundException;
import me.zilid.chessplatform.model.converter.ActiveGameStateConverter;
import me.zilid.chessplatform.model.converter.MatchRecordConverter;
import me.zilid.chessplatform.model.dto.ActiveGameState;
import me.zilid.chessplatform.model.dto.GameCreatedResponse;
import me.zilid.chessplatform.model.dto.GameJoinResponse;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MatchRecordResponse;
import me.zilid.chessplatform.model.entity.MatchRecord;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.rating.RatingChange;
import me.zilid.chessplatform.repository.GameStateStore;
import me.zilid.chessplatform.repository.MatchRecordRepo;
import me.zilid.chessplatform.repository.UserRepo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

@Service
public class MatchService {

    private static final Logger logger = LoggerFactory.getLogger(MatchService.class);
    private static final Duration FINISHED_GAME_TTL = Duration.ofMinutes(1);

    private final MatchRecordConverter matchRecordConverter;
    private final MatchRecordRepo matchRecordRepo;
    private final UserRepo userRepo;
    private final RatingService ratingService;
    private final GameStateStore gameStateStore;
    private final ActiveGameStateConverter activeGameStateConverter;

    public MatchService(MatchRecordRepo matchRecordRepo, MatchRecordConverter matchRecordConverter, UserRepo userRepo,
                        RatingService ratingService, GameStateStore gameStateStore,
                        ActiveGameStateConverter activeGameStateConverter) {
        this.matchRecordRepo = matchRecordRepo;
        this.matchRecordConverter = matchRecordConverter;
        this.userRepo = userRepo;
        this.ratingService = ratingService;
        this.gameStateStore = gameStateStore;
        this.activeGameStateConverter = activeGameStateConverter;
    }

    @Transactional(readOnly = true)
    public Page<MatchRecordResponse> findMatches(UUID userId, Pageable pageable) {
        logger.debug("Finding matches for user: {}", userId);
        pageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "endTime")
        );

        Page<MatchRecord> games = matchRecordRepo.findByWhitePlayer_IdOrBlackPlayer_Id(userId, userId, pageable);
        logger.debug("Found {} matches for user {}", games.getTotalElements(), userId);
        return games.map(matchRecordConverter::toResponse);
    }

    @Transactional(readOnly = true)
    public String getMatchPGN(UUID matchId) {
        logger.debug("Fetching PGN for match: {}", matchId);
        MatchRecord matchRecord = matchRecordRepo.findById(matchId).orElseThrow(() -> new GameNotFoundException("Game with id " + matchId + " does not exist"));
        return matchRecord.getPgn();
    }

    public GameCreatedResponse createGame(Player currentUser, Color color, TimeControl timeControl) {
        UUID gameId = UUID.randomUUID();
        logger.info("Creating new {} game {} for user {} with color {}",
                timeControl, gameId, currentUser.displayName(), color);
        Game game = color.isWhite()
                ? new Game(currentUser, null, timeControl)
                : new Game(null, currentUser, timeControl);
        saveGame(gameId, game);

        return new GameCreatedResponse(
                gameId,
                color,
                timeControl,
                game.getFen(),
                "/game/" + gameId
        );
    }

    public GameJoinResponse joinGame(UUID gameId, Player currentUser) {
        logger.info("User {} attempting to join game {}", currentUser.displayName(), gameId);
        return updateGame(gameId, game -> {
            String role;
            if (currentUser.equals(game.getWhitePlayer())) {
                role = "WHITE"; // reconnect
                logger.debug("User {} reconnecting as WHITE to game {}", currentUser.displayName(), gameId);
            } else if (currentUser.equals(game.getBlackPlayer())) {
                role = "BLACK"; // reconnect
                logger.debug("User {} reconnecting as BLACK to game {}", currentUser.displayName(), gameId);
            } else if (game.getWhitePlayer() == null) {
                game.setWhitePlayer(currentUser);
                role = "WHITE";
                logger.info("User {} joined game {} as WHITE", currentUser.displayName(), gameId);
            } else if (game.getBlackPlayer() == null) {
                game.setBlackPlayer(currentUser);
                role = "BLACK";
                logger.info("User {} joined game {} as BLACK", currentUser.displayName(), gameId);
            } else {
                role = "SPECTATOR"; // spectator
                logger.info("User {} joined game {} as SPECTATOR", currentUser.displayName(), gameId);
            }

            return new GameJoinResponse(
                    gameId,
                    role,
                    game.getTimeControl(),
                    game.getFen(),
                    game.getStatus(),
                    game.getTurnColor().name()
            );
        });
    }

    public GameStateResponse makeMove(Player currentUser, UUID gameId,
                                      String moveFrom, String moveTo, @Nullable PieceType promotion) {
        return updateGame(gameId, game -> {
            requirePlayer(game, currentUser);
            if (game.isGameOver()) {
                throw new IllegalStateException("Game is already over");
            }
            if (!game.isUserTurn(currentUser)) {
                throw new IllegalStateException("It is not your turn");
            }
            if (!game.makeMove(moveFrom, moveTo, promotion)) {
                throw new IllegalArgumentException("Invalid move: " + moveFrom + " to " + moveTo);
            }
            logger.info("Move executed in game {}: {} to {}", gameId, moveFrom, moveTo);
            return buildGameStateResponse(game);
        });
    }

    public void offerDraw(Player currentUser, UUID gameId) {
        logger.info("User {} offering draw in game {}", currentUser.displayName(), gameId);
        updateGame(gameId, game -> {
            Color color = playerColor(game, currentUser);
            if (game.isGameOver()) {
                throw new IllegalStateException("Game is over");
            }
            game.offerDraw(color);
            logger.info("Draw offered by {} in game {}", color, gameId);
            return color;
        });
    }

    public GameStateResponse acceptDraw(Player currentUser, UUID gameId) {
        return updateGame(gameId, game -> {
            Color color = playerColor(game, currentUser);
            if (game.isGameOver()) {
                logger.warn("Attempted draw acceptance on completed game {}", gameId);
                throw new GameIsOverException("Game is already over");
            }
            game.acceptDraw(color);
            return buildGameStateResponse(game);
        });
    }

    public GameStateResponse resign(Player currentUser, UUID gameId) {
        return updateGame(gameId, game -> {
            Color color = playerColor(game, currentUser);
            if (game.isGameOver()) {
                logger.warn("Attempted resignation on completed game {}", gameId);
                throw new GameIsOverException("Game is already over");
            }
            game.resign(color);
            logger.info("Player {} resigned in game {}", color, gameId);
            return buildGameStateResponse(game);
        });
    }

    public GameStateResponse getGameState(UUID gameId) {
        Game game = getGameOrThrow(gameId);

        return buildGameStateResponse(game);
    }

    /**
     * Build a GameStateResponse from the current game state
     */
    public GameStateResponse buildGameStateResponse(Game game) {
        synchronized (game) {
            return new GameStateResponse(
                    game.getStatus(),
                    game.getFen(),
                    game.getLastMoveFrom(),
                    game.getLastMoveTo(),
                    game.getTurnColor().name()
            );
        }
    }

    /**
     * Save the finished game and apply its result to both players' ratings in one transaction.
     */
    @Transactional
    public RatingChange archiveMatch(UUID matchId, Game game) {
        if (!game.isGameOver()) {
            throw new IllegalStateException("Game is not over");
        }
        Player white = game.getWhitePlayer();
        Player black = game.getBlackPlayer();
        Instant endTime = game.getEndTime();
        // A lone player can resign before an opponent joins; there is no match to record then
        if (white == null || black == null || endTime == null) {
            throw new IllegalStateException("Game " + matchId + " ended without both players seated");
        }
        logger.info("Archiving match {} with result: {}", matchId, game.getStatus().getSymbol());
        UUID whitePlayerId = white.id();
        UUID blackPlayerId = black.id();
        User whitePlayer = userRepo.getReferenceById(whitePlayerId);
        User blackPlayer = userRepo.getReferenceById(blackPlayerId);
        MatchRecord matchRecord = new MatchRecord(whitePlayer, blackPlayer, game.getStatus().getSymbol(),
                game.getStatus().getReason(), game.getNotation(), game.getStartTime(), endTime);
        matchRecord.setTimeControl(game.getTimeControl());
        RatingChange ratingChange = ratingService.applyResult(
                whitePlayerId, blackPlayerId, game.getTimeControl(), game.getStatus());
        matchRecord.setRatingChange(ratingChange);
        matchRecordRepo.save(matchRecord);
        logger.info("Match {} archived successfully", matchId);
        return ratingChange;
    }

    /**
     * Keep a finished game readable for a short while (late joiners, reconnects), then let Redis evict it
     */
    public void scheduleGameCleanup(UUID gameId) {
        gameStateStore.expireGame(gameId, FINISHED_GAME_TTL);
        logger.info("Game {} will be removed in {}", gameId, FINISHED_GAME_TTL);
    }

    /**
     * Load a game session (useful for testing or administrative purposes)
     */
    public @Nullable Game getGameSession(UUID gameId) {
        ActiveGameState state = gameStateStore.loadGame(gameId);
        return state == null ? null : activeGameStateConverter.toGame(state);
    }

    public Game getGameOrThrow(UUID gameId) {
        Game game = getGameSession(gameId);
        if (game == null) {
            throw new GameNotFoundException("Game not found: " + gameId);
        }
        return game;
    }

    /**
     * Load the game, apply {@code action} and store the result, all under the game's distributed lock.
     * Nothing is stored if {@code action} throws.
     */
    private <T> T updateGame(UUID gameId, Function<Game, T> action) {
        return gameStateStore.withLock(gameId, () -> {
            Game game = getGameOrThrow(gameId);
            T result = action.apply(game);
            saveGame(gameId, game);
            return result;
        });
    }

    private void saveGame(UUID gameId, Game game) {
        gameStateStore.storeGame(gameId, activeGameStateConverter.toState(game));
    }

    public void requirePlayer(Game game, Player user) {
        if (!game.isValidPlayer(user)) {
            throw new IllegalStateException("You are not a player in this game");
        }
    }

    private Color playerColor(Game game, Player user) {
        requirePlayer(game, user);
        Color color = game.getPlayerColor(user);
        if (color == null) {
            throw new IllegalStateException("You are not a player in this game");
        }
        return color;
    }
}
