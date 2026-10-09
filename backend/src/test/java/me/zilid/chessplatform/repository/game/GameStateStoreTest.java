package me.zilid.chessplatform.repository.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.Move;
import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.Square;
import me.zilid.chessplatform.chess.game.ClockSetting;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.RegisteredPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import tools.jackson.databind.json.JsonMapper;

class GameStateStoreTest {
    private static final UUID GAME_ID = UUID.randomUUID();

    private static final Instant T0 = Instant.parse("2026-09-27T10:15:30Z");

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> values;
    private ZSetOperations<String, String> sortedSets;
    private GameStateStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        sortedSets = mock(ZSetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(redisTemplate.opsForZSet()).thenReturn(sortedSets);
        store = new GameStateStore(redisTemplate, JsonMapper.builder().build(), new ActiveGameStateConverter());
    }

    @Test
    void storedStateSurvivesJsonRoundTrip() {
        ActiveGameState state = new ActiveGameState(
                List.of(
                        Move.doublePush(Square.fromNotation("e2"), Square.fromNotation("e4")),
                        Move.promotion(Square.fromNotation("b7"), Square.fromNotation("a8"), PieceType.KNIGHT),
                        Move.castleKingside(Color.BLACK)),
                ClockSetting.ofMinutes(3, 2),
                Duration.ofMillis(171_250),
                Duration.ofMinutes(3),
                Instant.parse("2026-09-27T10:16:30Z"),
                Color.WHITE,
                Instant.parse("2026-09-27T10:15:30Z"),
                null,
                GameStatus.ONGOING,
                new StoredPlayer(UUID.randomUUID(), "white"),
                null,
                Color.WHITE,
                null);

        JsonMapper mapper = JsonMapper.builder().build();
        assertThat(mapper.readValue(mapper.writeValueAsString(state), ActiveGameState.class))
                .isEqualTo(state);
    }

    @Test
    void storeReconstructsGameClockAndPlayers() {
        Game game = new Game(
                new RegisteredPlayer(UUID.randomUUID(), "white"),
                new RegisteredPlayer(UUID.randomUUID(), "black"),
                ClockSetting.ofMinutes(3, 2));
        assertThat(game.makeMove("e2", "e4", null, T0)).isTrue();
        assertThat(game.makeMove("e7", "e5", null, T0.plusSeconds(4))).isTrue();
        game.offerDraw(Color.WHITE);

        Game restored = storeAndLoad(game);

        assertThat(restored.getFen()).isEqualTo(game.getFen());
        assertThat(restored.getMoves()).isEqualTo(game.getMoves());
        assertThat(restored.getWhitePlayer()).isEqualTo(game.getWhitePlayer());
        assertThat(restored.getWhitePlayer().displayName()).isEqualTo("white");
        assertThat(restored.getBlackPlayer()).isEqualTo(game.getBlackPlayer());
        assertThat(restored.getDrawOfferedBy()).isEqualTo(Color.WHITE);
        assertThat(restored.getClockSetting()).isEqualTo(ClockSetting.ofMinutes(3, 2));
        assertThat(restored.getWhiteRemaining()).isEqualTo(game.getWhiteRemaining());
        assertThat(restored.getBlackRemaining()).isEqualTo(game.getBlackRemaining());
        assertThat(restored.getTurnStartAt()).isEqualTo(T0.plusSeconds(4));
        assertThat(restored.timeoutDeadline()).isEqualTo(game.timeoutDeadline());
        assertThat(restored.makeMove("g1", "f3", null, T0.plusSeconds(5))).isTrue();
    }

    @Test
    void gameWaitingForAnOpponentKeepsTheOpenSeat() {
        Game game = new Game(null, new RegisteredPlayer(UUID.randomUUID(), "black"), ClockSetting.ofMinutes(5, 3));

        Game restored = storeAndLoad(game);

        assertThat(restored.getWhitePlayer()).isNull();
        assertThat(restored.getBlackPlayer()).isEqualTo(game.getBlackPlayer());
    }

    @Test
    void finishedGameStaysFinishedAfterReloading() {
        Game game = new Game(
                new RegisteredPlayer(UUID.randomUUID(), "white"),
                new RegisteredPlayer(UUID.randomUUID(), "black"),
                ClockSetting.ofMinutes(3, 2));
        game.makeMove("e2", "e4", null, T0);
        game.makeMove("e7", "e5", null, T0.plusSeconds(1));
        assertThat(game.checkTimeout(T0.plus(Duration.ofMinutes(10)))).isTrue();

        Game restored = storeAndLoad(game);

        assertThat(restored.getStatus()).isEqualTo(GameStatus.FLAGGED_BLACK_WINS);
        assertThat(restored.getEndTime()).isEqualTo(game.getEndTime());
        assertThat(restored.getWhiteRemaining()).isZero();
        assertThat(restored.timeoutDeadline()).isNull();
    }

