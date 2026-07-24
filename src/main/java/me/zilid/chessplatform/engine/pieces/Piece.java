package me.zilid.chessplatform.engine.pieces;


import me.zilid.chessplatform.engine.Color;
import me.zilid.chessplatform.engine.PieceType;
import me.zilid.chessplatform.engine.Square;

import java.util.List;
import java.util.Objects;

public abstract class Piece {
    protected final Color color;

    public Piece(Color color) {
        this.color = color;
    }
    
    public abstract List<Square> getValidMoves(Square square, Piece[][] board);

    public abstract List<Square> getControlledSquares(Square square, Piece[][] board);

    public abstract PieceType getType();

    // used for hashing the board and getting the fen representation
    public String getSymbol() {
        String type = getType().getSymbol();
        type = type.isEmpty() ? "P": type;
        return isWhite() ? type : type.toLowerCase();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Piece piece)) return false;
        return color == piece.color && getType() == piece.getType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(color, getType());
    }

    public Color getColor() {
        return color;
    }
    
    public boolean isWhite() {
        return color == Color.WHITE;
    }
    
    protected boolean isEnemyPiece(Piece piece) {
        return piece != null && piece.color != this.color;
    }
    
    protected boolean isFriendlyPiece(Piece piece) {
        return piece != null && piece.color == this.color;
    }

    public static Piece of(PieceType type, Color color) {
        return switch (type) {
            case QUEEN ->  new Queen(color);
            case KING ->  new King(color);
            case ROOK ->  new Rook(color);
            case BISHOP ->  new Bishop(color);
            case KNIGHT ->   new Knight(color);
            case PAWN ->  new Pawn(color);
        };
    }

}
