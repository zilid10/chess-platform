package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.Move;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.TimeControl;

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
        TimeControl timeControl,
        GameStatus status,
        UUID whitePlayerId,
        UUID blackPlayerId,
        Color drawOfferedBy
) {
}
