package me.zilid.chessplatform.model.dto;

import java.time.Instant;
import java.util.UUID;

public record MatchRecordResponse(
        UUID id,
        UserResponse whitePlayer,
        UserResponse blackPlayer,
        String result,
        String reason,
        Instant startTime,
        Instant endTime
) {
}
