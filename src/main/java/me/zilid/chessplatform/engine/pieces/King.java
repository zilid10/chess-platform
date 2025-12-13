package me.zilid.chessplatform.engine.pieces;


import me.zilid.chessplatform.engine.Position;

import java.util.ArrayList;
import java.util.List;

public class King extends Piece {
    
    public King(Color color) {
        super(color);
    }
    
    @Override
    public List<Position> getValidMoves(Position position, Piece[][] board) {
        List<Position> validMoves = new ArrayList<>();
        int x = position.x();
        int y = position.y();
        
        // All 8 adjacent squares
        int[][] moves = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        
        for (int[] move : moves) {
            int newX = x + move[0];
            int newY = y + move[1];
            
            if (isValidPosition(newX, newY)) {
                Piece target = board[newX][newY];
                if (target == null || isEnemyPiece(target)) {
                    validMoves.add(new Position(newX, newY));
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
        return PieceType.KING;
    }
}
