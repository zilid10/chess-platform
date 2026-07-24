package me.zilid.chessplatform.engine;

import java.util.Arrays;

public class Board {
    private final static int BOARD_SIZE = 64;
    private final Piece[] pieces;

    public Board(Piece[] pieces) {
        if (pieces.length != BOARD_SIZE) {
            throw new IllegalArgumentException("The size of the board must be equal to 64");
        }
        this.pieces = Arrays.copyOf(pieces, pieces.length);
    }

    public Board() {
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

    public Board copy() {
        return new Board(pieces);
    }

    public Piece pieceAt(Square square) {
        return pieces[square.index()];
    }

    public Piece put(Square square, Piece piece) {
        Piece oldPiece = pieces[square.index()];
        pieces[square.index()] = piece;
        return oldPiece;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Board board)) return false;
        return Arrays.equals(pieces, board.pieces);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pieces);
    }
}
