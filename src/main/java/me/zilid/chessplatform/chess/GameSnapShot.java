package me.zilid.chessplatform.chess;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GameSnapShot(
        // board info
        String fen,

        // move history
        List<Move> history,
        Map<Integer, Integer> positionHistory,

        // game metadata
        Instant startTime,
        Instant endTime,
        GameStatus status,
        UUID whitePlayerId,
        UUID blackPlayerId,
        Color drawOfferedBy
) {
}
