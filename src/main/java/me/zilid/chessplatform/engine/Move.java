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
    private final boolean isEnPassant;
    private final boolean isCastling;
    private final boolean isKingsideCastle;
    private final String disambiguation;
    private final String notation;

    public Move(Position from, Position to, Piece.PieceType movedPieceType,
                Piece.PieceType capturedPieceType, boolean isCheck, boolean isCheckmate,
                boolean isEnPassant, boolean isCastling, boolean isKingsideCastle, String disambiguation) {
        this.from = from;
        this.to = to;
        this.movedPieceType = movedPieceType;
        this.capturedPieceType = capturedPieceType;
        this.isCheck = isCheck;
        this.isCheckmate = isCheckmate;
        this.isEnPassant = isEnPassant;
        this.isCastling = isCastling;
        this.isKingsideCastle = isKingsideCastle;
        this.disambiguation = disambiguation;
        this.notation = generateNotation();
    }

    private String generateNotation() {
        StringBuilder sb = new StringBuilder();

        // Special notation for castling
        if (isCastling) {
            sb.append(isKingsideCastle ? "O-O" : "O-O-O");
        } else {
            // Piece symbol (pawn symbol is empty string)
            sb.append(movedPieceType.getSymbol());
            // there is no ambiguation when moving piece is pawn or king
            if (movedPieceType != Piece.PieceType.PAWN && movedPieceType != Piece.PieceType.KING) {
                sb.append(disambiguation);
            }

            // Add 'x' for captures (including en passant)
            if (capturedPieceType != null || isEnPassant) {
                if (isEnPassant || movedPieceType == Piece.PieceType.PAWN) {
                    sb.append(from.toNotation().charAt(0)); // File of pawn
                }
                sb.append('x');
            }

            // Destination square
            sb.append(to.toNotation());
        }

        if ((to.y() == 0 || to.y() == 7) && movedPieceType == Piece.PieceType.PAWN) {
            sb.append("=Q");
        }

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
        return capturedPieceType != null || isEnPassant;
    }
    
    public boolean isEnPassant() {
        return isEnPassant;
    }
    
    public boolean isCastling() {
        return isCastling;
    }
    
    public boolean isKingsideCastle() {
        return isKingsideCastle;
    }
    
    @Override
    public String toString() {
        return notation + " (" + from.toNotation() + " -> " + to.toNotation() + ")";
    }
}
