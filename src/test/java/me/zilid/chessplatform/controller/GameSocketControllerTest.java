package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MoveRequest;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.MatchService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @BeforeEach
    void setUp() {
        when(matchService.getGameOrThrow(GAME_ID)).thenReturn(game);
        when(game.isUserTurn(PLAYER)).thenReturn(true);
    }

    @ParameterizedTest
    @CsvSource({"q, QUEEN", "r, ROOK", "b, BISHOP", "n, KNIGHT"})
    void forwardsPromotionPieceAndBroadcastsUpdatedGame(String symbol, PieceType pieceType) {
        GameStateResponse response = response("a7", "a8");
        when(game.makeMove("a7", "a8", pieceType)).thenReturn(true);
        when(matchService.buildGameStateResponse(game)).thenReturn(response);

        controller.movePiece(GAME_ID, request("a7", "a8", symbol), authentication());

        verify(matchService).requirePlayer(game, PLAYER);
        verify(game).makeMove("a7", "a8", pieceType);
        verify(messagingTemplate).convertAndSend("/topic/game/" + GAME_ID, response);
    }

    @Test
    void ordinaryMoveHasNoPromotionPiece() {
        GameStateResponse response = response("e2", "e4");
        when(game.makeMove("e2", "e4", null)).thenReturn(true);
        when(matchService.buildGameStateResponse(game)).thenReturn(response);

        controller.movePiece(GAME_ID, request("e2", "e4", null), authentication());

        verify(game).makeMove("e2", "e4", null);
        verify(messagingTemplate).convertAndSend("/topic/game/" + GAME_ID, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Q", "k", "queen"})
    void invalidPromotionIsRejectedBeforeMovingOrBroadcasting(String symbol) {
        assertThatThrownBy(() -> controller.movePiece(GAME_ID, request("a7", "a8", symbol), authentication()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid promotion");

        verify(game, never()).makeMove(anyString(), anyString(), nullable(PieceType.class));
        verifyNoInteractions(messagingTemplate);
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
