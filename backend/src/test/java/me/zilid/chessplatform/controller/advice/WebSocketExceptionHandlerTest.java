package me.zilid.chessplatform.controller.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import me.zilid.chessplatform.exception.GameNotFoundException;
import org.junit.jupiter.api.Test;

class WebSocketExceptionHandlerTest {
    private static final UUID GAME_ID = UUID.fromString("8a169d0a-c121-4d83-a7b3-8ee30f87cfa9");

    private final WebSocketExceptionHandler handler = new WebSocketExceptionHandler();

    @Test
    void unexpectedSocketErrorDoesNotRevealInternalDetails() {
        assertThat(handler.handleException(new RuntimeException("private database detail"))
                        .error())
                .isEqualTo("An unexpected error occurred");
    }

    @Test
    void missingGameSocketErrorHasOneClearMessage() {
        assertThat(handler.handleException(new GameNotFoundException("Game not found: " + GAME_ID))
                        .error())
                .isEqualTo("Game not found: " + GAME_ID);
    }
}
