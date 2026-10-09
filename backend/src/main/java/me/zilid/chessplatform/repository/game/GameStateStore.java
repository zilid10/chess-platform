package me.zilid.chessplatform.repository.game;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import me.zilid.chessplatform.chess.game.Game;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Keeps games in progress in Redis so they survive restarts and can be shared by several backend instances.
 *
 * <p>Deliberately a {@code @Component}, not a {@code @Repository}: exception translation would turn the game-rule
 * exceptions thrown inside {@link #withLock} into {@code InvalidDataAccessApiUsageException}. Redis errors are already
 * translated by {@link StringRedisTemplate}.
 */
@Component
public class GameStateStore {

    static final Duration GAME_TTL = Duration.ofHours(1);
    static final Duration LOCK_TTL = Duration.ofSeconds(10);
    // Sorted set of game ids scored by the epoch millis at which each game's time limit passes
    static final String TIMEOUT_DEADLINES_KEY = "game-timeouts";
    private static final Duration LOCK_WAIT = Duration.ofSeconds(3);
    private static final Duration LOCK_RETRY_INTERVAL = Duration.ofMillis(10);

    // Only the holder of the lock may release it, even if its TTL expired and someone else took it.
    private static final RedisScript<Long> RELEASE_LOCK = RedisScript.of("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ActiveGameStateConverter converter;

    GameStateStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, ActiveGameStateConverter converter) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.converter = converter;
    }

    /** Save the game, reset its expiry to {@link #GAME_TTL}, and record or clear its timeout deadline. */
    public void storeGame(UUID gameId, Game game) {
        String json;
        try {
            json = objectMapper.writeValueAsString(converter.toState(game));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("failed to serialize game state for gameId=" + gameId, e);
        }
        redisTemplate.opsForValue().set(key(gameId), json, GAME_TTL);
        Instant deadline = game.timeoutDeadline();
        if (deadline == null) {
            clearTimeoutDeadline(gameId);
        } else {
            redisTemplate.opsForZSet().add(TIMEOUT_DEADLINES_KEY, gameId.toString(), (double) deadline.toEpochMilli());
        }
    }

    /** Ids of games whose timeout deadline is at or before {@code now}, earliest first. */
    public List<UUID> findTimeoutsDue(Instant now, int limit) {
        Set<String> ids = redisTemplate
                .opsForZSet()
                .rangeByScore(TIMEOUT_DEADLINES_KEY, Double.NEGATIVE_INFINITY, (double) now.toEpochMilli(), 0, limit);
        if (ids == null) {
            return List.of();
        }
        return ids.stream().map(UUID::fromString).toList();
    }

    /** Stop tracking the game's timeout, for example after the game itself has disappeared. */
    public void clearTimeoutDeadline(UUID gameId) {
        redisTemplate.opsForZSet().remove(TIMEOUT_DEADLINES_KEY, gameId.toString());
    }

    public @Nullable Game loadGame(UUID gameId) {
        String json = redisTemplate.opsForValue().get(key(gameId));
        if (json == null) {
            return null;
        }

        try {
            return converter.toGame(objectMapper.readValue(json, ActiveGameState.class));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("failed to parse game state for gameId=" + gameId, e);
        }
    }

    public void expireGame(UUID gameId, Duration timeout) {
        redisTemplate.expire(key(gameId), timeout);
    }

    /**
     * Run {@code action} while holding a distributed lock on the game, so a load-modify-store cycle cannot interleave
     * with another one on any backend instance.
     *
     * @throws IllegalStateException if the lock could not be acquired in time
     */
    public <T> T withLock(UUID gameId, Supplier<T> action) {
        String lockKey = lockKey(gameId);
        String token = UUID.randomUUID().toString();
        acquireLock(lockKey, token);
        try {
            return action.get();
        } finally {
            redisTemplate.execute(RELEASE_LOCK, List.of(lockKey), token);
        }
    }

    private void acquireLock(String lockKey, String token) {
        long deadline = System.nanoTime() + LOCK_WAIT.toNanos();
        while (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL))) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Game is busy, please try again");
            }
            try {
                Thread.sleep(LOCK_RETRY_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for game lock", e);
            }
        }
    }

    private static String key(UUID gameId) {
        return "game:" + gameId;
    }

    private static String lockKey(UUID gameId) {
        return "game:" + gameId + ":lock";
    }
}
