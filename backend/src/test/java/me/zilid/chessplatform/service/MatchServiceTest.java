package me.zilid.chessplatform.service;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.game.*;
import me.zilid.chessplatform.exception.GameIsOverException;
import me.zilid.chessplatform.exception.GameNotFoundException;
import me.zilid.chessplatform.model.converter.MatchRecordConverter;
import me.zilid.chessplatform.model.dto.GameCreatedResponse;
import me.zilid.chessplatform.model.dto.GameJoinResponse;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.entity.MatchRecord;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.rating.RatingChange;
import me.zilid.chessplatform.repository.MatchRecordRepo;
import me.zilid.chessplatform.repository.UserRepo;
import me.zilid.chessplatform.repository.game.GameStateStore;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MatchServiceTest {
    private final RegisteredPlayer alice = player("alice");
    private final RegisteredPlayer bob = player("bob");
    private final RegisteredPlayer spectator = player("spectator");
    private MatchRecordRepo matchRecordRepo;
    private UserRepo userRepo;
    private RatingService ratingService;
    private GameStateStore gameStateStore;
    private MatchService service;

    /**
     * Stands in for Redis: a map for storage and a real lock, so races are still serialized per game.
     */
    @SuppressWarnings("unchecked")
    private static GameStateStore inMemoryGameStateStore() {
        GameStateStore store = mock(GameStateStore.class);
        Map<UUID, Game> games = new ConcurrentHashMap<>();
        ReentrantLock lock = new ReentrantLock();
        doAnswer(invocation -> games.put(invocation.getArgument(0), copyGame(invocation.getArgument(1))))
                .when(store).storeGame(any(), any());
        when(store.loadGame(any())).thenAnswer(invocation -> {
            Game game = games.get(invocation.<UUID>getArgument(0));
            return game == null ? null : copyGame(game);
        });
        when(store.withLock(any(), any())).thenAnswer(invocation -> {
            lock.lock();
            try {
                return invocation.<Supplier<Object>>getArgument(1).get();
            } finally {
                lock.unlock();
            }
        });
        return store;
    }

    private static RegisteredPlayer player(String username) {
        return new RegisteredPlayer(UUID.randomUUID(), username);
    }

    private static RegisteredPlayer player(User user) {
        return new RegisteredPlayer(user.getId(), user.getUsername());
    }

    private static Game copyGame(Game game) {
        return TestGames.copy(game);
    }

    @BeforeEach
    void setUp() {
        matchRecordRepo = mock(MatchRecordRepo.class);
        userRepo = mock(UserRepo.class);
        ratingService = mock(RatingService.class);
        gameStateStore = inMemoryGameStateStore();
        service = newService();
    }

    @Test
    void gamesAreStoredAndReloadedBetweenCalls() {
        UUID gameId = gameWithBothPlayers();
        service.makeMove(alice, gameId, "e2", "e4", null, Instant.now());

        Game reloaded = service.getGameSession(gameId);
        assertThat(reloaded.getMoves()).hasSize(1);
        assertThat(reloaded.getWhitePlayer()).isEqualTo(alice);
        assertThat(reloaded.getBlackPlayer()).isEqualTo(bob);
        assertThat(reloaded.getTurnColor()).isEqualTo(Color.BLACK);
    }

    @Test
    void rejectedActionsAreNotStored() {
        UUID gameId = gameWithBothPlayers();
        String fenBefore = gameStateStore.loadGame(gameId).getFen();

        assertThatThrownBy(() -> service.makeMove(alice, gameId, "e2", "e5", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(gameStateStore.loadGame(gameId).getFen()).isEqualTo(fenBefore);
        assertThat(gameStateStore.loadGame(gameId).getMoves()).isEmpty();
    }

    @Test
    void finishedGamesExpireShortly() {
        UUID gameId = gameWithBothPlayers();

        service.scheduleGameCleanup(gameId);

        verify(gameStateStore).expireGame(gameId, Duration.ofMinutes(1));
    }

    @Test
    void creatorKeepsSeatWhenReconnectingAndThirdUserSpectates() {
        GameCreatedResponse created = service.createGame(alice, Color.WHITE, ClockSetting.ofMinutes(3, 2));
        UUID gameId = created.gameId();

        assertThat(created.color()).isEqualTo(Color.WHITE);
        assertThat(created.clockSetting()).isEqualTo("3+2");
        assertThat(created.timeControl()).isEqualTo(TimeControl.BLITZ);
        assertThat(service.getGameSession(gameId).getTimeControl()).isEqualTo(TimeControl.BLITZ);
        assertThat(created.socketUrl()).isEqualTo("/game/" + gameId);
        assertThat(created.fen()).isEqualTo(service.getGameState(gameId).fen());
        assertThat(service.joinGame(gameId, alice).role()).isEqualTo("WHITE");
        assertThat(service.joinGame(gameId, bob).role()).isEqualTo("BLACK");
        assertThat(service.joinGame(gameId, spectator).role()).isEqualTo("SPECTATOR");
        assertThat(service.getGameSession(gameId).getWhitePlayer()).isEqualTo(alice);
        assertThat(service.getGameSession(gameId).getBlackPlayer()).isEqualTo(bob);
    }

    @Test
    void joiningBlackCreatorsGameTakesOpenWhiteSeat() {
        UUID gameId = service.createGame(alice, Color.BLACK, ClockSetting.ofMinutes(30, 0)).gameId();

        GameJoinResponse joined = service.joinGame(gameId, bob);

        assertThat(joined.role()).isEqualTo("WHITE");
        assertThat(joined.clockSetting()).isEqualTo("30+0");
        assertThat(joined.timeControl()).isEqualTo(TimeControl.CLASSICAL);
        assertThat(joined.status()).isEqualTo(GameStatus.ONGOING);
        assertThat(joined.currentTurn()).isEqualTo("WHITE");
        assertThat(service.joinGame(gameId, alice).role()).isEqualTo("BLACK");
    }

    @Test
    void concurrentJoinersCannotClaimTheSameOpenSeat() throws Exception {
        UUID gameId = UUID.randomUUID();
        CountDownLatch bothJoinersReachedOpenSeat = new CountDownLatch(2);
        Game game = new Game(alice, null, TestGames.TEN_MINUTES) {
            private final ThreadLocal<Integer> blackReads = ThreadLocal.withInitial(() -> 0);

            @Override
            public @Nullable Player getBlackPlayer() {
                int readCount = blackReads.get() + 1;
                blackReads.set(readCount);
                if (readCount == 2) {
                    // Both unsynchronized joiners can reach the open-seat read together.
                    // The timeout also lets a serialized join finish while its peer waits.
                    bothJoinersReachedOpenSeat.countDown();
                    try {
                        bothJoinersReachedOpenSeat.await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Join interrupted", e);
                    }
                }
                return super.getBlackPlayer();
            }
        };
        MatchService raceService = new MatchService(matchRecordRepo, mock(MatchRecordConverter.class), userRepo,
                ratingService, gameStateStore) {
            @Override
            public Game getGameOrThrow(UUID requestedGameId) {
                assertThat(requestedGameId).isEqualTo(gameId);
                return game;
            }
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<GameJoinResponse> bobJoin = executor.submit(() -> raceService.joinGame(gameId, bob));
            Future<GameJoinResponse> spectatorJoin = executor.submit(() -> raceService.joinGame(gameId, spectator));

            String bobRole = bobJoin.get(5, TimeUnit.SECONDS).role();
            String spectatorRole = spectatorJoin.get(5, TimeUnit.SECONDS).role();
            assertThat(List.of(bobRole, spectatorRole)).containsExactlyInAnyOrder("BLACK", "SPECTATOR");
            assertThat(game.getBlackPlayer()).isEqualTo("BLACK".equals(bobRole) ? bob : spectator);
        }
    }

    @Test
    void unknownGameCannotBeJoinedOrRead() {
        UUID missingId = UUID.randomUUID();

        assertThatThrownBy(() -> service.joinGame(missingId, alice))
                .isInstanceOf(GameNotFoundException.class);
        assertThatThrownBy(() -> service.getGameState(missingId))
                .isInstanceOf(GameNotFoundException.class);
    }

    @Test
    void movesAreValidatedByTheServiceAndReturnTheUpdatedState() {
        UUID gameId = gameWithBothPlayers();

        assertThatThrownBy(() -> service.makeMove(spectator, gameId, "e2", "e4", null, Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You are not a player in this game");
        assertThatThrownBy(() -> service.makeMove(bob, gameId, "e7", "e5", null, Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("It is not your turn");
        assertThatThrownBy(() -> service.makeMove(alice, gameId, "e2", "e5", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid move: e2 to e5");

        GameStateResponse afterWhiteMove = service.makeMove(alice, gameId, "e2", "e4", null, Instant.now());
        assertThat(afterWhiteMove.lastMoveFrom()).isEqualTo("e2");
        assertThat(afterWhiteMove.lastMoveTo()).isEqualTo("e4");
        assertThat(afterWhiteMove.turnColor()).isEqualTo("BLACK");
        assertThat(afterWhiteMove.fen()).isEqualTo(service.getGameState(gameId).fen());

        assertThatThrownBy(() -> service.makeMove(alice, gameId, "d2", "d4", null, Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("It is not your turn");
        assertThat(service.makeMove(bob, gameId, "e7", "e5", null, Instant.now()).turnColor()).isEqualTo("WHITE");
    }

    @Test
    void completedGameRejectsMoves() {
        UUID gameId = gameWithBothPlayers();
        service.resign(alice, gameId);

        assertThatThrownBy(() -> service.makeMove(bob, gameId, "e7", "e5", null, Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Game is already over");
    }

    @Test
    void simultaneousMovesCannotBothPassTheTurnCheck() throws Exception {
        UUID gameId = UUID.randomUUID();
        CountDownLatch bothTurnChecksReached = new CountDownLatch(2);
        Game game = new Game(alice, bob, TestGames.TEN_MINUTES) {
            @Override
            public boolean isUserTurn(Player user) {
                boolean isTurn = super.isUserTurn(user);
                bothTurnChecksReached.countDown();
                try {
                    // If checks run independently, both calls observe the same turn.
                    bothTurnChecksReached.await(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Move interrupted", e);
                }
                return isTurn;
            }
        };
        MatchService raceService = new MatchService(matchRecordRepo, mock(MatchRecordConverter.class), userRepo,
                ratingService, gameStateStore) {
            @Override
            public Game getGameOrThrow(UUID requestedGameId) {
                assertThat(requestedGameId).isEqualTo(gameId);
                return game;
            }
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Object> firstMove = () -> moveOrFailure(raceService, gameId, "e2", "e4");
            Callable<Object> secondMove = () -> moveOrFailure(raceService, gameId, "d2", "d4");
            Future<Object> first = executor.submit(firstMove);
            Future<Object> second = executor.submit(secondMove);
            List<Object> results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

            assertThat(results.stream().filter(GameStateResponse.class::isInstance).count()).isEqualTo(1);
            assertThat(results.stream().filter(IllegalStateException.class::isInstance)
                    .map(result -> ((IllegalStateException) result).getMessage()))
                    .containsExactly("It is not your turn");
        }
    }

    @Test
    void spectatorCannotOfferDrawAcceptDrawOrResign() {
        UUID gameId = gameWithBothPlayers();

        assertThatThrownBy(() -> service.offerDraw(spectator, gameId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You are not a player in this game");
        assertThatThrownBy(() -> service.acceptDraw(spectator, gameId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You are not a player in this game");
        assertThatThrownBy(() -> service.resign(spectator, gameId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You are not a player in this game");
        assertThat(service.getGameState(gameId).gameStatus()).isEqualTo(GameStatus.ONGOING);
    }

    @Test
    void drawNeedsOfferAndAcceptanceByOpponent() {
        UUID gameId = gameWithBothPlayers();

        assertThat(service.acceptDraw(bob, gameId).gameStatus()).isEqualTo(GameStatus.ONGOING);
        service.offerDraw(alice, gameId);
        assertThat(service.getGameSession(gameId).getDrawOfferedBy()).isEqualTo(Color.WHITE);
        assertThat(service.acceptDraw(alice, gameId).gameStatus()).isEqualTo(GameStatus.ONGOING);

        GameStateResponse accepted = service.acceptDraw(bob, gameId);
        assertThat(accepted.gameStatus()).isEqualTo(GameStatus.DRAW_BY_AGREEMENT);
        assertThat(service.getGameSession(gameId).getDrawOfferedBy()).isNull();
        assertThat(service.getGameSession(gameId).getEndTime()).isNotNull();
    }

    @Test
    void resignationAwardsWinToOpponentAndCompletedGameRejectsFurtherActions() {
        UUID gameId = gameWithBothPlayers();

        GameStateResponse state = service.resign(alice, gameId);

        assertThat(state.gameStatus()).isEqualTo(GameStatus.RESIGNED_BLACK_WINS);
        assertThat(service.getGameSession(gameId).getEndTime()).isNotNull();
        assertThatThrownBy(() -> service.resign(bob, gameId))
                .isInstanceOf(GameIsOverException.class);
        assertThatThrownBy(() -> service.acceptDraw(bob, gameId))
                .isInstanceOf(GameIsOverException.class);
        assertThatThrownBy(() -> service.offerDraw(bob, gameId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Game is over");
    }

    @Test
    void onlyFinishedGamesCanBeArchived() {
        Game game = TestGames.game(alice, bob);

        assertThatThrownBy(() -> service.archiveMatch(UUID.randomUUID(), game))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Game is not over");
        verify(matchRecordRepo, never()).save(any());
        verifyNoInteractions(ratingService);
    }

    @Test
    void archiveStoresPlayersResultNotationTimesAndRatingChange() {
        User aliceEntity = new User("alice@example.com", "alice", "hash", "");
        User bobEntity = new User("bob@example.com", "bob", "hash", "");
        Game game = new Game(player(aliceEntity), player(bobEntity), ClockSetting.ofMinutes(3, 2));
        game.resign(Color.WHITE);
        when(userRepo.getReferenceById(aliceEntity.getId())).thenReturn(aliceEntity);
        when(userRepo.getReferenceById(bobEntity.getId())).thenReturn(bobEntity);
        RatingChange ratingChange = new RatingChange(aliceEntity.getId(), bobEntity.getId(), 1180, 1220, -20, 20);
        when(ratingService.applyResult(aliceEntity.getId(), bobEntity.getId(), TimeControl.BLITZ,
                GameStatus.RESIGNED_BLACK_WINS)).thenReturn(ratingChange);

        assertThat(service.archiveMatch(UUID.randomUUID(), game)).isEqualTo(ratingChange);

        ArgumentCaptor<MatchRecord> record = ArgumentCaptor.forClass(MatchRecord.class);
        verify(matchRecordRepo).save(record.capture());
        MatchRecord saved = record.getValue();
        assertThat(saved.getWhitePlayer()).isSameAs(aliceEntity);
        assertThat(saved.getBlackPlayer()).isSameAs(bobEntity);
        assertThat(saved.getMatchResult()).isEqualTo("0-1");
        assertThat(saved.getReason()).isEqualTo("RESIGNATION");
        assertThat(saved.getPgn()).contains("[White \"alice\"]", "[Black \"bob\"]", "[Result \"0-1\"]");
        assertThat(saved.getStartTime()).isEqualTo(game.getStartTime());
        assertThat(saved.getEndTime()).isEqualTo(game.getEndTime());
        assertThat(saved.getTimeControl()).isEqualTo(TimeControl.BLITZ);
        assertThat(saved.getWhiteRating()).isEqualTo(1180);
        assertThat(saved.getBlackRating()).isEqualTo(1220);
        assertThat(saved.getWhiteRatingChange()).isEqualTo(-20);
        assertThat(saved.getBlackRatingChange()).isEqualTo(20);
    }

    @Test
    void matchHistoryUsesOneDescendingEndTimeQuery() {
        UUID userId = alice.id();
        when(matchRecordRepo.findByWhitePlayer_IdOrBlackPlayer_Id(eq(userId), eq(userId), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.findMatches(userId, PageRequest.of(2, 5));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(matchRecordRepo).findByWhitePlayer_IdOrBlackPlayer_Id(eq(userId), eq(userId), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getPageSize()).isEqualTo(5);
        assertThat(page.getValue().getSort().getOrderFor("endTime").isDescending()).isTrue();
    }

    @Nested
    class Timeout {
        // Far enough back that the real clock is past White's deadline too
        private final Instant t0 = Instant.now().minus(Duration.ofMinutes(30));
        private final Instant deadline = t0.plusSeconds(1).plus(Duration.ofMinutes(10));

        // White moves at t0 and Black at t0 + 1s, which starts White's ten minutes
        private UUID gameWithWhiteToMove() {
            UUID gameId = gameWithBothPlayers();
            service.makeMove(alice, gameId, "e2", "e4", null, t0);
            service.makeMove(bob, gameId, "e7", "e5", null, t0.plusSeconds(1));
            return gameId;
        }

        @Test
        void nothingHappensBeforeTheDeadline() {
            UUID gameId = gameWithWhiteToMove();

            assertThat(service.checkTimeout(gameId, deadline.minusMillis(1))).isEmpty();

            assertThat(service.getGameSession(gameId).getStatus()).isEqualTo(GameStatus.ONGOING);
        }

        @Test
        void expiredGameIsEndedAndStored() {
            UUID gameId = gameWithWhiteToMove();

            GameStateResponse state = service.checkTimeout(gameId, deadline).orElseThrow();

            assertThat(state.gameStatus()).isEqualTo(GameStatus.FLAGGED_BLACK_WINS);
            assertThat(state.whiteRemainingMillis()).isZero();
            assertThat(state.clockRunning()).isFalse();
            Game stored = service.getGameSession(gameId);
            assertThat(stored.getStatus()).isEqualTo(GameStatus.FLAGGED_BLACK_WINS);
            assertThat(stored.getEndTime()).isEqualTo(deadline);
        }

        @Test
        void onlyTheFirstCheckReportsTheResult() {
            UUID gameId = gameWithWhiteToMove();

            assertThat(service.checkTimeout(gameId, deadline)).isPresent();
            assertThat(service.checkTimeout(gameId, deadline.plusSeconds(1))).isEmpty();
        }

        @Test
        void concurrentChecksEndTheGameOnce() throws Exception {
            UUID gameId = gameWithWhiteToMove();
            int checkers = 8;
            CountDownLatch start = new CountDownLatch(1);

            try (ExecutorService executor = Executors.newFixedThreadPool(checkers)) {
                List<Future<Optional<GameStateResponse>>> results = new ArrayList<>();
                for (int i = 0; i < checkers; i++) {
                    results.add(executor.submit(() -> {
                        start.await();
                        return service.checkTimeout(gameId, deadline);
                    }));
                }
                start.countDown();

                long ended = 0;
                for (Future<Optional<GameStateResponse>> result : results) {
                    ended += result.get(5, TimeUnit.SECONDS).isPresent() ? 1 : 0;
                }
                assertThat(ended).isEqualTo(1);
            }
        }

        @Test
        void missingGameHasNoTimeout() {
            assertThat(service.checkTimeout(UUID.randomUUID(), Instant.now())).isEmpty();
        }

        @Test
        void aMoveAfterTheDeadlineEndsTheGameOnTime() {
            UUID gameId = gameWithWhiteToMove();

            GameStateResponse state = service.makeMove(alice, gameId, "g1", "f3", null, deadline.plusSeconds(1));

            assertThat(state.gameStatus()).isEqualTo(GameStatus.FLAGGED_BLACK_WINS);
            assertThat(service.getGameSession(gameId).getMoves()).hasSize(2);
            assertThat(service.checkTimeout(gameId, deadline.plusSeconds(2))).isEmpty();
        }

        @Test
        void actingAfterTheDeadlineEndsTheGameOnTimeFirst() {
            UUID gameId = gameWithWhiteToMove();

            assertThat(service.resign(bob, gameId).gameStatus()).isEqualTo(GameStatus.FLAGGED_BLACK_WINS);
            assertThatThrownBy(() -> service.acceptDraw(alice, gameId)).isInstanceOf(GameIsOverException.class);
        }

        @Test
        void drawOfferAfterTheDeadlineIsRejected() {
            UUID gameId = gameWithWhiteToMove();

            assertThatThrownBy(() -> service.offerDraw(bob, gameId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Game is over");
            assertThat(service.getGameSession(gameId).getDrawOfferedBy()).isNull();
        }
    }

    private MatchService newService() {
        return new MatchService(matchRecordRepo, mock(MatchRecordConverter.class), userRepo,
                ratingService, gameStateStore);
    }

    private UUID gameWithBothPlayers() {
        UUID gameId = service.createGame(alice, Color.WHITE, TestGames.TEN_MINUTES).gameId();
        service.joinGame(gameId, bob);
        return gameId;
    }

    private Object moveOrFailure(MatchService raceService, UUID gameId, String from, String to) {
        try {
            return raceService.makeMove(alice, gameId, from, to, null, Instant.now());
        } catch (RuntimeException e) {
            return e;
        }
    }
}
