package me.zilid.chessplatform.engine;

public enum PieceType {
    PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING;

    public String getSymbol() {
        return switch (this) {
            case PAWN -> "";
            case KNIGHT -> "N";
            case BISHOP -> "B";
            case ROOK -> "R";
            case QUEEN -> "Q";
            case KING -> "K";
        };
    }
}
