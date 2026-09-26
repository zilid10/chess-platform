package me.zilid.chessplatform.chess.format;

import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.Square;
import org.jspecify.annotations.Nullable;

public record UciMove(Square from, Square to, @Nullable PieceType promotion) {
}
