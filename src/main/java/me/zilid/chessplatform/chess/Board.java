package me.zilid.chessplatform.chess;

import java.util.*;

public class Board {
    private final static int BOARD_SIZE = 64;
    private static final int[][] KNIGHT = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING = {{0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}};
    private static final int[][] ORTHO = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    private static final int[][] DIAG = {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
    private final Piece[] pieces;
    private final Map<Color, Square> kingLocations;

    // TODO: add validation for Board
    public Board(Piece[] pieces) {
        if (pieces.length != BOARD_SIZE) {
            throw new IllegalArgumentException("The size of the board must be equal to " + BOARD_SIZE);
        }
        kingLocations = new EnumMap<>(Color.class);
        this.pieces = Arrays.copyOf(pieces, pieces.length);
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (this.pieces[i] != null && this.pieces[i].type() == PieceType.KING) {
                kingLocations.put(this.pieces[i].color(), new Square(i));
            }
        }
    }

    public Board() {
        kingLocations = new EnumMap<>(Color.class);
        pieces = new Piece[BOARD_SIZE];
    }

    public static Board initial() {
        Piece[] pieces = new Piece[BOARD_SIZE];
        pieces[0] = Piece.WHITE_ROOK;
        pieces[1] = Piece.WHITE_KNIGHT;
        pieces[2] = Piece.WHITE_BISHOP;
        pieces[3] = Piece.WHITE_QUEEN;
        pieces[4] = Piece.WHITE_KING;
        pieces[5] = Piece.WHITE_BISHOP;
        pieces[6] = Piece.WHITE_KNIGHT;
        pieces[7] = Piece.WHITE_ROOK;
        for (int i = 8; i < 16; i++) {
            pieces[i] = Piece.WHITE_PAWN;
        }
        for (int i = 48; i < 56; i++) {
            pieces[i] = Piece.BLACK_PAWN;
        }
        pieces[56] = Piece.BLACK_ROOK;
        pieces[57] = Piece.BLACK_KNIGHT;
        pieces[58] = Piece.BLACK_BISHOP;
        pieces[59] = Piece.BLACK_QUEEN;
        pieces[60] = Piece.BLACK_KING;
        pieces[61] = Piece.BLACK_BISHOP;
        pieces[62] = Piece.BLACK_KNIGHT;
        pieces[63] = Piece.BLACK_ROOK;
        return new Board(pieces);
    }

    public boolean isInCheck(Color color) {
        Square king = kingLocations.get(color);
        return isSquareAttackedBy(king, color.opposite());
    }

    boolean isSquareAttackedBy(Square square, Color attacker) {
        int file = square.file();
        int rank = square.rank();
        int back = attacker.isWhite() ? -1 : 1;
        if (hasPiece(file + 1, rank + back, attacker, PieceType.PAWN)
                || hasPiece(file - 1, rank + back, attacker, PieceType.PAWN)) {
            return true;
        }
        for (int[] dir : KNIGHT) {
            if (hasPiece(file + dir[0], rank + dir[1], attacker, PieceType.KNIGHT)) {
                return true;
            }
        }
        for (int[] dir : KING) {
            if (hasPiece(file + dir[0], rank + dir[1], attacker, PieceType.KING)) {
                return true;
            }
        }
        for (int[] dir : ORTHO) {
            Piece piece = firstPieceOnRay(square, dir);
            if (piece != null && piece.color() == attacker && (piece.type() == PieceType.QUEEN || piece.type() == PieceType.ROOK)) {
                return true;
            }
        }
        for (int[] dir : DIAG) {
            Piece piece = firstPieceOnRay(square, dir);
            if (piece != null && piece.color() == attacker && (piece.type() == PieceType.QUEEN || piece.type() == PieceType.BISHOP)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPiece(int file, int rank, Color color, PieceType type) {
        if (!Square.isValid(file, rank)) {
            return false;
        }
        Piece piece = pieceAt(Square.of(file, rank));
        return piece != null && piece.color() == color && piece.type() == type;
    }

    public Piece firstPieceOnRay(Square square, int[] dir) {
        int file = square.file() + dir[0];
        int rank = square.rank() + dir[1];
        while (Square.isValid(file, rank)) {
            Piece piece = pieceAt(Square.of(file, rank));
            if (piece != null) {
                return piece;
            }
            file += dir[0];
            rank += dir[1];
        }
        return null;
    }

    public Board copy() {
        return new Board(pieces);
    }

    public Piece pieceAt(Square square) {
        return pieces[square.index()];
    }

    public Piece put(Square square, Piece piece) {
        Piece oldPiece = pieces[square.index()];
        pieces[square.index()] = piece;
        if (piece != null && piece.type() == PieceType.KING) {
            kingLocations.put(piece.color(), square);
        }
        return oldPiece;
    }

    public Piece move(Square from, Square to) {
        Piece piece = put(from, null);
        return put(to, piece);
    }

    public boolean isEmpty(Square square) {
        return pieces[square.index()] == null;
    }

    public Square kingSquare(Color color) {
        return kingLocations.getOrDefault(color, null);
    }

    public int count(Color color) {
        int cnt = 0;
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (pieces[i] != null && pieces[i].color() == color) {
                cnt++;
            }
        }
        return cnt;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Board board)) {
            return false;
        }
        // TODO: if zobrist value is not equal, it can safely return false (like a bloom filter)
        return Arrays.equals(pieces, board.pieces);
    }

    @Override
    public int hashCode() {
        // TODO: change this to hash zobrist value
        return Arrays.hashCode(pieces);
    }

    /**
     * Check if the current position is a draw due to insufficient material
     */
    public boolean isInsufficientMaterial() {
        List<Piece> otherPieces = new ArrayList<>();
        List<Square> bishopSquares = new ArrayList<>();
        int whiteCount = 0, blackCount = 0;
        for (int i = 0; i < BOARD_SIZE; i++) {
            Piece piece = pieceAt(new Square(i));
            if (piece != null) {
                if (piece.color().isWhite()) {
                    whiteCount++;
                } else {
                    blackCount++;
                }
                if (piece.type() == PieceType.BISHOP) {
                    bishopSquares.add(new Square(i));
                }
                if (piece.type() == PieceType.KNIGHT || piece.type() == PieceType.BISHOP) {
                    otherPieces.add(piece);
                }
            }
        }

        // King vs King
        if (whiteCount == 1 && blackCount == 1) {
            return true;
        }

        // King and Bishop vs King or King and Knight vs King
        if (otherPieces.size() == 1 && (otherPieces.getFirst().type() == PieceType.KNIGHT || otherPieces.getFirst().type() == PieceType.BISHOP)) {
            return true;
        }

        // King and Bishop vs King and Bishop (same color bishop)
        if (blackCount == 2 && whiteCount == 2 && bishopSquares.size() == 2) {
            Square b1 = bishopSquares.get(0);
            Square b2 = bishopSquares.get(1);
            return (b1.file() + b1.rank()) % 2 == (b2.file() + b2.rank()) % 2;
        }

        return false;
    }
}
