package me.zilid.chessplatform.engine.pieces;


import me.zilid.chessplatform.engine.Color;
import me.zilid.chessplatform.engine.PieceType;
import me.zilid.chessplatform.engine.Square;

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
    public List<Square> getValidMoves(Square square, Piece[][] board) {
        List<Square> validMoves = new ArrayList<>();
        int x = square.x();
        int y = square.y();

        for (int[] move : moves) {
            int newX = x + move[0];
            int newY = y + move[1];
            
            if (Square.isValid(newX, newY)) {
                Piece target = board[newX][newY];
                if (target == null || isEnemyPiece(target)) {
                    validMoves.add(new Square(newX, newY));
                }
            }
        }
        
        return validMoves;
    }

    @Override
    public List<Square> getControlledSquares(Square square, Piece[][] board) {
        List<Square> controlled = new ArrayList<>();
        int x = square.x();
        int y = square.y();

        for (int[] move : moves) {
            int newX = x + move[0];
            int newY = y + move[1];

            if (Square.isValid(newX, newY)) {
                controlled.add(new Square(newX, newY));
            }
        }

        return controlled;
    }
    
    @Override
    public PieceType getType() {
        return PieceType.KNIGHT;
    }
}
