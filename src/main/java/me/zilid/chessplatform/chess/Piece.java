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

    private static final Piece[][] LOOKUP = new Piece[Color.values().length][PieceType.values().length];

    static {
        for (Piece piece : Piece.values()) {
            LOOKUP[piece.color.ordinal()][piece.type.ordinal()] = piece;
        }
    }

    private final Color color;
    private final PieceType type;

    Piece(Color color, PieceType type) {
        this.color = color;
        this.type = type;
    }

    public static Piece of(Color color, PieceType type) {
        return LOOKUP[color.ordinal()][type.ordinal()];
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
