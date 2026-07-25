package me.zilid.chessplatform.engine.formatter;

import me.zilid.chessplatform.engine.PieceType;
import me.zilid.chessplatform.engine.Square;

import java.util.Objects;

public record UciMove(Square from, Square to, PieceType promotion) {
    public UciMove {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
