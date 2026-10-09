package me.zilid.chessplatform.controller.advice;

import java.util.Objects;
import me.zilid.chessplatform.controller.GameSocketController;
import me.zilid.chessplatform.exception.GameIsOverException;
import me.zilid.chessplatform.exception.GameNotFoundException;
import me.zilid.chessplatform.model.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * Sends STOMP handler failures to the user's private {@code /user/topic/errors} queue.
 *
 * <p>Keep every {@code @MessageExceptionHandler} here: Spring prefers handlers declared on the controller itself, so a
 * catch-all left there would shadow the specific handlers below.
 */
@ControllerAdvice(assignableTypes = GameSocketController.class)
public class WebSocketExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketExceptionHandler.class);

    @MessageExceptionHandler
    @SendToUser("/topic/errors")
    public ErrorResponse handleException(Exception e) {
        logger.error("Unexpected WebSocket error", e);
        return new ErrorResponse("An unexpected error occurred");
    }

    @MessageExceptionHandler(GameNotFoundException.class)
    @SendToUser("/topic/errors")
    public ErrorResponse handleException(GameNotFoundException e) {
        logger.warn("WebSocket request failed: {}", e.getMessage());
        return new ErrorResponse(Objects.requireNonNullElse(e.getMessage(), "Game not found"));
    }

    @MessageExceptionHandler(GameIsOverException.class)
    @SendToUser("/topic/errors")
    public ErrorResponse handleException(GameIsOverException e) {
        logger.warn("Game is over: {}", e.getMessage());
        return new ErrorResponse(Objects.requireNonNullElse(e.getMessage(), "Game is already over"));
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser("/topic/errors")
    public ErrorResponse handleInvalidInput(IllegalArgumentException e) {
        logger.warn("Invalid WebSocket input: {}", e.getMessage());
        return new ErrorResponse("Invalid input: " + e.getMessage());
    }

    @MessageExceptionHandler(IllegalStateException.class)
    @SendToUser("/topic/errors")
    public ErrorResponse handleInvalidState(IllegalStateException e) {
        logger.warn("Invalid WebSocket state: {}", e.getMessage());
        return new ErrorResponse("Cannot perform action: " + e.getMessage());
    }
}
