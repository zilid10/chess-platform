package me.zilid.chessplatform.engine;

import java.util.List;

public class ChessEngine {
    private Board board;
    
    public ChessEngine() {
        this.board = new Board();
    }
    
    /**
     * Make a move using chess notation (e.g., "e2" to "e4")
     */
    public boolean makeMove(String from, String to) {
        try {
            Position fromPos = Position.fromNotation(from);
            Position toPos = Position.fromNotation(to);
            return board.makeMove(fromPos, toPos);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Get all valid moves for a piece at the given position
     */
    public List<Position> getValidMoves(String position) {
        try {
            Position pos = Position.fromNotation(position);
            return board.getValidMovesForPiece(pos);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
    
    /**
     * Get all valid moves for a piece at the given position (using Position object)
     */
    public List<Position> getValidMoves(Position position) {
        return board.getValidMovesForPiece(position);
    }
    
    /**
     * Check if the current player is in check
     */
    public boolean isInCheck() {
        return board.isInCheck(board.isWhiteTurn());
    }
    
    /**
     * Check if the current player is in checkmate
     */
    public boolean isCheckmate() {
        return board.isCheckmate(board.isWhiteTurn());
    }
    
    /**
     * Check if the current player is in stalemate
     */
    public boolean isStalemate() {
        return board.isStalemate(board.isWhiteTurn());
    }
    
    /**
     * Check if the game is over (checkmate or stalemate)
     */
    public boolean isGameOver() {
        return isCheckmate() || isStalemate() || isDraw();
    }
    
    /**
     * Check if the game is a draw (any draw condition)
     */
    public boolean isDraw() {
        return isStalemate() || isThreefoldRepetition() || isFiftyMoveRule() || isInsufficientMaterial();
    }
    
    /**
     * Check for threefold repetition
     */
    public boolean isThreefoldRepetition() {
        return board.isThreefoldRepetition();
    }
    
    /**
     * Check for fifty-move rule
     */
    public boolean isFiftyMoveRule() {
        return board.isFiftyMoveRule();
    }
    
    /**
     * Check for insufficient material
     */
    public boolean isInsufficientMaterial() {
        return board.isInsufficientMaterial();
    }

    /**
     * Get the current board state
     */
    public Board getBoard() {
        return board;
    }
    
    /**
     * Get whose turn it is
     */
    public boolean isWhiteTurn() {
        return board.isWhiteTurn();
    }

    /**
     * Reset the board to starting position
     */
    public void reset() {
        this.board = new Board();
    }
}
