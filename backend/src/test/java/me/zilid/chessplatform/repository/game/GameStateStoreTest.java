package me.zilid.chessplatform.repository.game;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.Move;
import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.Square;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.RegisteredPlayer;
import me.zilid.chessplatform.chess.game.TimeControl;
import me.zilid.chessplatform.exception.UserNotFoundException;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameStateStoreTest {
    private static final UUID GAME_ID = UUID.randomUUID();

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> values;
    private GameStateStore store;
    private UserRepo users;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        users = mock(UserRepo.class);
        store = new GameStateStore(redisTemplate, JsonMapper.builder().build(), new ActiveGameStateConverter(users));
    }

    @Test
    void storedStateSurvivesJsonRoundTrip() {
        ActiveGameState state = new ActiveGameState(
                List.of(
                        Move.doublePush(Square.fromNotation("e2"), Square.fromNotation("e4")),
                        Move.promotion(Square.fromNotation("b7"), Square.fromNotation("a8"), PieceType.KNIGHT),
                        Move.castleKingside(Color.BLACK)),
                Instant.parse("2026-09-27T10:15:30Z"),
                null,
                TimeControl.BLITZ,
                GameStatus.ONGOING,
                UUID.randomUUID(),
                null,
                Color.WHITE);

        JsonMapper mapper = JsonMapper.builder().build();
        assertThat(mapper.readValue(mapper.writeValueAsString(state), ActiveGameState.class)).isEqualTo(state);
    }

    @Test
    void storeReconstructsGameAndPlayersWithoutAuthentication() {
        User white = new User("white@example.com", "white", "hash", "");
        User black = new User("black@example.com", "black", "hash", "");
        when(users.findById(white.getId())).thenReturn(Optional.of(white));
        when(users.findById(black.getId())).thenReturn(Optional.of(black));
        Game game = new Game(new RegisteredPlayer(white.getId(), white.getUsername()),
                new RegisteredPlayer(black.getId(), black.getUsername()), TimeControl.BLITZ);
        assertThat(game.makeMove("e2", "e4", null)).isTrue();
        game.offerDraw(Color.WHITE);

        store.storeGame(GAME_ID, game);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("game:" + GAME_ID), json.capture(), eq(GameStateStore.GAME_TTL));
        when(values.get("game:" + GAME_ID)).thenReturn(json.getValue());
        Game restored = store.loadGame(GAME_ID);
        assertThat(restored.getGameSnapshot()).isEqualTo(game.getGameSnapshot());
        assertThat(restored.getFen()).isEqualTo(game.getFen());
        assertThat(restored.getWhitePlayer()).isEqualTo(game.getWhitePlayer());
        assertThat(restored.getBlackPlayer()).isEqualTo(game.getBlackPlayer());
        assertThat(restored.makeMove("e7", "e5", null)).isTrue();
    }

    @Test
    void existingRedisJsonStillLoadsWithAnEmptySeat() {
        User white = new User("white@example.com", "white", "hash", "");
        when(users.findById(white.getId())).thenReturn(Optional.of(white));
        // The stored format has no Java class name; keep these keys compatible across package moves.
        when(values.get("game:" + GAME_ID)).thenReturn("""
                {"history":[],"startTime":"2026-09-27T10:15:30Z","endTime":null,
                 "timeControl":"RAPID","status":"ONGOING","whitePlayerId":"%s",
                 "blackPlayerId":null,"drawOfferedBy":null}
                """.formatted(white.getId()));

        Game restored = store.loadGame(GAME_ID);

        assertThat(restored.getWhitePlayer()).isEqualTo(new RegisteredPlayer(white.getId(), "white"));
        assertThat(restored.getBlackPlayer()).isNull();
        assertThat(restored.getTimeControl()).isEqualTo(TimeControl.RAPID);
        verify(users).findById(white.getId());
        verifyNoMoreInteractions(users);
    }

    @Test
    void deletedPlayerIsAnApplicationLookupFailure() {
        UUID missingId = UUID.randomUUID();
        when(values.get("game:" + GAME_ID)).thenReturn("""
                {"history":[],"startTime":"2026-09-27T10:15:30Z","endTime":null,
                 "timeControl":"RAPID","status":"ONGOING","whitePlayerId":"%s",
                 "blackPlayerId":null,"drawOfferedBy":null}
                """.formatted(missingId));

        assertThatThrownBy(() -> store.loadGame(GAME_ID))
                .isInstanceOf(UserNotFoundException.class);
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
            })).isSameAs(notYourTurn);
        }
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), anyString());
    }
}
