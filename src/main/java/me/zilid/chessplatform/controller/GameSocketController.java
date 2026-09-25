package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.exception.GameIsOverException;
import me.zilid.chessplatform.exception.GameNotFoundException;
import me.zilid.chessplatform.model.dto.ChatMessage;
import me.zilid.chessplatform.model.dto.ErrorResponse;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MoveRequest;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.MatchService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    private final SimpMessagingTemplate messagingTemplate;
    private final MatchService matchService;

    public GameSocketController(SimpMessagingTemplate messagingTemplate, MatchService matchService) {
        this.messagingTemplate = messagingTemplate;
        this.matchService = matchService;
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
                messagingTemplate.convertAndSend("/topic/game/" + gameId + "/chat", disconnectMessage);
            }
        }
    }

    /**
     * Handle player joining a game session
     * Maps to: /app/game/{gameId}/join
     * Response sent to: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/join")
    public void joinGame(@DestinationVariable UUID gameId,
                         Principal principal,
                         SimpMessageHeaderAccessor headerAccessor) {
        UserPrincipal currentUser = currentUser(principal);
        var attributes = Objects.requireNonNull(headerAccessor.getSessionAttributes());
        attributes.put("gameId", gameId);
        attributes.put("userId", currentUser.getId());
        attributes.put("username", currentUser.getUsername());

        Game game = matchService.getGameOrThrow(gameId);
        boolean isPlayer = game.isValidPlayer(currentUser);

        headerAccessor.getSessionAttributes().put("isPlayer", isPlayer);
        if (isPlayer) {
            logger.info("Player {} joined game {}", currentUser.getUsername(), gameId);
        } else {
            logger.info("Spectator {} joined game {}", currentUser.getUsername(), gameId);
        }

        GameStateResponse response = matchService.buildGameStateResponse(game);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, response);

        ChatMessage notification = new ChatMessage(
                "System",
                currentUser.getUsername() + (isPlayer ? " (Player)" : " (Spectator)") + " connected",
                ChatMessage.MessageType.JOIN
        );
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/chat", notification);
    }

    /**
     * Handle chess piece moves
     * Maps to: /app/game/{gameId}/move
     * Response sent to: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/move")
    public void movePiece(@DestinationVariable UUID gameId,
                          @Payload MoveRequest moveRequest,
                          Principal principal) {
        UserPrincipal currentUser = currentUser(principal);
        GameStateResponse response = matchService.makeMove(
                currentUser, gameId, moveRequest.moveFrom(), moveRequest.moveTo(),
                parsePromotion(moveRequest.promotion()));
        messagingTemplate.convertAndSend("/topic/game/" + gameId, response);
        onGameEnd(gameId, response);
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

    /**
     * Handle player resignation
     * Maps to: /app/game/{gameId}/resign
     * Response sent to: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/resign")
    public void resign(@DestinationVariable UUID gameId,
                       Principal principal) {
        UserPrincipal currentUser = currentUser(principal);
        GameStateResponse response = matchService.resign(currentUser, gameId);

        // Send updated game state
        messagingTemplate.convertAndSend("/topic/game/" + gameId, response);

        // Send system message
        String winner = response.gameStatus().isWhiteWin() ? "White" : "Black";
        logger.info("Resign executed in game {}: {} wins", gameId, winner);
        onGameEnd(gameId, response);
    }

    /**
     * Handle draw offer/acceptance
     * Maps to: /app/game/{gameId}/draw
     * Response sent to: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/draw/accept")
    public void acceptDraw(
            @DestinationVariable UUID gameId,
            Principal principal) {
        UserPrincipal currentUser = currentUser(principal);
        GameStateResponse response = matchService.acceptDraw(currentUser, gameId);

        // update the game state
        messagingTemplate.convertAndSend("/topic/game/" + gameId, response);
        logger.info("Draw acceptance handled in game {}", gameId);

        // Send system message
        onGameEnd(gameId, response);
    }

    @MessageMapping("/game/{gameId}/draw/offer")
    public void offerDraw(
            @DestinationVariable UUID gameId,
            Principal principal) {
        UserPrincipal currentUser = currentUser(principal);
        matchService.offerDraw(currentUser, gameId);
        logger.info("Draw offered in game {}", gameId);

        // Send system message
        sendSystemMessage(gameId, "Draw offered");
    }

    /**
     * Handle chat messages in a game
     * Maps to: /app/game/{gameId}/chat
     * Response sent to: /topic/game/{gameId}/chat
     */
    @MessageMapping("/game/{gameId}/chat")
    public void sendChatMessage(@DestinationVariable UUID gameId,
                                @Payload ChatMessage chatMessage,
                                Principal principal) {
        UserPrincipal currentUser = currentUser(principal);
        matchService.getGameOrThrow(gameId);

        logger.info("Chat message in game {} from {}", gameId, currentUser.getUsername());

        ChatMessage timestampedMessage = new ChatMessage(
                currentUser.getUsername(),
                chatMessage.message(),
                ChatMessage.MessageType.CHAT
        );

        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/chat", timestampedMessage);
    }

    private void sendSystemMessage(UUID gameId, String message) {
        ChatMessage systemMessage = new ChatMessage(
                "System",
                message,
                ChatMessage.MessageType.SYSTEM
        );
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/chat", systemMessage);
    }

    private static UserPrincipal currentUser(@Nullable Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal user) {
            return user;
        }
        throw new IllegalStateException("Authentication required");
    }

    private void onGameEnd(UUID gameId, GameStateResponse response) {
        if (response.gameStatus().isGameOver()) {
            sendSystemMessage(gameId, "Game Over: " + response.gameStatus().getDescription());
            try {
                matchService.archiveMatch(gameId, matchService.getGameOrThrow(gameId));
                matchService.scheduleGameCleanup(gameId);
            } catch (Exception e) {
                logger.error("Failed to archive game {}", gameId, e);
            }
        }
    }

    @MessageExceptionHandler
    public void handleException(Exception e, SimpMessageHeaderAccessor headerAccessor) {
        logger.error("Unexpected WebSocket error", e);
        sendError(headerAccessor, "An unexpected error occurred");
    }

    @MessageExceptionHandler(GameNotFoundException.class)
    public void handleException(GameNotFoundException e, SimpMessageHeaderAccessor headerAccessor) {
        logger.warn("WebSocket request failed: {}", e.getMessage());
        sendError(headerAccessor, e.getMessage());
    }

    @MessageExceptionHandler(GameIsOverException.class)
    public void handleException(GameIsOverException e, SimpMessageHeaderAccessor headerAccessor) {
        logger.warn("Game is over: {}", e.getMessage());
        sendError(headerAccessor, e.getMessage());
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    public void handleInvalidInput(IllegalArgumentException e, SimpMessageHeaderAccessor headerAccessor) {
        logger.warn("Invalid WebSocket input: {}", e.getMessage());
        sendError(headerAccessor, "Invalid input: " + e.getMessage());
    }

    @MessageExceptionHandler(IllegalStateException.class)
    public void handleInvalidState(IllegalStateException e, SimpMessageHeaderAccessor headerAccessor) {
        logger.warn("Invalid WebSocket state: {}", e.getMessage());
        sendError(headerAccessor, "Cannot perform action: " + e.getMessage());
    }

    private void sendError(SimpMessageHeaderAccessor headerAccessor, String message) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return;
        }
        String username = (String) sessionAttributes.get("username");
        if (username != null) {
            messagingTemplate.convertAndSendToUser(username, "/queue/errors", new ErrorResponse(message));
        }
    }
}
