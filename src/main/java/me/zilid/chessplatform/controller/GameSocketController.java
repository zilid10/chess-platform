package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.engine.Game;
import me.zilid.chessplatform.exception.GameIsOverException;
import me.zilid.chessplatform.exception.GameNotFoundException;
import me.zilid.chessplatform.model.dto.ChatMessage;
import me.zilid.chessplatform.model.dto.ErrorResponse;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MoveRequest;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.MatchService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
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
                         Authentication authentication,
                         SimpMessageHeaderAccessor headerAccessor) {
        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
        headerAccessor.getSessionAttributes().put("gameId", gameId);
        headerAccessor.getSessionAttributes().put("userId", currentUser.getId());
        headerAccessor.getSessionAttributes().put("username", currentUser.getUsername());

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
                          Authentication authentication) {
        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
        Game game = matchService.getGameOrThrow(gameId);
        matchService.requirePlayer(game, currentUser);

        if (game.isGameOver()) {
            logger.warn("Attempted move on completed game {}", gameId);
            throw new IllegalStateException("Game is already over");
        }

        if (!game.isUserTurn(currentUser)) {
            logger.warn("Attempted move on opponent's turn {}", gameId);
            throw new IllegalStateException("It is not your turn");
        }


        // Validate and execute move
        String moveFrom = moveRequest.moveFrom();
        String moveTo = moveRequest.moveTo();

        boolean moveSuccess = game.makeMove(moveFrom, moveTo);

        if (!moveSuccess) {
            logger.warn("Invalid move attempted in game {}: {} to {}", gameId, moveFrom, moveTo);
            throw new IllegalArgumentException("Invalid move: " + moveFrom + " to " + moveTo);
        }

        logger.info("Move executed in game {}: {} to {}", gameId, moveFrom, moveTo);

        // Build and send response with updated game state
        GameStateResponse response = matchService.buildGameStateResponse(game);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, response);

        // Send system message if game ended
        if (game.isGameOver()) {
            sendSystemMessage(gameId, "Game Over: " + game.getStatus().getDescription());
            try {
                matchService.archiveMatch(gameId, game);

                matchService.scheduleGameCleanup(gameId);
            } catch (Exception e) {
                logger.error("Failed to archive game {}", gameId, e);
            }
        }
    }

    /**
     * Handle player resignation
     * Maps to: /app/game/{gameId}/resign
     * Response sent to: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/resign")
    public void resign(@DestinationVariable UUID gameId,
                       Authentication authentication) {
        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
        GameStateResponse response = matchService.resign(currentUser, gameId);

        // Send updated game state
        messagingTemplate.convertAndSend("/topic/game/" + gameId, response);

        // Send system message
        String winner = response.gameStatus().isWhiteWin() ? "Black" : "White";
        sendSystemMessage(gameId, winner + " wins by resignation");
    }

    /**
     * Handle draw offer/acceptance
     * Maps to: /app/game/{gameId}/draw
     * Response sent to: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/draw/accept")
    public void acceptDraw(
            @DestinationVariable UUID gameId,
            Authentication authentication) {
        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();

        GameStateResponse response = matchService.acceptDraw(currentUser, gameId);

        // update the game state
        messagingTemplate.convertAndSend("/topic/game/" + gameId, response);
        logger.info("Draw offered in game {}", gameId);

        // Send system message
        sendSystemMessage(gameId, "Draw agreed");
    }

    @MessageMapping("/game/{gameId}/draw/offer")
    public void offerDraw(
            @DestinationVariable UUID gameId,
            Authentication authentication) {
        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
        matchService.offerDraw(currentUser, gameId);
        logger.info("Draw agreed in game {}", gameId);

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
                                @Payload ChatMessage chatMessage) {
        Game game = matchService.getGameOrThrow(gameId);

        logger.info("Chat message in game {} from {}: {}", gameId, chatMessage.sender(), chatMessage.message());

        // Create timestamped message and broadcast to all players
        ChatMessage timestampedMessage = new ChatMessage(
            chatMessage.sender(),
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

    @MessageExceptionHandler
    public void handleException(Exception e, SimpMessageHeaderAccessor headerAccessor) {
        logger.error("WebSocket error: ", e);

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) return;

        String username = (String) sessionAttributes.get("username");

        if (username != null) {
            ErrorResponse error = new ErrorResponse("An unexpected error occurred: " + e.getMessage());
            messagingTemplate.convertAndSendToUser(username, "/queue/errors", error);
        }
    }

    @MessageExceptionHandler(GameNotFoundException.class)
    public void handleException(GameNotFoundException e, SimpMessageHeaderAccessor headerAccessor) {
        logger.error("WebSocket error: ", e);

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) return;

        String username = (String) sessionAttributes.get("username");

        if (username != null) {
            ErrorResponse error = new ErrorResponse("Game not found: " + e.getMessage());
            messagingTemplate.convertAndSendToUser(username, "/queue/errors", error);
        }
    }

    @MessageExceptionHandler(GameIsOverException.class)
    public void handleException(GameIsOverException e, SimpMessageHeaderAccessor headerAccessor) {
        logger.error("WebSocket error: ", e);

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) return;

        String username = (String) sessionAttributes.get("username");

        if (username != null) {
            ErrorResponse error = new ErrorResponse("Game is already over: " + e.getMessage());
            messagingTemplate.convertAndSendToUser(username, "/queue/errors", error);
        }
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    public void handleInvalidInput(IllegalArgumentException e, SimpMessageHeaderAccessor headerAccessor) {
        logger.error("WebSocket error: ", e);

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) return;

        String username = (String) sessionAttributes.get("username");

        if (username != null) {
            ErrorResponse error = new ErrorResponse("Invalid input: " + e.getMessage());
            messagingTemplate.convertAndSendToUser(username, "/queue/errors", error);
        }
    }

    @MessageExceptionHandler(IllegalStateException.class)
    public void handleInvalidState(IllegalStateException e, SimpMessageHeaderAccessor headerAccessor) {
        logger.error("WebSocket error: ", e);

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) return;

        String username = (String) sessionAttributes.get("username");

        if (username != null) {
            ErrorResponse error = new ErrorResponse("Cannot perform action: " + e.getMessage());
            messagingTemplate.convertAndSendToUser(username, "/queue/errors", error);
        }
    }

}
