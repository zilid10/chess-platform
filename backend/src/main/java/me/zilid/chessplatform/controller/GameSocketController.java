package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.Player;
import me.zilid.chessplatform.model.dto.ChatMessage;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MoveRequest;
import me.zilid.chessplatform.security.UserPrincipal;
import me.zilid.chessplatform.service.GameEventPublisher;
import me.zilid.chessplatform.service.MatchService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Controller
public class GameSocketController {
    private static final Logger logger = LoggerFactory.getLogger(GameSocketController.class);

    private final MatchService matchService;
    private final GameEventPublisher publisher;

    public GameSocketController(MatchService matchService, GameEventPublisher publisher) {
        this.matchService = matchService;
        this.publisher = publisher;
    }

    private static @Nullable PieceType parsePromotion(@Nullable String promotion) {
        if (promotion == null) {
            return null;
        }
        return switch (promotion) {
            case "q" -> PieceType.QUEEN;
            case "r" -> PieceType.ROOK;
            case "b" -> PieceType.BISHOP;
            case "n" -> PieceType.KNIGHT;
            default -> throw new IllegalArgumentException("Invalid promotion: " + promotion);
        };
    }

    private static Player currentPlayer(@Nullable Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal user) {
            return user.toPlayer();
        }
        throw new IllegalStateException("Authentication required");
    }

    /**
     * Handle WebSocket disconnection events
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (sessionAttributes != null) {
            UUID gameId = (UUID) sessionAttributes.get("gameId");
            UUID userId = (UUID) sessionAttributes.get("userId");
            String username = (String) sessionAttributes.get("username");

            if (gameId != null && userId != null && username != null) {
                logger.info("User {} disconnected from game {}", username, gameId);

                // Send disconnect notification
                ChatMessage disconnectMessage = new ChatMessage(
                        "System",
                        username + " disconnected",
                        ChatMessage.MessageType.LEAVE
                );
                publisher.publishChat(gameId, disconnectMessage);
            }
        }
    }

    /**
     * Handle player joining a game session
     * Maps to: /app/game/{gameId}/join
     * Response sent to: /topic/game.{gameId}
     */
    @MessageMapping("/game/{gameId}/join")
    public void joinGame(@DestinationVariable UUID gameId,
                         Principal principal,
                         SimpMessageHeaderAccessor headerAccessor) {
        Player currentUser = currentPlayer(principal);
        var attributes = Objects.requireNonNull(headerAccessor.getSessionAttributes());
        attributes.put("gameId", gameId);
        attributes.put("userId", currentUser.id());
        attributes.put("username", currentUser.displayName());

        Game game = matchService.getGameOrThrow(gameId);
        boolean isPlayer = game.isValidPlayer(currentUser);

        headerAccessor.getSessionAttributes().put("isPlayer", isPlayer);
        if (isPlayer) {
            logger.info("Player {} joined game {}", currentUser.displayName(), gameId);
        } else {
            logger.info("Spectator {} joined game {}", currentUser.displayName(), gameId);
        }

        publisher.publishState(gameId, matchService.buildGameStateResponse(game));

        ChatMessage notification = new ChatMessage(
                "System",
                currentUser.displayName() + (isPlayer ? " (Player)" : " (Spectator)") + " connected",
                ChatMessage.MessageType.JOIN
        );
        publisher.publishChat(gameId, notification);
    }

    /**
     * Handle chess piece moves
     * Maps to: /app/game/{gameId}/move
     * Response sent to: /topic/game.{gameId}
     */
    @MessageMapping("/game/{gameId}/move")
    public void movePiece(@DestinationVariable UUID gameId,
                          @Payload MoveRequest moveRequest,
                          Principal principal) {
        Player currentUser = currentPlayer(principal);
        GameStateResponse response = matchService.makeMove(
                currentUser, gameId, moveRequest.moveFrom(), moveRequest.moveTo(),
                parsePromotion(moveRequest.promotion()));
        publisher.publishUpdate(gameId, response);
    }

    /**
     * A client's claim that the side to move has run out of time. The server decides from its own clock; a claim
     * made too early is ignored, and the client may repeat it.
     * Maps to: /app/game/{gameId}/flag
     * Response sent to: /topic/game.{gameId}
     */
    @MessageMapping("/game/{gameId}/flag")
    public void claimTimeout(@DestinationVariable UUID gameId, Principal principal) {
        Player currentUser = currentPlayer(principal);
        logger.debug("User {} claims a timeout in game {}", currentUser.displayName(), gameId);
        matchService.checkTimeout(gameId)
                .ifPresent(response -> publisher.publishUpdate(gameId, response));
    }

    /**
     * Handle player resignation
     * Maps to: /app/game/{gameId}/resign
     * Response sent to: /topic/game.{gameId}
     */
    @MessageMapping("/game/{gameId}/resign")
    public void resign(@DestinationVariable UUID gameId,
                       Principal principal) {
        Player currentUser = currentPlayer(principal);
        GameStateResponse response = matchService.resign(currentUser, gameId);
        logger.info("Resign handled in game {}: {}", gameId, response.gameStatus());
        publisher.publishUpdate(gameId, response);
    }

    /**
     * Handle draw offer/acceptance
     * Maps to: /app/game/{gameId}/draw
     * Response sent to: /topic/game.{gameId}
     */
    @MessageMapping("/game/{gameId}/draw/accept")
    public void acceptDraw(
            @DestinationVariable UUID gameId,
            Principal principal) {
        Player currentUser = currentPlayer(principal);
        GameStateResponse response = matchService.acceptDraw(currentUser, gameId);
        logger.info("Draw acceptance handled in game {}", gameId);
        publisher.publishUpdate(gameId, response);
    }

    @MessageMapping("/game/{gameId}/draw/offer")
    public void offerDraw(
            @DestinationVariable UUID gameId,
            Principal principal) {
        Player currentUser = currentPlayer(principal);
        matchService.offerDraw(currentUser, gameId);
        logger.info("Draw offered in game {}", gameId);

        // Send system message
        publisher.sendSystemMessage(gameId, "Draw offered");
    }

    /**
     * Handle chat messages in a game
     * Maps to: /app/game/{gameId}/chat
     * Response sent to: /topic/game.{gameId}.chat
     */
    @MessageMapping("/game/{gameId}/chat")
    public void sendChatMessage(@DestinationVariable UUID gameId,
                                @Payload ChatMessage chatMessage,
                                Principal principal) {
        Player currentUser = currentPlayer(principal);
        matchService.getGameOrThrow(gameId);

        logger.info("Chat message in game {} from {}", gameId, currentUser.displayName());

        ChatMessage timestampedMessage = new ChatMessage(
                currentUser.displayName(),
                chatMessage.message(),
                ChatMessage.MessageType.CHAT
        );

        publisher.publishChat(gameId, timestampedMessage);
    }
}
