package me.zilid.chessplatform.chess;

import me.zilid.chessplatform.chess.format.Fen;

import java.util.List;

public class ChessEngine {
    private final Position position;

    public ChessEngine() {
        position = Position.startingPosition();
    }

    public ChessEngine(Position position) {
        this.position = position;
    }

    public boolean makeMove(Square from, Square to, PieceType promotionType) {
        try {
            Move move = MoveGenerator.findLegalMove(position, from, to, promotionType)
                    .orElseThrow(() -> new IllegalArgumentException("No such moves"));
            position.applyMove(move);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Get all valid moves for a piece at the given position
     */
    public List<Square> getValidMoves(String notation) {
        try {
            Square square = Square.fromNotation(notation);
            return MoveGenerator.legalDestinations(position, square);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
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
        return position.isInCheck();
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
        return position.getBoard().isInsufficientMaterial();
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
        return Fen.format(position);
    }
}
