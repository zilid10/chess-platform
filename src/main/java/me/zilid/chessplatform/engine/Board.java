package me.zilid.chessplatform.engine;

dimport me.zilid.chessplatform.engine.pieces.*;

import java.util.ArrayList;
import java.util.List;

public class Board {
    private final Piece[][] board;
    private boolean whiteTurn;
    
    public Board() {
        board = new Piece[8][8];
        whiteTurn = true;
        initializeBoard();
    }
    
    private void initializeBoard() {
        // Initialize pawns
        for (int i = 0; i < 8; i++) {
            board[i][1] = new Pawn(true);
            board[i][6] = new Pawn(false);
        }
        
        // Initialize rooks
        board[0][0] = new Rook(true);
        board[7][0] = new Rook(true);
        board[0][7] = new Rook(false);
        board[7][7] = new Rook(false);
        
        // Initialize knights
        board[1][0] = new Knight(true);
        board[6][0] = new Knight(true);
        board[1][7] = new Knight(false);
        board[6][7] = new Knight(false);
        
        // Initialize bishops
        board[2][0] = new Bishop(true);
        board[5][0] = new Bishop(true);
        board[2][7] = new Bishop(false);
        board[5][7] = new Bishop(false);
        
        // Initialize queens
        board[3][0] = new Queen(true);
        board[3][7] = new Queen(false);
        
        // Initialize kings
        board[4][0] = new King(true);
        board[4][7] = new King(false);
    }
    
    public Piece getPiece(Position position) {
        return board[position.x()][position.y()];
    }
    
    public boolean isWhiteTurn() {
        return whiteTurn;
    }

    public boolean makeMove(Position from, Position to) {
        Piece piece = getPiece(from);

        if (piece == null) {
            return false;
        }

        if (piece.isWhite() != whiteTurn) {
            return false;
        }

        List<Position> validMoves = getValidMovesForPiece(from);
        if (!validMoves.contains(to)) {
            return false;
        }

        // Make the move
        Piece capturedPiece = getPiece(to);
        board[to.x()][to.y()] = piece;
        board[from.x()][from.y()] = null;
        piece.setMoved();
        whiteTurn = !whiteTurn;
        return true;
    }

    public List<Position> getValidMovesForPiece(Position piecePosition) {
        Piece piece = getPiece(piecePosition);
        if (piece == null) {
            return new ArrayList<>();
        }

        List<Position> pseudoLegalMoves = piece.getValidMoves(piecePosition, board);
        List<Position> legalMoves = new ArrayList<>();

        for (Position move : pseudoLegalMoves) {
            // Simulate the move
            Piece capturedPiece = board[move.x()][move.y()];
            board[move.x()][move.y()] = piece;
            board[piecePosition.x()][piecePosition.y()] = null;

            // Check if this move leaves the king in check
            if (!isInCheck(piece.isWhite())) {
                legalMoves.add(move);
            }

            // Undo the move
            board[piecePosition.x()][piecePosition.y()] = piece;
            board[move.x()][move.y()] = capturedPiece;
        }

        return legalMoves;
    }

    public boolean isInCheck(boolean isWhite) {
        // Find the king
        Position kingPosition = null;
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.getType() == Piece.PieceType.KING && piece.isWhite() == isWhite) {
                    kingPosition = new Position(x, y);
                    break;
                }
            }
            if (kingPosition != null) break;
        }
        
        if (kingPosition == null) {
            return false; // No king found
        }
        
        // Check if any enemy piece can attack the king
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.isWhite() != isWhite) {
                    Position enemyPosition = new Position(x, y);
                    List<Position> moves = piece.getControlledSquares(enemyPosition, board);
                    if (moves.contains(kingPosition)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    public boolean isCheckmate(boolean isWhite) {
        // Not in check, so not checkmate
        if (!isInCheck(isWhite)) {
            return false;
        }
        
        // Check if there are any legal moves
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.isWhite() == isWhite) {
                    Position piecePosition = new Position(x, y);
                    List<Position> legalMoves = getValidMovesForPiece(piecePosition);
                    if (!legalMoves.isEmpty()) {
                        return false; // Found a legal move
                    }
                }
            }
        }
        
        return true; // No legal moves and in check = checkmate
    }
    
    public boolean isStalemate(boolean isWhite) {
        if (isInCheck(isWhite)) {
            return false; // In check, so not stalemate
        }
        
        // Check if there are any legal moves
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.isWhite() == isWhite) {
                    Position piecePosition = new Position(x, y);
                    List<Position> legalMoves = getValidMovesForPiece(piecePosition);
                    if (!legalMoves.isEmpty()) {
                        return false; // Found a legal move
                    }
                }
            }
        }
        
        return true; // No legal moves and not in check = stalemate
    }
}
