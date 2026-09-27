package me.zilid.chessplatform.repository;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.Move;
import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.Square;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.TimeControl;
import me.zilid.chessplatform.model.dto.ActiveGameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameStateStoreTest {
    private static final UUID GAME_ID = UUID.randomUUID();

    private ValueOperations<String, String> values;
    private GameStateStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        store = new GameStateStore(redisTemplate, JsonMapper.builder().build());
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

        store.storeGame(GAME_ID, state);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("game:" + GAME_ID), json.capture(), eq(GameStateStore.GAME_TTL));
        when(values.get("game:" + GAME_ID)).thenReturn(json.getValue());
        assertThat(store.loadGame(GAME_ID)).isEqualTo(state);
    }

    @Test
    void missingGameLoadsAsNull() {
        assertThat(store.loadGame(GAME_ID)).isNull();
    }
}
