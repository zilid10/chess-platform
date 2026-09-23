package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.config.SecurityConfig;
import me.zilid.chessplatform.exception.GlobalExceptionHandler;
import me.zilid.chessplatform.model.dto.GameCreatedResponse;
import me.zilid.chessplatform.model.dto.GameJoinResponse;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.MatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The production application enables JPA repositories, so keep this MVC slice isolated.
@WebMvcTest(GameController.class)
@ContextConfiguration(classes = GameControllerWebMvcTest.TestConfiguration.class)
class GameControllerWebMvcTest {

    @SpringBootConfiguration
    @Import({GameController.class, SecurityConfig.class, GlobalExceptionHandler.class})
    static class TestConfiguration {
    }

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
                .andExpect(status().isForbidden());

        verifyNoInteractions(matchService);
    }

    @Test
    void creatingAndJoiningGamesRequireAuthentication() throws Exception {
        mvc.perform(post("/api/games").param("color", "WHITE"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/games/{gameId}/join", GAME_ID))
                .andExpect(status().isForbidden());

        verifyNoInteractions(matchService);
    }

    @Test
    void stateEndpointSerializesTheServiceResponse() throws Exception {
        when(matchService.getGameState(GAME_ID)).thenReturn(new GameStateResponse(
                GameStatus.ONGOING, "starting-fen", "e2", "e4", "BLACK"));

        mvc.perform(get("/api/games/{gameId}/state", GAME_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameStatus").value("ONGOING"))
                .andExpect(jsonPath("$.fen").value("starting-fen"))
                .andExpect(jsonPath("$.lastMoveFrom").value("e2"))
                .andExpect(jsonPath("$.lastMoveTo").value("e4"))
                .andExpect(jsonPath("$.turnColor").value("BLACK"));

        verify(matchService).getGameState(GAME_ID);
    }

    @Test
    void createGamePassesAuthenticatedPlayerAndColor() throws Exception {
        when(matchService.createGame(PLAYER, Color.BLACK)).thenReturn(new GameCreatedResponse(
                GAME_ID, Color.BLACK, "starting-fen", "/game/" + GAME_ID));

        mvc.perform(post("/api/games")
                        .param("color", "BLACK")
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.color").value("BLACK"))
                .andExpect(jsonPath("$.socketUrl").value("/game/" + GAME_ID));

        verify(matchService).createGame(PLAYER, Color.BLACK);
    }

    @Test
    void joinGameReturnsTheAssignedRole() throws Exception {
        when(matchService.joinGame(GAME_ID, PLAYER)).thenReturn(new GameJoinResponse(
                GAME_ID, "WHITE", "starting-fen", GameStatus.ONGOING, "WHITE"));

        mvc.perform(post("/api/games/{gameId}/join", GAME_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PLAYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("WHITE"))
                .andExpect(jsonPath("$.currentTurn").value("WHITE"));

        verify(matchService).joinGame(GAME_ID, PLAYER);
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
                .andExpect(jsonPath("$.error").value("Invalid game"));
    }
}
