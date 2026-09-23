package me.zilid.chessplatform.service;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.exception.GameIsOverException;
import me.zilid.chessplatform.exception.GameNotFoundException;
import me.zilid.chessplatform.model.converter.MatchRecordConverter;
import me.zilid.chessplatform.model.dto.GameCreatedResponse;
import me.zilid.chessplatform.model.dto.GameJoinResponse;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MatchRecordResponse;
import me.zilid.chessplatform.model.entity.MatchRecord;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.model.entity.UserPrincipal;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Service
public class MatchService {

    private static final Logger logger = LoggerFactory.getLogger(MatchService.class);

    private final MatchRecordConverter matchRecordConverter;
    private final MatchRecordRepo matchRecordRepo;
    private final UserRepo userRepo;

    private final ConcurrentMap<UUID, Game> gameSessions = new ConcurrentHashMap<>();

    public MatchService(MatchRecordRepo matchRecordRepo, MatchRecordConverter matchRecordConverter, UserRepo userRepo) {
        this.matchRecordRepo = matchRecordRepo;
        this.matchRecordConverter = matchRecordConverter;
        this.userRepo = userRepo;
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
        MatchRecord matchRecord = matchRecordRepo.findById(matchId).orElseThrow(() -> new IllegalArgumentException("Game with id " + matchId + " does not exist"));
        return matchRecord.getPgn();
    }

    public GameCreatedResponse createGame(UserPrincipal currentUser, Color color) {
        UUID gameId = UUID.randomUUID();
        logger.info("Creating new game {} for user {} with color {}", gameId, currentUser.getUsername(), color);
        Game game = getOrCreateGameSession(gameId);
        if (color.isWhite()) {
            game.setWhitePlayer(currentUser);
        } else {
            game.setBlackPlayer(currentUser);
        }

        return new GameCreatedResponse(
                gameId,
                color,
                game.getFen(),
                "/game/" + gameId
        );
    }

    public GameJoinResponse joinGame(UUID gameId, UserPrincipal currentUser) {
        logger.info("User {} attempting to join game {}", currentUser.getUsername(), gameId);
        Game game = getGameOrThrow(gameId);

        String role;
        synchronized (game) {
            if (currentUser.equals(game.getWhitePlayer())) {
                role = "WHITE"; // reconnect
                logger.debug("User {} reconnecting as WHITE to game {}", currentUser.getUsername(), gameId);
            } else if (currentUser.equals(game.getBlackPlayer())) {
                role = "BLACK"; // reconnect
                logger.debug("User {} reconnecting as BLACK to game {}", currentUser.getUsername(), gameId);
            } else if (game.getWhitePlayer() == null) {
                game.setWhitePlayer(currentUser);
                role = "WHITE";
                logger.info("User {} joined game {} as WHITE", currentUser.getUsername(), gameId);
            } else if (game.getBlackPlayer() == null) {
                game.setBlackPlayer(currentUser);
                role = "BLACK";
                logger.info("User {} joined game {} as BLACK", currentUser.getUsername(), gameId);
            } else {
                role = "SPECTATOR"; // spectator
                logger.info("User {} joined game {} as SPECTATOR", currentUser.getUsername(), gameId);
            }
        }

        return new GameJoinResponse(
                gameId,
                role,
                game.getFen(),
                game.getStatus(),
                game.getTurnColor().name()
        );
    }

    public void offerDraw(UserPrincipal currentUser, UUID gameId) {
        logger.info("User {} offering draw in game {}", currentUser.getUsername(), gameId);
        Game game = getGameOrThrow(gameId);
        requirePlayer(game, currentUser);

        if (game.isGameOver()) {
            throw new IllegalStateException("Game is over");
        }
        Color color = game.getPlayerColor(currentUser);
        if (color == null) {
            throw new IllegalStateException("You can't offer a draw");
        }
        game.offerDraw(color);
        logger.info("Draw offered by {} in game {}", color, gameId);
    }

