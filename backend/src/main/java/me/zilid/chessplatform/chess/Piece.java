package me.zilid.chessplatform.chess;

public enum Piece {
    WHITE_PAWN(Color.WHITE, PieceType.PAWN),
    WHITE_KNIGHT(Color.WHITE, PieceType.KNIGHT),
    WHITE_BISHOP(Color.WHITE, PieceType.BISHOP),
    WHITE_ROOK(Color.WHITE, PieceType.ROOK),
    WHITE_QUEEN(Color.WHITE, PieceType.QUEEN),
    WHITE_KING(Color.WHITE, PieceType.KING),
    BLACK_PAWN(Color.BLACK, PieceType.PAWN),
    BLACK_KNIGHT(Color.BLACK, PieceType.KNIGHT),
    BLACK_BISHOP(Color.BLACK, PieceType.BISHOP),
    BLACK_ROOK(Color.BLACK, PieceType.ROOK),
    BLACK_QUEEN(Color.BLACK, PieceType.QUEEN),
    BLACK_KING(Color.BLACK, PieceType.KING);

    private final Color color;
    private final PieceType type;

    Piece(Color color, PieceType type) {
        this.color = color;
        this.type = type;
    }

    public static Piece of(Color color, PieceType type) {
        return switch (color) {
            case WHITE ->
                switch (type) {
                    case PAWN -> WHITE_PAWN;
                    case KNIGHT -> WHITE_KNIGHT;
                    case BISHOP -> WHITE_BISHOP;
                    case ROOK -> WHITE_ROOK;
                    case QUEEN -> WHITE_QUEEN;
                    case KING -> WHITE_KING;
                };
            case BLACK ->
                switch (type) {
                    case PAWN -> BLACK_PAWN;
                    case KNIGHT -> BLACK_KNIGHT;
                    case BISHOP -> BLACK_BISHOP;
                    case ROOK -> BLACK_ROOK;
                    case QUEEN -> BLACK_QUEEN;
                    case KING -> BLACK_KING;
                };
        };
    }

    public static boolean isEnemyPiece(Piece piece, Piece target) {
        return piece.color != target.color;
    }

    public static boolean isFriendlyPiece(Piece piece, Piece target) {
        return piece.color == target.color;
    }

    public PieceType type() {
        return type;
    }

    public Color color() {
        return color;
    }
}
