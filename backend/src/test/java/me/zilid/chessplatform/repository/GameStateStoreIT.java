package me.zilid.chessplatform.repository;

import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.clock.TimeControl;
import me.zilid.chessplatform.model.dto.ActiveGameState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the Redis provisioned by the backend CI job.
 */
@EnabledIfEnvironmentVariable(named = "SPRING_DATA_REDIS_HOST", matches = ".+")
class GameStateStoreIT {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static GameStateStore store;

    @BeforeAll
    static void connect() {
        String host = System.getenv("SPRING_DATA_REDIS_HOST");
        String port = System.getenv().getOrDefault("SPRING_DATA_REDIS_PORT", "6379");
        connectionFactory = new LettuceConnectionFactory(host, Integer.parseInt(port));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        store = new GameStateStore(redisTemplate, JsonMapper.builder().build());
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test
    void storesLoadsAndExpiresGames() {
        UUID gameId = UUID.randomUUID();
        ActiveGameState state = new ActiveGameState(
                List.of(), Instant.parse("2026-09-27T10:15:30Z"), null, TimeControl.RAPID, GameStatus.ONGOING,
                UUID.randomUUID(), null, null);

        store.storeGame(gameId, state);

        assertThat(store.loadGame(gameId)).isEqualTo(state);
        assertThat(redisTemplate.getExpire("game:" + gameId, TimeUnit.SECONDS))
                .isBetween(GameStateStore.GAME_TTL.toSeconds() - 5, GameStateStore.GAME_TTL.toSeconds());

        store.expireGame(gameId, Duration.ofSeconds(30));

        assertThat(redisTemplate.getExpire("game:" + gameId, TimeUnit.SECONDS)).isBetween(25L, 30L);
        redisTemplate.delete("game:" + gameId);
    }

    @Test
    void lockSerializesActionsOnTheSameGameAndIsReleasedAfterwards() throws Exception {
        UUID gameId = UUID.randomUUID();
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> store.withLock(gameId, () -> {
                firstHoldsLock.countDown();
                await(releaseFirst);
                return "first";
            }));
            await(firstHoldsLock);
            Future<String> second = executor.submit(() -> store.withLock(gameId, () -> "second"));

            Thread.sleep(200);
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("second");
        }
        assertThat(redisTemplate.hasKey("game:" + gameId + ":lock")).isFalse();
    }

    @Test
    void lockIsReleasedWhenTheActionFails() {
        UUID gameId = UUID.randomUUID();

        assertThatThrownBy(() -> store.withLock(gameId, () -> {
            throw new IllegalArgumentException("boom");
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(store.withLock(gameId, () -> "acquired again")).isEqualTo("acquired again");
    }

    @Test
    void lockHeldElsewhereTimesOut() {
        UUID gameId = UUID.randomUUID();
        redisTemplate.opsForValue().set("game:" + gameId + ":lock", "another-instance", Duration.ofSeconds(10));

        try {
            assertThatThrownBy(() -> store.withLock(gameId, () -> "never"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Game is busy, please try again");
        } finally {
            redisTemplate.delete("game:" + gameId + ":lock");
        }
    }
}
