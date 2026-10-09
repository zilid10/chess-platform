package me.zilid.chessplatform.repository.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import me.zilid.chessplatform.chess.game.ClockSetting;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.RegisteredPlayer;
import me.zilid.chessplatform.util.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Runs against the Redis provisioned by the backend CI job. */
@IntegrationTest
class GameStateStoreIT {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private GameStateStore store;

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
        Game game = new Game(null, null, ClockSetting.ofMinutes(10, 0));

        store.storeGame(gameId, game);

        Game loaded = store.loadGame(gameId);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getFen()).isEqualTo(game.getFen());
        assertThat(loaded.getClockSetting()).isEqualTo(game.getClockSetting());
        assertThat(loaded.getStartTime()).isEqualTo(game.getStartTime());
        assertThat(redisTemplate.getExpire("game:" + gameId, TimeUnit.SECONDS))
                .isBetween(GameStateStore.GAME_TTL.toSeconds() - 5, GameStateStore.GAME_TTL.toSeconds());

        store.expireGame(gameId, Duration.ofSeconds(30));

        assertThat(redisTemplate.getExpire("game:" + gameId, TimeUnit.SECONDS)).isBetween(25L, 30L);
        redisTemplate.delete("game:" + gameId);
    }

    @Test
    void timeoutDeadlinesAreIndexedUntilTheGameEnds() {
        UUID gameId = UUID.randomUUID();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Game game = new Game(
                new RegisteredPlayer(UUID.randomUUID(), "white"),
                new RegisteredPlayer(UUID.randomUUID(), "black"),
                ClockSetting.ofMinutes(1, 0));
        game.makeMove("e2", "e4", null, start);
        game.makeMove("e7", "e5", null, start.plusSeconds(1));
        Instant deadline = game.timeoutDeadline();
        assertThat(deadline).isNotNull();

        try {
            store.storeGame(gameId, game);

            assertThat(store.findTimeoutsDue(deadline.minusMillis(1), 1000)).doesNotContain(gameId);
            assertThat(store.findTimeoutsDue(deadline, 1000)).contains(gameId);

            game.checkTimeout(deadline);
            store.storeGame(gameId, game);

            assertThat(store.findTimeoutsDue(deadline, 1000)).doesNotContain(gameId);
        } finally {
            store.clearTimeoutDeadline(gameId);
            redisTemplate.delete("game:" + gameId);
        }
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
                }))
                .isInstanceOf(IllegalArgumentException.class);

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
