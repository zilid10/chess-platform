package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.game.ClockSetting;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.TimeControl;
import me.zilid.chessplatform.controller.advice.GlobalExceptionHandler;
import me.zilid.chessplatform.exception.GameIsOverException;
import me.zilid.chessplatform.exception.GameNotFoundException;
import me.zilid.chessplatform.model.dto.GameCreatedResponse;
import me.zilid.chessplatform.model.dto.GameJoinResponse;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.security.SecurityConfig;
import me.zilid.chessplatform.security.UserPrincipal;
import me.zilid.chessplatform.service.MatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// The production application enables JPA repositories, so keep this MVC slice isolated.
@WebMvcTest(GameController.class)
@ContextConfiguration(classes = GameControllerWebMvcTest.TestConfiguration.class)
class GameControllerWebMvcTest {

    private static final UUID GAME_ID = UUID.fromString("8a169d0a-c121-4d83-a7b3-8ee30f87cfa9");
    private static final UserPrincipal PLAYER = new UserPrincipal(
            UUID.fromString("02410898-174c-4cb5-b8c5-55fe3cc535b9"),
            "player", "player@example.com", "password", true, List.of());
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private MatchService matchService;

    @Test
    void gameStateRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/games/{gameId}/state", GAME_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication required"))
                .andExpect(jsonPath("$.instance").value("/api/games/" + GAME_ID + "/state"));

        verifyNoInteractions(matchService);
    }

    @Test
    void creatingAndJoiningGamesRequireAuthentication() throws Exception {
        mvc.perform(post("/api/games").param("color", "WHITE"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/games/{gameId}/join", GAME_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(matchService);
    }

    @Test
    void stateEndpointSerializesTheServiceResponse() throws Exception {
        when(matchService.getGameState(GAME_ID)).thenReturn(new GameStateResponse(
                GameStatus.ONGOING, "starting-fen", "e2", "e4", "BLACK", 299_500, 300_000, true));

        mvc.perform(get("/api/games/{gameId}/state", GAME_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameStatus").value("ONGOING"))
                .andExpect(jsonPath("$.fen").value("starting-fen"))
                .andExpect(jsonPath("$.lastMoveFrom").value("e2"))
                .andExpect(jsonPath("$.lastMoveTo").value("e4"))
                .andExpect(jsonPath("$.turnColor").value("BLACK"))
                .andExpect(jsonPath("$.whiteRemainingMillis").value(299_500))
                .andExpect(jsonPath("$.blackRemainingMillis").value(300_000))
                .andExpect(jsonPath("$.clockRunning").value(true));

        verify(matchService).getGameState(GAME_ID);
    }

    @Test
    void createGamePassesAuthenticatedPlayerColorAndClockSetting() throws Exception {
        ClockSetting blitz = ClockSetting.ofMinutes(3, 2);
        when(matchService.createGame(PLAYER.toPlayer(), Color.BLACK, blitz)).thenReturn(new GameCreatedResponse(
                GAME_ID, Color.BLACK, "3+2", TimeControl.BLITZ, "starting-fen", "/game/" + GAME_ID));

        mvc.perform(post("/api/games")
                        .param("color", "BLACK")
                        .param("timeControl", "3+2")
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.color").value("BLACK"))
                .andExpect(jsonPath("$.clockSetting").value("3+2"))
                .andExpect(jsonPath("$.timeControl").value("BLITZ"))
                .andExpect(jsonPath("$.socketUrl").value("/game/" + GAME_ID));

        verify(matchService).createGame(PLAYER.toPlayer(), Color.BLACK, blitz);
    }

    @Test
    void createGameDefaultsToFivePlusThree() throws Exception {
        ClockSetting fivePlusThree = ClockSetting.ofMinutes(5, 3);
        when(matchService.createGame(PLAYER.toPlayer(), Color.WHITE, fivePlusThree)).thenReturn(new GameCreatedResponse(
                GAME_ID, Color.WHITE, "5+3", TimeControl.BLITZ, "starting-fen", "/game/" + GAME_ID));

        mvc.perform(post("/api/games")
                        .param("color", "WHITE")
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clockSetting").value("5+3"));

        verify(matchService).createGame(PLAYER.toPlayer(), Color.WHITE, fivePlusThree);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BLITZ", "5", "5+", "+3", "5+3+1", "0+0", "5.1+0", "181+0", "5+-3"})
    void createGameRejectsMalformedClockSettings(String clockSetting) throws Exception {
        mvc.perform(post("/api/games")
                        .param("color", "WHITE")
                        .param("timeControl", clockSetting)
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(matchService);
    }

    @Test
    void joinGameReturnsTheAssignedRole() throws Exception {
        when(matchService.joinGame(GAME_ID, PLAYER.toPlayer())).thenReturn(new GameJoinResponse(
                GAME_ID, "WHITE", "10+0", TimeControl.RAPID, "starting-fen", GameStatus.ONGOING, "WHITE"));

        mvc.perform(post("/api/games/{gameId}/join", GAME_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("WHITE"))
                .andExpect(jsonPath("$.currentTurn").value("WHITE"));

        verify(matchService).joinGame(GAME_ID, PLAYER.toPlayer());
    }

    @Test
    void pgnEndpointReturnsPlainText() throws Exception {
        when(matchService.getMatchPGN(GAME_ID)).thenReturn("1. e4 e5 2. Nf3");

        mvc.perform(get("/api/games/{gameId}/pgn", GAME_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("1. e4 e5 2. Nf3"));
    }

    @Test
    void invalidGameRequestUsesTheApplicationErrorFormat() throws Exception {
        when(matchService.getGameState(GAME_ID)).thenThrow(new IllegalArgumentException("Invalid game"));

        mvc.perform(get("/api/games/{gameId}/state", GAME_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid request"));
    }

    @Test
    void missingGameReturnsNotFound() throws Exception {
        when(matchService.getGameState(GAME_ID))
                .thenThrow(new GameNotFoundException("Game not found: " + GAME_ID));

        mvc.perform(get("/api/games/{gameId}/state", GAME_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Game not found: " + GAME_ID));
    }

    @Test
    void unexpectedFailureDoesNotExposeInternalDetails() throws Exception {
        when(matchService.getGameState(GAME_ID))
                .thenThrow(new RuntimeException("private connection detail"));

        mvc.perform(get("/api/games/{gameId}/state", GAME_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void invalidUuidReturnsBadRequestRatherThanInternalError() throws Exception {
        mvc.perform(get("/api/games/not-a-uuid/state")
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/games/not-a-uuid/state"));
        verifyNoInteractions(matchService);
    }

    @Test
    void missingColorReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/games").with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(matchService);
    }

    @Test
    void invalidColorReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/games").param("color", "PURPLE")
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(matchService);
    }

    @Test
    void unsupportedMethodPreservesAllowHeader() throws Exception {
        mvc.perform(post("/api/games/{gameId}/state", GAME_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(header().string("Allow", containsString("GET")));
        verifyNoInteractions(matchService);
    }

    @Test
    void missingRouteUsesProblemDetails() throws Exception {
        mvc.perform(get("/api/nonexistent").with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void accessDeniedInControllerReturnsForbiddenWithoutInternalDetails() throws Exception {
        when(matchService.getGameState(GAME_ID)).thenThrow(new AccessDeniedException("private policy"));
        mvc.perform(get("/api/games/{gameId}/state", GAME_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action"));
    }

    @Test
    void unexpectedIllegalStateIsAnInternalFailureRatherThanConflict() throws Exception {
        when(matchService.getGameState(GAME_ID)).thenThrow(new IllegalStateException("private state"));
        mvc.perform(get("/api/games/{gameId}/state", GAME_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void gameOverIsAnExplicitDomainConflict() throws Exception {
        when(matchService.joinGame(GAME_ID, PLAYER.toPlayer())).thenThrow(new GameIsOverException("Game is already over"));
        mvc.perform(post("/api/games/{gameId}/join", GAME_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Game is already over"));
    }

    @SpringBootConfiguration
    @Import({GameController.class, SecurityConfig.class, GlobalExceptionHandler.class})
    static class TestConfiguration {
    }
}
