package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.model.dto.ChatMessage;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MoveRequest;
import me.zilid.chessplatform.security.UserPrincipal;
import me.zilid.chessplatform.service.GameEventPublisher;
import me.zilid.chessplatform.service.MatchService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameSocketControllerTest {
    private static final UUID GAME_ID = UUID.fromString("8a169d0a-c121-4d83-a7b3-8ee30f87cfa9");
    private static final UserPrincipal PLAYER = new UserPrincipal(
            UUID.fromString("02410898-174c-4cb5-b8c5-55fe3cc535b9"),
            "player", "player@example.com", "password", true, List.of());

    private final MatchService matchService = mock(MatchService.class);
    private final GameEventPublisher publisher = mock(GameEventPublisher.class);
    private final Game game = mock(Game.class);
    private final GameSocketController controller = new GameSocketController(matchService, publisher);

    private static MoveRequest request(String from, String to, @Nullable String promotion) {
        return new MoveRequest(GAME_ID.toString(), from, to, promotion);
    }

    private static UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(PLAYER, null, PLAYER.getAuthorities());
    }

    private static GameStateResponse response(String from, String to) {
        return new GameStateResponse(GameStatus.ONGOING, "updated-fen", from, to, "BLACK", 60_000, 60_000, true);
    }

    @ParameterizedTest
    @CsvSource({"q, QUEEN", "r, ROOK", "b, BISHOP", "n, KNIGHT"})
    void forwardsPromotionPieceAndBroadcastsUpdatedGame(String symbol, PieceType pieceType) {
        GameStateResponse response = response("a7", "a8");
        when(matchService.makeMove(eq(PLAYER.toPlayer()), eq(GAME_ID), eq("a7"), eq("a8"), eq(pieceType),
                any(Instant.class))).thenReturn(response);

        controller.movePiece(GAME_ID, request("a7", "a8", symbol), authentication());

        verify(publisher).publishUpdate(GAME_ID, response);
    }

    @Test
    void ordinaryMoveHasNoPromotionPiece() {
        GameStateResponse response = response("e2", "e4");
        when(matchService.makeMove(eq(PLAYER.toPlayer()), eq(GAME_ID), eq("e2"), eq("e4"), isNull(),
                any(Instant.class))).thenReturn(response);

        controller.movePiece(GAME_ID, request("e2", "e4", null), authentication());

        verify(publisher).publishUpdate(GAME_ID, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Q", "k", "queen"})
    void invalidPromotionIsRejectedBeforeMovingOrBroadcasting(String symbol) {
        assertThatThrownBy(() -> controller.movePiece(GAME_ID, request("a7", "a8", symbol), authentication()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid promotion");

        verifyNoInteractions(matchService);
        verifyNoInteractions(publisher);
    }

    @Test
    void chatUsesAuthenticatedUsernameInsteadOfPayloadSender() {
        when(matchService.getGameOrThrow(GAME_ID)).thenReturn(game);

        controller.sendChatMessage(GAME_ID, new ChatMessage("forged", "hello"), authentication());

        ArgumentCaptor<ChatMessage> payload = ArgumentCaptor.forClass(ChatMessage.class);
        verify(publisher).publishChat(eq(GAME_ID), payload.capture());
        ChatMessage sent = payload.getValue();
        assertThat(sent.sender()).isEqualTo(PLAYER.getUsername());
        assertThat(sent.message()).isEqualTo("hello");
        assertThat(sent.type()).isEqualTo(ChatMessage.MessageType.CHAT);
    }

    @Test
    void resignationIsPublishedAsAnUpdate() {
        GameStateResponse resigned = new GameStateResponse(
                GameStatus.RESIGNED_BLACK_WINS, "final-fen", "e2", "e4", "BLACK", 60_000, 60_000, false);
        when(matchService.resign(PLAYER.toPlayer(), GAME_ID)).thenReturn(resigned);

        controller.resign(GAME_ID, authentication());

        verify(publisher).publishUpdate(GAME_ID, resigned);
    }

    @Test
    void acceptedTimeoutClaimIsPublishedAsAnUpdate() {
        GameStateResponse flagged = new GameStateResponse(
                GameStatus.FLAGGED_BLACK_WINS, "final-fen", "e7", "e5", "WHITE", 0, 60_000, false);
        when(matchService.checkTimeout(eq(GAME_ID), any(Instant.class))).thenReturn(Optional.of(flagged));

        controller.claimTimeout(GAME_ID, authentication());

        verify(publisher).publishUpdate(GAME_ID, flagged);
    }

    @Test
    void earlyTimeoutClaimIsIgnored() {
        when(matchService.checkTimeout(eq(GAME_ID), any(Instant.class))).thenReturn(Optional.empty());

        controller.claimTimeout(GAME_ID, authentication());

        verify(matchService).checkTimeout(eq(GAME_ID), any(Instant.class));
        verifyNoInteractions(publisher);
    }

    @Test
    void timeoutClaimNeedsAnAuthenticatedUser() {
        assertThatThrownBy(() -> controller.claimTimeout(GAME_ID, () -> "anonymous"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authentication required");

        verifyNoInteractions(matchService, publisher);
    }

    @Test
    void chatWithoutAnAuthenticatedUserIsRejected() {
        Principal anonymous = () -> "anonymous";

        assertThatThrownBy(() -> controller.sendChatMessage(
                GAME_ID, new ChatMessage("forged", "hello"), anonymous))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authentication required");

        verifyNoInteractions(matchService, publisher);
    }
}
