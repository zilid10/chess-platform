package me.zilid.chessplatform.engine.pieces;


import me.zilid.chessplatform.engine.Color;
import me.zilid.chessplatform.engine.PieceType;
import me.zilid.chessplatform.engine.Square;

import java.util.ArrayList;
import java.util.List;

public class Rook extends Piece {
    // Horizontal and vertical directions
    private static final int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public Rook(Color color) {
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

            while (Square.isValid(newX, newY)) {
                Piece target = board[newX][newY];

                if (target == null) {
                    validMoves.add(Square.of(newX, newY));
                } else {
                    if (isEnemyPiece(target)) {
                        validMoves.add(Square.of(newX, newY));
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

            while (Square.isValid(newX, newY)) {
                Piece target = board[newX][newY];
                controlled.add(Square.of(newX, newY));
                if (target != null) {
                    break;
                }

                newX += dir[0];
                newY += dir[1];
            }
        }
        return controlled;
    }

    @Override
    public PieceType getType() {
        return PieceType.ROOK;
    }
}
