package me.zilid.chessplatform.engine.pieces;


import me.zilid.chessplatform.engine.Position;

import java.util.List;

public abstract class Piece {
    protected Color color;
    protected boolean hasMoved;
    
    public Piece(Color color) {
        this.color = color;
        this.hasMoved = false;
    }
    
    public abstract List<Position> getValidMoves(Position position, Piece[][] board);

    public abstract List<Position> getControlledSquares(Position position, Piece[][] board);

    public abstract PieceType getType();

    // used for hashing the board and getting the fen representation
    public String getSymbol() {
        String type = getType().getSymbol();
        type = type.isEmpty() ? "P": type;
        return isWhite() ? type : type.toLowerCase();
    }

    public Color getColor() {
        return color;
    }
    
    public boolean isWhite() {
        return color == Color.WHITE;
    }
    
    public void setMoved() {
        this.hasMoved = true;
    }
    
    public boolean hasMoved() {
        return hasMoved;
    }
    
    protected boolean isValidPosition(int x, int y) {
        return x >= 0 && x < 8 && y >= 0 && y < 8;
    }
    
    protected boolean isEnemyPiece(Piece piece) {
        return piece != null && piece.color != this.color;
    }
    
    protected boolean isFriendlyPiece(Piece piece) {
        return piece != null && piece.color == this.color;
    }
    
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

    public enum Color {
        WHITE, BLACK;

        public Color opposite() {
            return this == WHITE ? BLACK : WHITE;
        }

        public boolean isWhite() {
            return this == WHITE;
        }

        public boolean isBlack() {
            return this == BLACK;
        }

        public String getSymbol() {
            return switch (this) {
                case WHITE -> "w";
                case BLACK -> "b";
            };
        }
    }
}
