package me.zilid.chessplatform.exception;

import me.zilid.chessplatform.model.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketExceptionHandlerTest {
    private static final UUID GAME_ID = UUID.fromString("8a169d0a-c121-4d83-a7b3-8ee30f87cfa9");

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final WebSocketExceptionHandler handler = new WebSocketExceptionHandler(messagingTemplate);

    @Test
    void unexpectedSocketErrorDoesNotRevealInternalDetails() {
        handler.handleException(new RuntimeException("private database detail"), sessionHeaders());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(eq("player"), eq("/topic/errors"), payload.capture());
        assertThat(((ErrorResponse) payload.getValue()).error()).isEqualTo("An unexpected error occurred");
    }

    @Test
    void missingGameSocketErrorHasOneClearMessage() {
        handler.handleException(new GameNotFoundException("Game not found: " + GAME_ID), sessionHeaders());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(eq("player"), eq("/topic/errors"), payload.capture());
        assertThat(((ErrorResponse) payload.getValue()).error()).isEqualTo("Game not found: " + GAME_ID);
    }

    private static SimpMessageHeaderAccessor sessionHeaders() {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionAttributes(Map.of("username", "player"));
        return headers;
    }
}
