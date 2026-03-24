package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.pieces.Piece;

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
        Piece.Color drawOfferedBy
) {
}
