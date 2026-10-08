package me.zilid.chessplatform.repository.game;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.Move;
import me.zilid.chessplatform.chess.game.ClockSetting;
import me.zilid.chessplatform.chess.game.GameStatus;
import org.jspecify.annotations.Nullable;

/** Redis representation of a game in progress. */
record ActiveGameState(
        // move history
        List<Move> moves,

        // clock settings
        ClockSetting clockSetting,
        Duration whiteRemaining,
        Duration blackRemaining,
        @Nullable Instant turnStartAt,

        // game metadata
        Color turnColor,
        Instant startTime,
        @Nullable Instant endTime,
        GameStatus status,
        @Nullable StoredPlayer whitePlayer,
        @Nullable StoredPlayer blackPlayer,
        @Nullable Color drawOfferedBy,
        @Nullable Instant firstMoveDeadline) {
    // JSON in an older format lacks some of these; fail while parsing instead of when the game is rebuilt
    ActiveGameState {
        Objects.requireNonNull(moves, "moves");
        Objects.requireNonNull(clockSetting, "clockSetting");
        Objects.requireNonNull(whiteRemaining, "whiteRemaining");
        Objects.requireNonNull(blackRemaining, "blackRemaining");
        Objects.requireNonNull(turnColor, "turnColor");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(status, "status");
    }
}
