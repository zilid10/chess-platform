package me.zilid.chessplatform.service;

import me.zilid.chessplatform.engine.Game;
import me.zilid.chessplatform.model.converter.MatchRecordConverter;
import me.zilid.chessplatform.model.dto.MatchRecordResponse;
import me.zilid.chessplatform.model.entity.MatchRecord;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.MatchRecordRepo;
import me.zilid.chessplatform.repository.UserRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class MatchService {

    private final MatchRecordRepo matchRecordRepo;
    private final MatchRecordConverter matchRecordConverter;
    private final ConcurrentMap<UUID, Game> gameSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Boolean>> playerConnections = new ConcurrentHashMap<>();
    private final UserRepo userRepo;


    public MatchService(MatchRecordRepo matchRecordRepo, MatchRecordConverter matchRecordConverter, UserRepo userRepo) {
        this.matchRecordRepo = matchRecordRepo;
        this.matchRecordConverter = matchRecordConverter;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public Page<MatchRecordResponse> findMatches(UUID userId, Pageable pageable) {
        Page<MatchRecord> games = matchRecordRepo.findByWhitePlayer_IdOrBlackPlayer_Id(userId, userId, pageable);
        return games.map(matchRecordConverter::toResponse);
    }

    @Transactional(readOnly = true)
    public String getMatchPGN(UUID matchId) {
        MatchRecord matchRecord = matchRecordRepo.findById(matchId).orElseThrow(() -> new IllegalArgumentException("Game with id " + matchId + " does not exist"));
        return matchRecord.getPgn();
    }

    @Transactional
    public void archiveMatch(UUID matchId, Game game) {
        if (!game.isGameOver()) {
            throw new IllegalStateException("Game is not over");
        }
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
    }

    /**
     * Get a game session (useful for testing or administrative purposes)
     */
    public Game getGameSession(UUID gameId) {
        return gameSessions.getOrDefault(gameId, null);
    }

    public Game getOrCreateGameSession(UUID gameId) {
        return gameSessions.computeIfAbsent(gameId, (k) -> new Game());
    }

    /**
     * Remove a game session (cleanup after game completion)
     */
    public void removeGameSession(UUID gameId) {
        gameSessions.remove(gameId);
        playerConnections.remove(gameId);
    }

    /**
     * Get connection status for a player in a game
     */
    public boolean isPlayerConnected(UUID gameId, UUID userId) {
        ConcurrentMap<UUID, Boolean> gameConnections = playerConnections.get(gameId);
        if (gameConnections == null) {
            return false;
        }
        return gameConnections.getOrDefault(userId, false);
    }

    /**
     * Get all player connection statuses for a game
     */
    public ConcurrentMap<UUID, Boolean> getGameConnectionStatus(UUID gameId) {
        return playerConnections.getOrDefault(gameId, null);
    }

    public ConcurrentMap<UUID, Boolean> getOrCreateGameConnectionStatus(UUID gameId) {
        return playerConnections.computeIfAbsent(gameId, (k) -> new ConcurrentHashMap<>());
    }

    /**
     * Mark a player as connected in a game
     */
    public void setPlayerConnected(UUID gameId, UUID userId, boolean connected) {
        ConcurrentMap<UUID, Boolean> gameConnections = playerConnections.computeIfAbsent(
            gameId, 
            k -> new ConcurrentHashMap<>()
        );
        gameConnections.put(userId, connected);
    }

    /**
     * Get all player connections map (for internal use)
     */
    public Map<UUID, ConcurrentMap<UUID, Boolean>> getAllPlayerConnections() {
        return playerConnections;
    }
}
