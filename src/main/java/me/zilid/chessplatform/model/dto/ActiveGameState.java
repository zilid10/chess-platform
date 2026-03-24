package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.engine.GameStatus;
import me.zilid.chessplatform.engine.Move;
import me.zilid.chessplatform.engine.pieces.Piece;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ActiveGameState(
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
