package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.chess.game.TimeControl;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record MatchRecordResponse(
        UUID id,
        UserResponse whitePlayer,
        UserResponse blackPlayer,
        String result,
        String reason,
        Instant startTime,
        Instant endTime,
        // null for matches archived before ratings were tracked
        @Nullable TimeControl timeControl,
        @Nullable Integer whiteRating,
        @Nullable Integer blackRating,
        @Nullable Integer whiteRatingChange,
        @Nullable Integer blackRatingChange
) {
}
