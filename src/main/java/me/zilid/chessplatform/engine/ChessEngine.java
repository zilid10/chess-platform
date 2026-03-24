package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.pieces.Piece;

import java.util.List;

public class ChessEngine {
    private final Board board;
    
    public ChessEngine() {
        board = new Board();
    }

    public ChessEngine(Board board) {
        this.board = board;
    }

    /**
     * Make a move using chess notation (e.g., "e2" to "e4")
     */
    public boolean makeMove(String from, String to, Piece.PieceType promotionType) {
        try {
            Position fromPos = Position.fromNotation(from);
            Position toPos = Position.fromNotation(to);
            return makeMove(fromPos, toPos, promotionType);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Make a move from a given position
     */
    public boolean makeMove(Position from, Position to, Piece.PieceType promotionType) {
        return board.makeMove(from, to, promotionType);
    }

    /**
     * Get all valid moves for a piece at the given position
     */
    public List<Position> getValidMoves(String position) {
        try {
            Position pos = Position.fromNotation(position);
            return getValidMoves(pos);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
    
    /**
     * Get all valid moves for a piece at the given position
     */
    public List<Position> getValidMoves(Position position) {
        return board.getValidMovesForPiece(position);
    }

    public UndoInfo applyMove(Move move) {
        return board.applyMove(move);
    }

    public void undoMove(Move move, UndoInfo undoInfo) {
        board.undoMove(move, undoInfo);
    }

    /**
     * Check if the current player is in check
     */
    public boolean isInCheck() {
        return board.isInCheck(board.getTurnColor());
    }

    /**
     * Check if the current player is in checkmate
     */
    public boolean isCheckmate() {
        return board.isCheckmate(board.getTurnColor());
    }

    /**
     * Check if the current player is in stalemate
     */
    public boolean isStalemate() {
        return board.isStalemate(board.getTurnColor());
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
     * Get the current turn color
     */
    public Piece.Color getTurnColor() {
        return board.getTurnColor();
    }

    /**
     * Get fen representation of current board
     */
    public String getFen() {
        return board.getFen();
    }
}
