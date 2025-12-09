package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.pieces.Piece;

/*
 * Represents a single chess move
 */
public class Move {
    private final Position from;
    private final Position to;
    private final Piece.PieceType movedPieceType;
    private final Piece.PieceType capturedPieceType; // null if no capture
    private final boolean isCheck;
    private final boolean isCheckmate;
    private final String notation;
    
    public Move(Position from, Position to, Piece.PieceType movedPieceType,
                Piece.PieceType capturedPieceType, boolean isCheck, boolean isCheckmate) {
        this.from = from;
        this.to = to;
        this.movedPieceType = movedPieceType;
        this.capturedPieceType = capturedPieceType;
        this.isCheck = isCheck;
        this.isCheckmate = isCheckmate;
        this.notation = generateNotation();
    }
    
    private String generateNotation() {
        StringBuilder sb = new StringBuilder();
        
        // Piece symbol (pawn symbol is empty string)
        sb.append(movedPieceType.getSymbol());

        // Add 'x' for captures
        if (capturedPieceType != null) {
            if (movedPieceType == Piece.PieceType.PAWN) {
                sb.append(from.toNotation().charAt(0)); // File of pawn
            }
            sb.append('x');
        }
        
        // Destination square
        sb.append(to.toNotation());
        
        // Check/Checkmate indicators
        if (isCheckmate) {
            sb.append('#');
        } else if (isCheck) {
            sb.append('+');
        }
        
        return sb.toString();
    }
    
    public Position getFrom() {
        return from;
    }
    
    public Position getTo() {
        return to;
    }
    
    public Piece.PieceType getMovedPieceType() {
        return movedPieceType;
    }
    
    public Piece.PieceType getCapturedPieceType() {
        return capturedPieceType;
    }
    
    public boolean isCheck() {
        return isCheck;
    }
    
    public boolean isCheckmate() {
        return isCheckmate;
    }
    
    public String getNotation() {
        return notation;
    }
    
    public boolean isCapture() {
        return capturedPieceType != null;
    }
    
    @Override
    public String toString() {
        return notation + " (" + from.toNotation() + " -> " + to.toNotation() + ")";
    }
}
