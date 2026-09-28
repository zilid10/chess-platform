package me.zilid.chessplatform.service;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.Player;
import me.zilid.chessplatform.chess.game.RegisteredPlayer;
import me.zilid.chessplatform.chess.game.clock.TimeControl;
import me.zilid.chessplatform.exception.GameIsOverException;
import me.zilid.chessplatform.exception.GameNotFoundException;
import me.zilid.chessplatform.model.converter.ActiveGameStateConverter;
import me.zilid.chessplatform.model.converter.MatchRecordConverter;
import me.zilid.chessplatform.model.dto.ActiveGameState;
import me.zilid.chessplatform.model.dto.GameCreatedResponse;
import me.zilid.chessplatform.model.dto.GameJoinResponse;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.entity.MatchRecord;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.rating.RatingChange;
import me.zilid.chessplatform.repository.GameStateStore;
import me.zilid.chessplatform.repository.MatchRecordRepo;
import me.zilid.chessplatform.repository.UserRepo;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.List;
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
    private ActiveGameStateConverter activeGameStateConverter;
    private MatchService service;

    /**
     * Stands in for Redis: a map for storage and a real lock, so races are still serialized per game.
     */
    @SuppressWarnings("unchecked")
    private static GameStateStore inMemoryGameStateStore() {
        GameStateStore store = mock(GameStateStore.class);
        Map<UUID, ActiveGameState> games = new ConcurrentHashMap<>();
        ReentrantLock lock = new ReentrantLock();
        doAnswer(invocation -> games.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(store).storeGame(any(), any());
        when(store.loadGame(any())).thenAnswer(invocation -> games.get(invocation.<UUID>getArgument(0)));
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

    private static UserPrincipal principal(Player player) {
        String username = player.displayName();
        return new UserPrincipal(player.id(), username, username + "@example.com", "hash", true, List.of());
    }

    @BeforeEach
    void setUp() {
        matchRecordRepo = mock(MatchRecordRepo.class);
        userRepo = mock(UserRepo.class);
        ratingService = mock(RatingService.class);
        gameStateStore = inMemoryGameStateStore();
        UserPrincipalService userPrincipalService = mock(UserPrincipalService.class);
        Map<UUID, UserPrincipal> users = Map.of(
                alice.id(), principal(alice), bob.id(), principal(bob), spectator.id(), principal(spectator));
        when(userPrincipalService.loadUserById(any())).thenAnswer(invocation -> users.get(invocation.<UUID>getArgument(0)));
        activeGameStateConverter = new ActiveGameStateConverter(userPrincipalService);
        service = newService();
    }

    @Test
    void gamesAreStoredAndReloadedBetweenCalls() {
        UUID gameId = gameWithBothPlayers();
        service.makeMove(alice, gameId, "e2", "e4", null);

        Game reloaded = service.getGameSession(gameId);
        assertThat(reloaded.getMoves()).hasSize(1);
        assertThat(reloaded.getWhitePlayer()).isEqualTo(alice);
        assertThat(reloaded.getBlackPlayer()).isEqualTo(bob);
        assertThat(reloaded.getTurnColor()).isEqualTo(Color.BLACK);
    }

    @Test
    void rejectedActionsAreNotStored() {
        UUID gameId = gameWithBothPlayers();
        ActiveGameState before = gameStateStore.loadGame(gameId);

        assertThatThrownBy(() -> service.makeMove(alice, gameId, "e2", "e5", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(gameStateStore.loadGame(gameId)).isEqualTo(before);
    }

    @Test
    void finishedGamesExpireShortly() {
        UUID gameId = gameWithBothPlayers();

        service.scheduleGameCleanup(gameId);

        verify(gameStateStore).expireGame(gameId, Duration.ofMinutes(1));
    }

    @Test
    void creatorKeepsSeatWhenReconnectingAndThirdUserSpectates() {
        GameCreatedResponse created = service.createGame(alice, Color.WHITE, TimeControl.BLITZ);
        UUID gameId = created.gameId();

        assertThat(created.color()).isEqualTo(Color.WHITE);
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
        UUID gameId = service.createGame(alice, Color.BLACK, TimeControl.CLASSICAL).gameId();

        GameJoinResponse joined = service.joinGame(gameId, bob);

        assertThat(joined.role()).isEqualTo("WHITE");
        assertThat(joined.timeControl()).isEqualTo(TimeControl.CLASSICAL);
        assertThat(joined.status()).isEqualTo(GameStatus.ONGOING);
        assertThat(joined.currentTurn()).isEqualTo("WHITE");
        assertThat(service.joinGame(gameId, alice).role()).isEqualTo("BLACK");
    }

    @Test
    void concurrentJoinersCannotClaimTheSameOpenSeat() throws Exception {
        UUID gameId = UUID.randomUUID();
        CountDownLatch bothJoinersReachedOpenSeat = new CountDownLatch(2);
        Game game = new Game(alice, null) {
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
                ratingService, gameStateStore, activeGameStateConverter) {
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

        assertThatThrownBy(() -> service.makeMove(spectator, gameId, "e2", "e4", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You are not a player in this game");
        assertThatThrownBy(() -> service.makeMove(bob, gameId, "e7", "e5", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("It is not your turn");
        assertThatThrownBy(() -> service.makeMove(alice, gameId, "e2", "e5", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid move: e2 to e5");

        GameStateResponse afterWhiteMove = service.makeMove(alice, gameId, "e2", "e4", null);
        assertThat(afterWhiteMove.lastMoveFrom()).isEqualTo("e2");
        assertThat(afterWhiteMove.lastMoveTo()).isEqualTo("e4");
        assertThat(afterWhiteMove.turnColor()).isEqualTo("BLACK");
        assertThat(afterWhiteMove.fen()).isEqualTo(service.getGameState(gameId).fen());

        assertThatThrownBy(() -> service.makeMove(alice, gameId, "d2", "d4", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("It is not your turn");
        assertThat(service.makeMove(bob, gameId, "e7", "e5", null).turnColor()).isEqualTo("WHITE");
    }

    @Test
    void completedGameRejectsMoves() {
        UUID gameId = gameWithBothPlayers();
        service.resign(alice, gameId);

        assertThatThrownBy(() -> service.makeMove(bob, gameId, "e7", "e5", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Game is already over");
    }

    @Test
    void simultaneousMovesCannotBothPassTheTurnCheck() throws Exception {
        UUID gameId = UUID.randomUUID();
        CountDownLatch bothTurnChecksReached = new CountDownLatch(2);
        Game game = new Game(alice, bob) {
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
                ratingService, gameStateStore, activeGameStateConverter) {
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
        Game game = new Game(alice, bob);

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
        Game game = new Game(player(aliceEntity), player(bobEntity), TimeControl.BLITZ);
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

    private MatchService newService() {
        return new MatchService(matchRecordRepo, mock(MatchRecordConverter.class), userRepo,
                ratingService, gameStateStore, activeGameStateConverter);
    }

    private UUID gameWithBothPlayers() {
        UUID gameId = service.createGame(alice, Color.WHITE, TimeControl.RAPID).gameId();
        service.joinGame(gameId, bob);
        return gameId;
    }

    private Object moveOrFailure(MatchService raceService, UUID gameId, String from, String to) {
        try {
            return raceService.makeMove(alice, gameId, from, to, null);
        } catch (RuntimeException e) {
            return e;
        }
    }
}
