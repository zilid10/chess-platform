package me.zilid.chessplatform.engine;

public record UndoInfo (
    Position capturedPosition,
    int rights,
    Position enPassantTarget,
    int halfMoveClock,
    int fullMoveClock
) {
}
