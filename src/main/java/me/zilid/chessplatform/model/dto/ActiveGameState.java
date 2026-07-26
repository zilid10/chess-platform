package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.GameStatus;
import me.zilid.chessplatform.chess.Move;

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
        Color drawOfferedBy
) {
}
