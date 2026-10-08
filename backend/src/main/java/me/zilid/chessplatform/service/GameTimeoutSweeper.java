package me.zilid.chessplatform.service;

import java.time.Clock;
import java.util.UUID;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.repository.game.GameStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ends games whose time limit has passed even when nobody acts on them, such as a player who never moves again. Every
 * backend instance runs the sweep; {@link MatchService#checkTimeout} makes sure each game ends once.
 */
@Component
@ConditionalOnProperty(name = "app.game.timeout-sweep.enabled", havingValue = "true", matchIfMissing = true)
public class GameTimeoutSweeper {
    private static final Logger logger = LoggerFactory.getLogger(GameTimeoutSweeper.class);
    private static final int BATCH_SIZE = 100;

    private final GameStateStore gameStateStore;
    private final MatchService matchService;
    private final GameEventPublisher publisher;
    private final Clock clock;

    public GameTimeoutSweeper(
            GameStateStore gameStateStore, MatchService matchService, GameEventPublisher publisher, Clock clock) {
        this.gameStateStore = gameStateStore;
        this.matchService = matchService;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.game.timeout-sweep.interval:250ms}")
    public void sweep() {
        for (UUID gameId : gameStateStore.findTimeoutsDue(clock.instant(), BATCH_SIZE)) {
            try {
                Game game = matchService.getGameSession(gameId);
                if (game == null || game.isGameOver()) {
                    // The game expired from Redis or has already ended; nothing is left to end
                    gameStateStore.clearTimeoutDeadline(gameId);
                    continue;
                }
                // Another instance or a player's action may have ended the game first; then there is nothing to send
                matchService.checkTimeout(gameId).ifPresent(state -> publisher.publishUpdate(gameId, state));
            } catch (RuntimeException e) {
                // A busy lock or a Redis hiccup: the deadline stays in the index, so the next sweep retries
                logger.warn("Timeout check failed for game {}", gameId, e);
            }
        }
    }
}
