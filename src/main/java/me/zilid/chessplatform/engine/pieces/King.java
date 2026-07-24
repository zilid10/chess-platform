package me.zilid.chessplatform.engine.pieces;


import me.zilid.chessplatform.engine.Color;
import me.zilid.chessplatform.engine.PieceType;
import me.zilid.chessplatform.engine.Square;

import java.util.ArrayList;
import java.util.List;

public class King extends Piece {
    // All 8 adjacent squares
    private static final int[][] moves = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    
    public King(Color color) {
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
        return PieceType.KING;
    }
}
