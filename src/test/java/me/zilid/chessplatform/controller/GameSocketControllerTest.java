package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.exception.GameNotFoundException;
import me.zilid.chessplatform.model.dto.ChatMessage;
import me.zilid.chessplatform.model.dto.ErrorResponse;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MoveRequest;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.MatchService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GameSocketControllerTest {
    private static final UUID GAME_ID = UUID.fromString("8a169d0a-c121-4d83-a7b3-8ee30f87cfa9");
    private static final UserPrincipal PLAYER = new UserPrincipal(
            UUID.fromString("02410898-174c-4cb5-b8c5-55fe3cc535b9"),
            "player", "player@example.com", "password", true, List.of());

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final MatchService matchService = mock(MatchService.class);
    private final Game game = mock(Game.class);
    private final GameSocketController controller = new GameSocketController(messagingTemplate, matchService);

    @ParameterizedTest
    @CsvSource({"q, QUEEN", "r, ROOK", "b, BISHOP", "n, KNIGHT"})
    void forwardsPromotionPieceAndBroadcastsUpdatedGame(String symbol, PieceType pieceType) {
        GameStateResponse response = response("a7", "a8");
        when(matchService.makeMove(PLAYER, GAME_ID, "a7", "a8", pieceType)).thenReturn(response);

        controller.movePiece(GAME_ID, request("a7", "a8", symbol), authentication());

        verify(matchService).makeMove(PLAYER, GAME_ID, "a7", "a8", pieceType);
        verify(messagingTemplate).convertAndSend("/topic/game/" + GAME_ID, response);
    }

    @Test
    void ordinaryMoveHasNoPromotionPiece() {
        GameStateResponse response = response("e2", "e4");
        when(matchService.makeMove(PLAYER, GAME_ID, "e2", "e4", null)).thenReturn(response);

        controller.movePiece(GAME_ID, request("e2", "e4", null), authentication());

        verify(matchService).makeMove(PLAYER, GAME_ID, "e2", "e4", null);
        verify(messagingTemplate).convertAndSend("/topic/game/" + GAME_ID, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Q", "k", "queen"})
    void invalidPromotionIsRejectedBeforeMovingOrBroadcasting(String symbol) {
        assertThatThrownBy(() -> controller.movePiece(GAME_ID, request("a7", "a8", symbol), authentication()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid promotion");

        verifyNoInteractions(matchService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void chatUsesAuthenticatedUsernameInsteadOfPayloadSender() {
        when(matchService.getGameOrThrow(GAME_ID)).thenReturn(game);

        controller.sendChatMessage(GAME_ID, new ChatMessage("forged", "hello"), authentication());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + GAME_ID + "/chat"), payload.capture());
        ChatMessage sent = (ChatMessage) payload.getValue();
        assertThat(sent.sender()).isEqualTo(PLAYER.getUsername());
        assertThat(sent.message()).isEqualTo("hello");
        assertThat(sent.type()).isEqualTo(ChatMessage.MessageType.CHAT);
    }

    @Test
    void unauthenticatedChatIsRejected() {
        assertThatThrownBy(() -> controller.sendChatMessage(
                GAME_ID, new ChatMessage("forged", "hello"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authentication required");

        verifyNoInteractions(matchService, messagingTemplate);
    }

    @Test
    void unexpectedSocketErrorDoesNotRevealInternalDetails() {
        controller.handleException(new RuntimeException("private database detail"), sessionHeaders());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(eq("player"), eq("/queue/errors"), payload.capture());
        assertThat(((ErrorResponse) payload.getValue()).error()).isEqualTo("An unexpected error occurred");
    }

    @Test
    void missingGameSocketErrorHasOneClearMessage() {
        controller.handleException(new GameNotFoundException("Game not found: " + GAME_ID), sessionHeaders());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(eq("player"), eq("/queue/errors"), payload.capture());
        assertThat(((ErrorResponse) payload.getValue()).error()).isEqualTo("Game not found: " + GAME_ID);
    }

    private static SimpMessageHeaderAccessor sessionHeaders() {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionAttributes(Map.of("username", "player"));
        return headers;
    }

    private static MoveRequest request(String from, String to, @Nullable String promotion) {
        return new MoveRequest(GAME_ID.toString(), from, to, promotion);
    }

    private static UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(PLAYER, null, PLAYER.getAuthorities());
    }

    private static GameStateResponse response(String from, String to) {
        return new GameStateResponse(GameStatus.ONGOING, "updated-fen", from, to, "BLACK");
    }
}
