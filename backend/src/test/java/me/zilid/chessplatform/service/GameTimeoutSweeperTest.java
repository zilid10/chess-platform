package me.zilid.chessplatform.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.repository.game.GameStateStore;
import me.zilid.chessplatform.util.MutableClock;
import org.junit.jupiter.api.Test;

class GameTimeoutSweeperTest {
    private static final UUID EXPIRED = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:10:00Z"));
    private final GameStateStore store = mock(GameStateStore.class);
    private final MatchService matchService = mock(MatchService.class);
    private final GameEventPublisher publisher = mock(GameEventPublisher.class);
    private final GameTimeoutSweeper sweeper = new GameTimeoutSweeper(store, matchService, publisher, clock);

    private static GameStateResponse flagged() {
        return new GameStateResponse(GameStatus.FLAGGED_BLACK_WINS, "fen", "e7", "e5", "WHITE", 0, 60_000, false, null);
    }

    private Game ongoingGame() {
        Game game = mock(Game.class);
        when(game.isGameOver()).thenReturn(false);
        return game;
    }

    @Test
    void asksForDeadlinesUpToTheClocksTime() {
        when(store.findTimeoutsDue(any(Instant.class), anyInt())).thenReturn(List.of());

        sweeper.sweep();

        verify(store).findTimeoutsDue(eq(clock.instant()), anyInt());
    }

    @Test
    void publishesGamesThatThisSweepEnded() {
        when(store.findTimeoutsDue(any(Instant.class), anyInt())).thenReturn(List.of(EXPIRED));
        Game game = ongoingGame();
        when(matchService.getGameSession(EXPIRED)).thenReturn(game);
        GameStateResponse state = flagged();
        when(matchService.checkTimeout(EXPIRED)).thenReturn(Optional.of(state));

        sweeper.sweep();

        verify(publisher).publishUpdate(EXPIRED, state);
    }

    @Test
    void doesNotPublishWhenAnotherInstanceEndedTheGameFirst() {
        when(store.findTimeoutsDue(any(Instant.class), anyInt())).thenReturn(List.of(EXPIRED));
        Game game = ongoingGame();
        when(matchService.getGameSession(EXPIRED)).thenReturn(game);
        when(matchService.checkTimeout(EXPIRED)).thenReturn(Optional.empty());

        sweeper.sweep();

        verifyNoInteractions(publisher);
    }

    @Test
    void forgetsGamesThatAreGoneOrAlreadyOver() {
        when(store.findTimeoutsDue(any(Instant.class), anyInt())).thenReturn(List.of(EXPIRED, OTHER));
        Game finished = mock(Game.class);
        when(finished.isGameOver()).thenReturn(true);
        when(matchService.getGameSession(EXPIRED)).thenReturn(null);
        when(matchService.getGameSession(OTHER)).thenReturn(finished);

        sweeper.sweep();

        verify(store).clearTimeoutDeadline(EXPIRED);
        verify(store).clearTimeoutDeadline(OTHER);
        verify(matchService, never()).checkTimeout(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void oneFailingGameDoesNotStopTheSweep() {
        when(store.findTimeoutsDue(any(Instant.class), anyInt())).thenReturn(List.of(EXPIRED, OTHER));
        Game first = ongoingGame();
        Game second = ongoingGame();
        when(matchService.getGameSession(EXPIRED)).thenReturn(first);
        when(matchService.getGameSession(OTHER)).thenReturn(second);
        when(matchService.checkTimeout(EXPIRED)).thenThrow(new IllegalStateException("Game is busy, please try again"));
        GameStateResponse state = flagged();
        when(matchService.checkTimeout(OTHER)).thenReturn(Optional.of(state));

        sweeper.sweep();

        verify(publisher).publishUpdate(OTHER, state);
        verify(store, never()).clearTimeoutDeadline(EXPIRED);
    }
}