    public GameStateResponse acceptDraw(UserPrincipal currentUser, UUID gameId) {
        Game game = getGameOrThrow(gameId);
        requirePlayer(game, currentUser);

        if (game.isGameOver()) {
            logger.warn("Attempted draw offer on completed game {}", gameId);
            throw new GameIsOverException("Game is already over");
        }

        Color color = game.getPlayerColor(currentUser);
        game.acceptDraw(color);

        return buildGameStateResponse(game);
    }

    public GameStateResponse resign(UserPrincipal currentUser, UUID gameId) {
        Game game = getGameOrThrow(gameId);
        requirePlayer(game, currentUser);

        if (game.isGameOver()) {
            logger.warn("Attempted resignation on completed game {}", gameId);
            throw new GameIsOverException("Game is already over");
        }

        // Parse player color and resign
        Color color = game.getPlayerColor(currentUser);
        if (color == null) {
            throw new IllegalStateException("you can't resign");
        }
        game.resign(color);

        logger.info("Player {} resigned in game {}", color, gameId);
        return buildGameStateResponse(game);
    }

    public GameStateResponse getGameState(UUID gameId) {
        Game game = getGameOrThrow(gameId);

        return buildGameStateResponse(game);
    }

    /**
     * Build a GameStateResponse from the current game state
     */
    public GameStateResponse buildGameStateResponse(Game game) {
        return new GameStateResponse(
                game.getStatus(),
                game.getFen(),
                game.getLastMoveFrom(),
                game.getLastMoveTo(),
                game.getTurnColor().name()
        );
    }

    @Transactional
    public void archiveMatch(UUID matchId, Game game) {
        if (!game.isGameOver()) {
            throw new IllegalStateException("Game is not over");
        }
        logger.info("Archiving match {} with result: {}", matchId, game.getStatus().getSymbol());
        MatchRecord matchRecord = new MatchRecord();
        UUID whitePlayerId = game.getWhitePlayer().getId();
        UUID blackPlayerId = game.getBlackPlayer().getId();
        User whitePlayer = userRepo.findById(whitePlayerId)
                .orElseThrow(() -> new IllegalArgumentException("White player with id " + whitePlayerId + " does not exist"));
        User blackPlayer = userRepo.findById(blackPlayerId)
                .orElseThrow(() -> new IllegalArgumentException("Black player with id " + blackPlayerId + " does not exist"));
        matchRecord.setWhitePlayer(whitePlayer);
        matchRecord.setBlackPlayer(blackPlayer);
        matchRecord.setPgn(game.getNotation());
        matchRecord.setMatchResult(game.getStatus().getSymbol());
        matchRecord.setReason(game.getStatus().getReason());
        matchRecord.setStartTime(game.getStartTime());
        matchRecord.setEndTime(game.getEndTime());
        matchRecordRepo.save(matchRecord);
        logger.info("Match {} archived successfully", matchId);
    }

    public void scheduleGameCleanup(UUID gameId) {
        CompletableFuture.delayedExecutor(1, TimeUnit.MINUTES)
                .execute(() -> {
                    removeGameSession(gameId);
                    logger.info("Cleaning up Game Session");
                });
    }

    /**
     * Get a game session (useful for testing or administrative purposes)
     */
    public @Nullable Game getGameSession(UUID gameId) {
        return gameSessions.get(gameId);
    }

    public Game getGameOrThrow(UUID gameId) {
        Game game = getGameSession(gameId);
        if (game == null) {
            throw new GameNotFoundException("Game not found: " + gameId);
        }
        return game;
    }

    public Game getOrCreateGameSession(UUID gameId) {
        return gameSessions.computeIfAbsent(gameId, (k) -> new Game());
    }

    public void removeGameSession(UUID gameId) {
        gameSessions.remove(gameId);
    }

    public void requirePlayer(Game game, UserPrincipal user) {
        if (!game.isValidPlayer(user)) {
            throw new IllegalStateException("You are not a player in this game");
        }
    }
}
