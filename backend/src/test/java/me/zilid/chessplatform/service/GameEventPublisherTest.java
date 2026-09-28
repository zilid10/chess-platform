package me.zilid.chessplatform.service;

import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.RegisteredPlayer;
import me.zilid.chessplatform.chess.game.TimeControl;
import me.zilid.chessplatform.model.dto.ChatMessage;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.rating.RatingChange;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GameEventPublisherTest {
    private static final UUID GAME_ID = UUID.fromString("8a169d0a-c121-4d83-a7b3-8ee30f87cfa9");
    private static final RegisteredPlayer WHITE = new RegisteredPlayer(UUID.randomUUID(), "white");
    private static final RegisteredPlayer BLACK = new RegisteredPlayer(UUID.randomUUID(), "black");

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final MatchService matchService = mock(MatchService.class);
    private final Game game = mock(Game.class);
    private final GameEventPublisher publisher = new GameEventPublisher(messagingTemplate, matchService);

    private static GameStateResponse state(GameStatus status) {
        return new GameStateResponse(status, "fen", "e2", "e4", "BLACK", 0, 60_000, false, null);
    }

    @Test
    void ongoingUpdateIsOnlyPublished() {
        GameStateResponse state = state(GameStatus.ONGOING);

        publisher.publishUpdate(GAME_ID, state);

        verify(messagingTemplate).convertAndSend("/topic/game." + GAME_ID, state);
        verifyNoMoreInteractions(messagingTemplate);
        verifyNoInteractions(matchService);
    }

    @Test
    void finishedGameIsAnnouncedArchivedAndScheduledForCleanup() {
        GameStateResponse flagged = state(GameStatus.FLAGGED_BLACK_WINS);
        when(matchService.getGameOrThrow(GAME_ID)).thenReturn(game);
        when(game.getWhitePlayer()).thenReturn(WHITE);
        when(game.getBlackPlayer()).thenReturn(BLACK);
        when(game.getTimeControl()).thenReturn(TimeControl.BLITZ);
        when(matchService.archiveMatch(GAME_ID, game)).thenReturn(
                new RatingChange(WHITE.id(), BLACK.id(), 1190, 1210, -10, 10));

        publisher.publishUpdate(GAME_ID, flagged);

        verify(messagingTemplate).convertAndSend("/topic/game." + GAME_ID, flagged);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/game." + GAME_ID + ".chat"), payload.capture());
        assertThat(payload.getAllValues()).map(message -> ((ChatMessage) message).message()).containsExactly(
                "Game Over: Black wins by flag",
                "BLITZ ratings: white 1190 (-10), black 1210 (+10)");
        verify(matchService).scheduleGameCleanup(GAME_ID);
    }

    @Test
    void abortedGameIsAnnouncedAndCleanedUpButNotArchived() {
        GameStateResponse aborted = new GameStateResponse(GameStatus.ABORTED, "fen", null, null, "WHITE",
                60_000, 60_000, false, null);

        publisher.publishUpdate(GAME_ID, aborted);

        verify(messagingTemplate).convertAndSend("/topic/game." + GAME_ID, aborted);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game." + GAME_ID + ".chat"), payload.capture());
        assertThat(((ChatMessage) payload.getValue()).message())
                .isEqualTo("Game aborted: White did not make a first move in time");
        verify(matchService).scheduleGameCleanup(GAME_ID);
        verify(matchService, never()).archiveMatch(any(), any());
    }

    @Test
    void archiveFailureStillAnnouncesTheResult() {
        GameStateResponse resigned = state(GameStatus.RESIGNED_WHITE_WINS);
        when(matchService.getGameOrThrow(GAME_ID)).thenReturn(game);
        when(matchService.archiveMatch(GAME_ID, game)).thenThrow(new IllegalStateException("database down"));

        publisher.publishUpdate(GAME_ID, resigned);

        verify(messagingTemplate).convertAndSend("/topic/game." + GAME_ID, resigned);
        verify(messagingTemplate).convertAndSend(eq("/topic/game." + GAME_ID + ".chat"), any(Object.class));
        verify(matchService, never()).scheduleGameCleanup(any());
    }
}
