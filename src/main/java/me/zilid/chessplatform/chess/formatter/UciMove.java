package me.zilid.chessplatform.chess.formatter;

import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.Square;

import java.util.Objects;

public record UciMove(Square from, Square to, PieceType promotion) {
    public UciMove {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
