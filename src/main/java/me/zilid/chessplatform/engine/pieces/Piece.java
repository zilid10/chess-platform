package me.zilid.chessplatform.engine.pieces;


import me.zilid.chessplatform.engine.Square;

import java.util.List;

public abstract class Piece {
    protected Color color;

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

    public Color getColor() {
        return color;
    }
    
    public boolean isWhite() {
        return color == Color.WHITE;
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

        public static Color fromSymbol(String color) {
            if (color.equals("w")) {
                return WHITE;
            }
            if (color.equals("b")) {
                return BLACK;
            }
            throw new IllegalArgumentException("invalid color: " + color);
        }
    }
}
