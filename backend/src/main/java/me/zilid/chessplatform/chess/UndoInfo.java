package me.zilid.chessplatform.chess;

import org.jspecify.annotations.Nullable;

public record UndoInfo(
        Square capturedSquare,
        @Nullable Piece capturedPiece,
        CastlingRights castlingRights,
        @Nullable Square enPassantTarget,
        int halfMoveClock,
        int fullMoveClock
) {
}
