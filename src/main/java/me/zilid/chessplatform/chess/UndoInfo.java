package me.zilid.chessplatform.chess;

public record UndoInfo(
        Square capturedSquare,
        Piece capturedPiece,
        CastlingRights castlingRights,
        Square enPassantTarget,
        int halfMoveClock,
        int fullMoveClock
) {
}
