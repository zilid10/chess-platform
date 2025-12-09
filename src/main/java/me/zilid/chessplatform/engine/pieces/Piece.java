package me.zilid.chessplatform.engine.pieces;


import me.zilid.chessplatform.engine.Position;

import java.util.List;

public abstract class Piece {
    protected boolean isWhite;
    protected boolean hasMoved;
    
    public Piece(boolean isWhite) {
        this.isWhite = isWhite;
        this.hasMoved = false;
    }
    
    public abstract List<Position> getValidMoves(Position position, Piece[][] board);

    public abstract List<Position> getControlledSquares(Position position, Piece[][] board);

    public abstract PieceType getType();

    public String getSymbol() {
        return getType().getSymbol();
    }

    public boolean isWhite() {
        return isWhite;
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
        return piece != null && piece.isWhite != this.isWhite;
    }
    
    protected boolean isFriendlyPiece(Piece piece) {
        return piece != null && piece.isWhite == this.isWhite;
    }
    
    public enum PieceType {
        PAWN {
            public String getSymbol() {
                return "";
            }
        },
        KNIGHT {
            public String getSymbol() {
                return "N";
            }
        },
        BISHOP {
            public String getSymbol() {
                return "B";
            }
        },
        ROOK {
            public String getSymbol() {
                return "R";
            }
        },
        QUEEN {
            public String getSymbol() {
                return "Q";
            }
        },
        KING {
            public String getSymbol() {
                return "K";
            }
        };

        public abstract String getSymbol();
    }
}
