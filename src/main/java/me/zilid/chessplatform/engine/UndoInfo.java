package me.zilid.chessplatform.engine;

public record UndoInfo (
    Square capturedSquare,
    int rights,
    Square enPassantTarget,
    int halfMoveClock,
    int fullMoveClock
) {
}
