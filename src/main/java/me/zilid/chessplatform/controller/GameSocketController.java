package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.engine.Game;
import me.zilid.chessplatform.engine.pieces.Piece;
import me.zilid.chessplatform.model.dto.ChatMessage;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MoveRequest;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.MatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

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

                // Mark player as disconnected
                matchService.setPlayerConnected(gameId, userId, false);

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
                         @AuthenticationPrincipal UserPrincipal currentUser,
                         SimpMessageHeaderAccessor headerAccessor) {
        // Store session attributes for tracking
        headerAccessor.getSessionAttributes().put("gameId", gameId);
        headerAccessor.getSessionAttributes().put("userId", currentUser.getId());
        headerAccessor.getSessionAttributes().put("username", currentUser.getUsername());

        // Create game if it doesn't exist
        Game game = matchService.getOrCreateGameSession(gameId);

        // Initialize connection tracking for this game if needed
        ConcurrentMap<UUID, Boolean> gameConnections = matchService.getOrCreateGameConnectionStatus(gameId);

        // Check if this is a reconnect
        boolean isReconnect = gameConnections.containsKey(currentUser.getId());

        // Assign player to white or black if new player
        if (!isReconnect) {
            if (game.getWhitePlayer() == null) {
                game.setWhitePlayer(currentUser);
                logger.info("User {} joined game {} as White", currentUser.getUsername(), gameId);
            } else if (game.getBlackPlayer() == null) {
                game.setBlackPlayer(currentUser);
                logger.info("User {} joined game {} as Black", currentUser.getUsername(), gameId);
            } else {
                logger.info("User {} joined game {} as spectator", currentUser.getUsername(), gameId);
            }
        } else {
            logger.info("User {} reconnected to game {}", currentUser.getUsername(), gameId);
        }

        // Mark player as connected
        matchService.setPlayerConnected(gameId, currentUser.getId(), true);

        // Send current game state to all subscribers
        GameStateResponse response = buildGameStateResponse(game, null, null);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, response);

        // Send appropriate notification to chat
        ChatMessage notification = new ChatMessage(
            "System",
            currentUser.getUsername() + (isReconnect ? " reconnected" : " joined the game"),
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
                          @AuthenticationPrincipal UserPrincipal currentUser) {
        Game game = matchService.getGameSession(gameId);

        if (game == null) {
            logger.error("Game {} not found", gameId);
            throw new IllegalArgumentException("Game not found: " + gameId);
        }

        if (game.isGameOver()) {
            logger.warn("Attempted move on completed game {}", gameId);
            throw new IllegalStateException("Game is already over");
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
        GameStateResponse response = buildGameStateResponse(game, moveFrom, moveTo);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, response);

        // Send system message if game ended
        if (game.isGameOver()) {
            sendSystemMessage(gameId, "Game Over: " + game.getStatus().getDescription());
        }
    }

    /**
     * Handle player resignation
     * Maps to: /app/game/{gameId}/resign
     * Response sent to: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/resign")
    public void resign(@DestinationVariable UUID gameId,
                       @AuthenticationPrincipal UserPrincipal currentUser) {
        Game game = matchService.getGameSession(gameId);

        if (game == null) {
            logger.error("Game {} not found", gameId);
            throw new IllegalArgumentException("Game not found: " + gameId);
        }

        if (game.isGameOver()) {
            logger.warn("Attempted resignation on completed game {}", gameId);
            throw new IllegalStateException("Game is already over");
        }

        // Parse player color and resign
        Piece.Color color = game.getPlayerColor(currentUser);
        game.resign(color);

        logger.info("Player {} resigned in game {}", color, gameId);

        // Send updated game state
        GameStateResponse response = buildGameStateResponse(game, null, null);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, response);

        // Send system message
        String winner = color.isWhite() ? "Black" : "White";
        sendSystemMessage(gameId, winner + " wins by resignation");
    }

    /**
     * Handle draw offer/acceptance
     * Maps to: /app/game/{gameId}/draw
     * Response sent to: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/draw")
    public void offerDraw(@DestinationVariable UUID gameId) {
        Game game = matchService.getGameSession(gameId);

        if (game == null) {
            logger.error("Game {} not found", gameId);
            throw new IllegalArgumentException("Game not found: " + gameId);
        }

        if (game.isGameOver()) {
            logger.warn("Attempted draw offer on completed game {}", gameId);
            throw new IllegalStateException("Game is already over");
        }

        game.agreeDraw();
        logger.info("Draw agreed in game {}", gameId);

        // Send updated game state
        GameStateResponse response = buildGameStateResponse(game, null, null);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, response);

        // Send system message
        sendSystemMessage(gameId, "Draw agreed");
    }

    /**
     * Build a GameStateResponse from the current game state
     */
    private GameStateResponse buildGameStateResponse(Game game, String lastMoveFrom, String lastMoveTo) {
        return new GameStateResponse(
            game.getStatus(),
            game.getFen(),
            lastMoveFrom,
            lastMoveTo,
            game.getTurnColor().name()
        );
    }

    /**
     * Handle chat messages in a game
     * Maps to: /app/game/{gameId}/chat
     * Response sent to: /topic/game/{gameId}/chat
     */
    @MessageMapping("/game/{gameId}/chat")
    public void sendChatMessage(@DestinationVariable UUID gameId,
                                @Payload ChatMessage chatMessage) {
        Game game = matchService.getGameSession(gameId);

        if (game == null) {
            logger.error("Game {} not found for chat message", gameId);
            throw new IllegalArgumentException("Game not found: " + gameId);
        }

        logger.info("Chat message in game {} from {}: {}", gameId, chatMessage.sender(), chatMessage.message());

        // Create timestamped message and broadcast to all players
        ChatMessage timestampedMessage = new ChatMessage(
            chatMessage.sender(),
            chatMessage.message(),
            ChatMessage.MessageType.CHAT
        );

        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/chat", timestampedMessage);
    }

    /**
     * Send system message to game chat
     */
    private void sendSystemMessage(UUID gameId, String message) {
        ChatMessage systemMessage = new ChatMessage(
            "System",
            message,
            ChatMessage.MessageType.SYSTEM
        );
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/chat", systemMessage);
    }
}
