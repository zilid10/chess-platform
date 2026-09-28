package me.zilid.chessplatform.service;

import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.model.dto.ChatMessage;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.rating.RatingChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sends game updates to a game's subscribers and wraps up finished games, for player actions and server-side
 * timeouts alike.
 */
@Component
public class GameEventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(GameEventPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final MatchService matchService;

    public GameEventPublisher(SimpMessagingTemplate messagingTemplate, MatchService matchService) {
        this.messagingTemplate = messagingTemplate;
        this.matchService = matchService;
    }

    // Dot-separated so the names are valid RabbitMQ topics
    public static String gameTopic(UUID gameId) {
        return "/topic/game." + gameId;
    }

    public static String chatTopic(UUID gameId) {
        return "/topic/game." + gameId + ".chat";
    }

    private static String ratingSummary(Game game, RatingChange change) {
        String white = game.getWhitePlayer() == null ? "White" : game.getWhitePlayer().displayName();
        String black = game.getBlackPlayer() == null ? "Black" : game.getBlackPlayer().displayName();
        return "%s ratings: %s %d (%+d), %s %d (%+d)".formatted(
                game.getTimeControl(), white, change.whiteAfter(), change.whiteDelta(),
                black, change.blackAfter(), change.blackDelta());
    }

    public void publishState(UUID gameId, GameStateResponse state) {
        messagingTemplate.convertAndSend(gameTopic(gameId), state);
    }

    public void publishChat(UUID gameId, ChatMessage message) {
        messagingTemplate.convertAndSend(chatTopic(gameId), message);
    }

    public void sendSystemMessage(UUID gameId, String message) {
        publishChat(gameId, new ChatMessage("System", message, ChatMessage.MessageType.SYSTEM));
    }

    /**
     * Publish {@code state} and, if the game has just ended, announce the result, archive the match and schedule the
     * game's removal. Call this only from the action that ended the game, so a match is archived once.
     */
    public void publishUpdate(UUID gameId, GameStateResponse state) {
        publishState(gameId, state);
        if (!state.gameStatus().isGameOver()) {
            return;
        }
        sendSystemMessage(gameId, "Game Over: " + state.gameStatus().getDescription());
        try {
            Game game = matchService.getGameOrThrow(gameId);
            RatingChange ratingChange = matchService.archiveMatch(gameId, game);
            sendSystemMessage(gameId, ratingSummary(game, ratingChange));
            matchService.scheduleGameCleanup(gameId);
        } catch (Exception e) {
            logger.error("Failed to archive game {}", gameId, e);
        }
    }
}