    @Test
    void storingAGameWithARunningClockIndexesItsDeadline() {
        Game game = new Game(
                new RegisteredPlayer(UUID.randomUUID(), "white"),
                new RegisteredPlayer(UUID.randomUUID(), "black"),
                ClockSetting.ofMinutes(3, 2));
        game.makeMove("e2", "e4", null, T0);
        game.makeMove("e7", "e5", null, T0.plusSeconds(1));

        store.storeGame(GAME_ID, game);

        verify(sortedSets).add(GameStateStore.TIMEOUT_DEADLINES_KEY, GAME_ID.toString(), (double)
                game.timeoutDeadline().toEpochMilli());
        verify(sortedSets, never()).remove(any(), any());
    }

    @Test
    void firstMoveDeadlineSurvivesReloadingAndIsIndexed() {
        Game game = new Game(
                new RegisteredPlayer(UUID.randomUUID(), "white"),
                new RegisteredPlayer(UUID.randomUUID(), "black"),
                ClockSetting.ofMinutes(3, 2),
                T0);

        Game restored = storeAndLoad(game);

        assertThat(restored.getFirstMoveDeadline()).isEqualTo(T0.plus(Game.FIRST_MOVE_TIMEOUT));
        verify(sortedSets).add(GameStateStore.TIMEOUT_DEADLINES_KEY, GAME_ID.toString(), (double)
                T0.plus(Game.FIRST_MOVE_TIMEOUT).toEpochMilli());
    }

    @Test
    void storingAGameWithoutARunningClockClearsItsDeadline() {
        store.storeGame(GAME_ID, new Game(null, null, ClockSetting.ofMinutes(3, 2)));

        verify(sortedSets).remove(GameStateStore.TIMEOUT_DEADLINES_KEY, GAME_ID.toString());
        verify(sortedSets, never()).add(any(), any(), anyDouble());
    }

    @Test
    void dueTimeoutsAreReadUpToNow() {
        UUID other = UUID.randomUUID();
        when(sortedSets.rangeByScore(
                        GameStateStore.TIMEOUT_DEADLINES_KEY,
                        Double.NEGATIVE_INFINITY,
                        (double) T0.toEpochMilli(),
                        0,
                        50))
                .thenReturn(new LinkedHashSet<>(List.of(GAME_ID.toString(), other.toString())));

        assertThat(store.findTimeoutsDue(T0, 50)).containsExactly(GAME_ID, other);
    }

    @Test
    void storedJsonFromBeforeTheClockDoesNotLoad() {
        when(values.get("game:" + GAME_ID)).thenReturn("""
                {"history":[],"startTime":"2026-09-27T10:15:30Z","endTime":null,
                 "timeControl":"RAPID","status":"ONGOING","whitePlayerId":"%s",
                 "blackPlayerId":null,"drawOfferedBy":null}
                """.formatted(UUID.randomUUID()));

        assertThatThrownBy(() -> store.loadGame(GAME_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failed to parse game state");
    }

    private Game storeAndLoad(Game game) {
        store.storeGame(GAME_ID, game);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("game:" + GAME_ID), json.capture(), eq(GameStateStore.GAME_TTL));
        when(values.get("game:" + GAME_ID)).thenReturn(json.getValue());
        return store.loadGame(GAME_ID);
    }

    @Test
    void missingGameLoadsAsNull() {
        assertThat(store.loadGame(GAME_ID)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void actionExceptionsEscapeTheSpringBeanUntranslated() {
        when(values.setIfAbsent(eq("game:" + GAME_ID + ":lock"), anyString(), eq(GameStateStore.LOCK_TTL)))
                .thenReturn(true);
        IllegalStateException notYourTurn = new IllegalStateException("It is not your turn");

        // The JPA dialect is the translator the application context registers alongside Hibernate.
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PersistenceExceptionTranslationPostProcessor.class);
            context.registerBean(HibernateJpaDialect.class);
            context.registerBean(GameStateStore.class, () -> store);
            context.refresh();
            GameStateStore bean = context.getBean(GameStateStore.class);

            assertThatThrownBy(() -> bean.withLock(GAME_ID, () -> {
                        throw notYourTurn;
                    }))
                    .isSameAs(notYourTurn);
        }
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), anyString());
    }
}
