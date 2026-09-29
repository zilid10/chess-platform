package me.zilid.chessplatform.controller.advice;

import me.zilid.chessplatform.controller.GameSocketController;
import me.zilid.chessplatform.exception.GameIsOverException;
import me.zilid.chessplatform.exception.GameNotFoundException;
import me.zilid.chessplatform.model.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.Map;
import java.util.Objects;

/**
 * Sends STOMP handler failures to the user's private {@code /user/topic/errors} queue.
 * <p>
 * Keep every {@code @MessageExceptionHandler} here: Spring prefers handlers declared on the controller itself, so a
 * catch-all left there would shadow the specific handlers below.
 */
@ControllerAdvice(assignableTypes = GameSocketController.class)
public class WebSocketExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketExceptionHandler.class);

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketExceptionHandler(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageExceptionHandler
    public void handleException(Exception e, SimpMessageHeaderAccessor headerAccessor) {
        logger.error("Unexpected WebSocket error", e);
        sendError(headerAccessor, "An unexpected error occurred");
    }

    @MessageExceptionHandler(GameNotFoundException.class)
    public void handleException(GameNotFoundException e, SimpMessageHeaderAccessor headerAccessor) {
        logger.warn("WebSocket request failed: {}", e.getMessage());
        sendError(headerAccessor, Objects.requireNonNullElse(e.getMessage(), "Game not found"));
    }

    @MessageExceptionHandler(GameIsOverException.class)
    public void handleException(GameIsOverException e, SimpMessageHeaderAccessor headerAccessor) {
        logger.warn("Game is over: {}", e.getMessage());
        sendError(headerAccessor, Objects.requireNonNullElse(e.getMessage(), "Game is already over"));
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
            messagingTemplate.convertAndSendToUser(username, "/topic/errors", new ErrorResponse(message));
        }
    }
}
