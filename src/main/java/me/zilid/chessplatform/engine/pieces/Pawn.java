package me.zilid.chessplatform.engine.pieces;


import me.zilid.chessplatform.engine.Square;

import java.util.ArrayList;
import java.util.List;

public class Pawn extends Piece {

    private static final int BLACK_STARTING_RANK = 6;
    private static final int WHITE_STARTING_RANK = 1;

    public Pawn(Color color) {
        super(color);
    }

    @Override
    public List<Square> getValidMoves(Square square, Piece[][] board) {
        List<Square> validMoves = new ArrayList<>();
        int direction = color.isWhite() ? 1 : -1;
        int x = square.x();
        int y = square.y();

        // Forward moves
        int newY = y + direction;
        if (isValidPosition(x, newY) && board[x][newY] == null) {
            validMoves.add(new Square(x, newY));

            // Double move from starting position, only possible when there is no blockade in front of the pawn
            int doubleY = y + (2 * direction);
            if (isOnStartingRank(square) && isValidPosition(x, doubleY) && board[x][doubleY] == null) {
                validMoves.add(new Square(x, doubleY));
            }
        }

        // Diagonal captures
        for (int dx : new int[]{-1, 1}) {
            int captureX = x + dx;
            int captureY = y + direction;
            if (isValidPosition(captureX, captureY)) {
                Piece target = board[captureX][captureY];
                if (isEnemyPiece(target)) {
                    validMoves.add(new Square(captureX, captureY));
                }
            }
        }

        return validMoves;
    }

    public boolean isOnStartingRank(Square square) {
        return switch (color) {
            case WHITE -> square.y() == WHITE_STARTING_RANK;
            case BLACK -> square.y() == BLACK_STARTING_RANK;
        };
    }

    @Override
    public List<Square> getControlledSquares(Square square, Piece[][] board) {
        // Pawns control diagonal squares regardless of whether they can capture
        List<Square> controlledSquares = new ArrayList<>();
        int dy = color.isWhite() ? 1 : -1;
        int x = square.x();
        int y = square.y();

        // Diagonal squares
        for (int dx : new int[]{-1, 1}) {
            int captureX = x + dx;
            int captureY = y + dy;
            if (isValidPosition(captureX, captureY)) {
                controlledSquares.add(new Square(captureX, captureY));
            }
        }

        return controlledSquares;
    }

    @Override
    public PieceType getType() {
        return PieceType.PAWN;
    }
}
