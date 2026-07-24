package me.zilid.chessplatform.engine.pieces;


import me.zilid.chessplatform.engine.Square;

import java.util.ArrayList;
import java.util.List;

public class Bishop extends Piece {

    private static final int[][] directions = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    
    public Bishop(Color color) {
        super(color);
    }
    
    @Override
    public List<Square> getValidMoves(Square square, Piece[][] board) {
        List<Square> validMoves = new ArrayList<>();
        int x = square.x();
        int y = square.y();

        for (int[] dir : directions) {
            int newX = x + dir[0];
            int newY = y + dir[1];

            while (isValidPosition(newX, newY)) {
                Piece target = board[newX][newY];
                
                if (target == null) {
                    validMoves.add(new Square(newX, newY));
                } else {
                    if (isEnemyPiece(target)) {
                        validMoves.add(new Square(newX, newY));
                    }
                    break; // Stop at any piece
                }
                
                newX += dir[0];
                newY += dir[1];
            }
        }
        
        return validMoves;
    }

    @Override
    public List<Square> getControlledSquares(Square square, Piece[][] board) {
        List<Square> controlled = new ArrayList<>();
        int x = square.x();
        int y = square.y();

        for (int[] dir : directions) {
            int newX = x + dir[0];
            int newY = y + dir[1];

            while (isValidPosition(newX, newY)) {
                Piece target = board[newX][newY];
                controlled.add(new Square(newX, newY));

                if (target != null) {
                    break; // Stop at the first piece encountered, but include its square.
                }

                newX += dir[0];
                newY += dir[1];
            }
        }

        return controlled;
    }
    
    @Override
    public PieceType getType() {
        return PieceType.BISHOP;
    }
}
