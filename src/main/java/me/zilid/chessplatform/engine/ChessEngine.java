package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.formatter.Fen;

import java.util.List;

public class ChessEngine {
    private final Position position;

    public ChessEngine() {
        position = Position.startingPosition();
    }

    public ChessEngine(Position position) {
        this.position = position;
    }

    /**
     * Make a move using chess notation (e.g., "e2" to "e4")
     */
    public boolean makeMove(String from, String to, PieceType promotionType) {
        try {
            Square fromSquare = Square.fromNotation(from);
            Square toSquare = Square.fromNotation(to);
            return makeMove(fromSquare, toSquare, promotionType);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Make a move from a given position
     */
    public boolean makeMove(Square from, Square to, PieceType promotionType) {
        return position.makeMove(from, to, promotionType);
    }

    /**
     * Get all valid moves for a piece at the given position
     */
    public List<Square> getValidMoves(String position) {
        try {
            Square pos = Square.fromNotation(position);
            return getValidMoves(pos);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    /**
     * Get all valid moves for a piece at the given position
     */
    public List<Square> getValidMoves(Square square) {
        return MoveGenerator.legalDestinations(position, square);
    }

    public UndoInfo applyMove(Move move) {
        return position.applyMove(move);
    }

    public void undoMove(Move move, UndoInfo undoInfo) {
        position.undoMove(move, undoInfo);
    }

    /**
     * Check if the current player is in check
     */
    public boolean isInCheck() {
        return position.isInCheck(position.getTurnColor());
    }

    /**
     * Check if the current player is in checkmate
     */
    public boolean isCheckmate() {
        return position.isCheckmate(position.getTurnColor());
    }

    /**
     * Check if the current player is in stalemate
     */
    public boolean isStalemate() {
        return position.isStalemate(position.getTurnColor());
    }

    /**
     * Check for fifty-move rule
     */
    public boolean isFiftyMoveRule() {
        return position.isFiftyMoveRule();
    }

    /**
     * Check for insufficient material
     */
    public boolean isInsufficientMaterial() {
        return position.isInsufficientMaterial();
    }

    /**
     * Get the current position
     */
    public Position getPosition() {
        return position;
    }

    /**
     * Get the current turn color
     */
    public Color getTurnColor() {
        return position.getTurnColor();
    }

    /**
     * Get fen representation of current board
     */
    public String getFen() {
        return Fen.write(position);
    }
}
