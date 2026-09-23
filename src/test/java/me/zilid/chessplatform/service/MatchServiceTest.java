package me.zilid.chessplatform.service;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.exception.GameIsOverException;
import me.zilid.chessplatform.exception.GameNotFoundException;
import me.zilid.chessplatform.model.converter.MatchRecordConverter;
import me.zilid.chessplatform.model.dto.GameCreatedResponse;
import me.zilid.chessplatform.model.dto.GameJoinResponse;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.entity.MatchRecord;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.repository.MatchRecordRepo;
import me.zilid.chessplatform.repository.UserRepo;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchServiceTest {
    private MatchRecordRepo matchRecordRepo;
    private UserRepo userRepo;
    private MatchService service;

    private final UserPrincipal alice = principal("alice");
    private final UserPrincipal bob = principal("bob");
    private final UserPrincipal spectator = principal("spectator");

    @BeforeEach
    void setUp() {
        matchRecordRepo = mock(MatchRecordRepo.class);
        userRepo = mock(UserRepo.class);
        service = new MatchService(matchRecordRepo, mock(MatchRecordConverter.class), userRepo);
    }

    @Test
    void creatorKeepsSeatWhenReconnectingAndThirdUserSpectates() {
        GameCreatedResponse created = service.createGame(alice, Color.WHITE);
        UUID gameId = created.gameId();

        assertThat(created.color()).isEqualTo(Color.WHITE);
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
        UUID gameId = service.createGame(alice, Color.BLACK).gameId();

        GameJoinResponse joined = service.joinGame(gameId, bob);

        assertThat(joined.role()).isEqualTo("WHITE");
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
            public @Nullable UserPrincipal getBlackPlayer() {
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
        MatchService raceService = new MatchService(matchRecordRepo, mock(MatchRecordConverter.class), userRepo) {
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
    }

    @Test
    void archiveStoresPlayersResultNotationAndTimes() {
        User aliceEntity = new User("alice@example.com", "alice", "hash", "");
        User bobEntity = new User("bob@example.com", "bob", "hash", "");
        Game game = new Game(principal(aliceEntity), principal(bobEntity));
        game.resign(Color.WHITE);
        when(userRepo.findById(aliceEntity.getId())).thenReturn(Optional.of(aliceEntity));
        when(userRepo.findById(bobEntity.getId())).thenReturn(Optional.of(bobEntity));

        service.archiveMatch(UUID.randomUUID(), game);

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
    }

    @Test
    void matchHistoryUsesOneDescendingEndTimeQuery() {
        UUID userId = alice.getId();
        when(matchRecordRepo.findByWhitePlayer_IdOrBlackPlayer_Id(eq(userId), eq(userId), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.findMatches(userId, PageRequest.of(2, 5));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(matchRecordRepo).findByWhitePlayer_IdOrBlackPlayer_Id(eq(userId), eq(userId), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getPageSize()).isEqualTo(5);
        assertThat(page.getValue().getSort().getOrderFor("endTime").isDescending()).isTrue();
    }

    private UUID gameWithBothPlayers() {
        UUID gameId = service.createGame(alice, Color.WHITE).gameId();
        service.joinGame(gameId, bob);
        return gameId;
    }

    private static UserPrincipal principal(String username) {
        return new UserPrincipal(UUID.randomUUID(), username, username + "@example.com", "hash", true, List.of());
    }

    private static UserPrincipal principal(User user) {
        return new UserPrincipal(user.getId(), user.getUsername(), user.getEmail(), user.getPasswordHash(), true, List.of());
    }
}
