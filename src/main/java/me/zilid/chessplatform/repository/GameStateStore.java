package me.zilid.chessplatform.repository;

import me.zilid.chessplatform.model.dto.ActiveGameState;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

@Repository
public class GameStateStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public GameStateStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void storeGame(String gameId, ActiveGameState game) {
        try {
            String json = objectMapper.writeValueAsString(game);
            redisTemplate.opsForValue().set(key(gameId), json, Expiration.from(1, TimeUnit.HOURS));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("failed to serialize game state for gameId=" + gameId, e);
        }
    }

    public @Nullable ActiveGameState loadGame(String gameId) {
        String json = redisTemplate.opsForValue().get(key(gameId));
        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readValue(json, ActiveGameState.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("failed to parse game state for gameId=" + gameId, e);
        }
    }

    private String key(String gameId) {
        return "game:" + gameId;
    }
}
