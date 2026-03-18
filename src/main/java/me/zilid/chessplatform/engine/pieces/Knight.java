package me.zilid.chessplatform.engine.pieces;


import me.zilid.chessplatform.engine.Position;

import java.util.ArrayList;
import java.util.List;

public class Knight extends Piece {
    // All possible L-shaped knight moves
    private static final int[][] moves = {
            {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
            {1, 2}, {1, -2}, {-1, 2}, {-1, -2}
    };
    
    public Knight(Color color) {
        super(color);
    }
    
    @Override
    public List<Position> getValidMoves(Position position, Piece[][] board) {
        List<Position> validMoves = new ArrayList<>();
        int x = position.x();
        int y = position.y();

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
        List<Position> controlled = new ArrayList<>();
        int x = position.x();
        int y = position.y();

        for (int[] move : moves) {
            int newX = x + move[0];
            int newY = y + move[1];

            if (isValidPosition(newX, newY)) {
                controlled.add(new Position(newX, newY));
            }
        }

        return controlled;
    }
    
    @Override
    public PieceType getType() {
        return PieceType.KNIGHT;
    }
}
