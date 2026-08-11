package me.zilid.chessplatform.rating;

import java.util.Objects;
import java.util.UUID;

public record RatingChange(
        UUID whitePlayerId,
        UUID blackPlayerId,
        int whiteAfter,
        int blackAfter,
        int whiteDelta,
        int blackDelta
) {
    public int newRatingFor(UUID userId) {
        if (Objects.equals(whitePlayerId, userId)) {
            return whiteAfter;
        } else if (Objects.equals(blackPlayerId, userId)) {
            return blackAfter;
        }
        throw new IllegalArgumentException("invalid user id");
    }
}
