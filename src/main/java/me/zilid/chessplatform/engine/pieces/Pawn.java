package me.zilid.chessplatform.engine.pieces;


import me.zilid.chessplatform.engine.Position;

import java.util.ArrayList;
import java.util.List;

public class Pawn extends Piece {
    
    public Pawn(boolean isWhite) {
        super(isWhite);
    }
    
    @Override
    public List<Position> getValidMoves(Position position, Piece[][] board) {
        List<Position> validMoves = new ArrayList<>();
        int direction = isWhite ? 1 : -1;
        int x = position.x();
        int y = position.y();
        
        // Forward moves
        int newY = y + direction;
        if (isValidPosition(x, newY) && board[x][newY] == null) {
            validMoves.add(new Position(x, newY));
            
            // Double move from starting position, only possible when there is no blockade in front of the pawn
            if (!hasMoved) {
                int doubleY = y + (2 * direction);
                if (isValidPosition(x, doubleY) && board[x][doubleY] == null) {
                    validMoves.add(new Position(x, doubleY));
                }
            }
        }
        
        // Diagonal captures
        for (int dx : new int[]{-1, 1}) {
            int captureX = x + dx;
            int captureY = y + direction;
            if (isValidPosition(captureX, captureY)) {
                Piece target = board[captureX][captureY];
                if (isEnemyPiece(target)) {
                    validMoves.add(new Position(captureX, captureY));
                }
            }
        }
        
        return validMoves;
    }

    @Override
    public List<Position> getControlledSquares(Position position, Piece[][] board) {
        return getValidMoves(position, board);
    }

    @Override
    public PieceType getType() {
        return PieceType.PAWN;
    }
}
