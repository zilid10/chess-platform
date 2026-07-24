package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.pieces.Piece;

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
        pieces[0] = Piece.of(PieceType.ROOK, Color.WHITE);
        pieces[1] = Piece.of(PieceType.KNIGHT, Color.WHITE);
        pieces[2] = Piece.of(PieceType.BISHOP, Color.WHITE);
        pieces[3] = Piece.of(PieceType.QUEEN, Color.WHITE);
        pieces[4] = Piece.of(PieceType.KING, Color.WHITE);
        pieces[5] = Piece.of(PieceType.BISHOP, Color.WHITE);
        pieces[6] = Piece.of(PieceType.KNIGHT, Color.WHITE);
        pieces[7] = Piece.of(PieceType.ROOK, Color.WHITE);
        for (int i = 8; i < 16; i++) {
            pieces[i] = Piece.of(PieceType.PAWN, Color.WHITE);
        }
        for (int i = 48; i < 56; i++) {
            pieces[i] = Piece.of(PieceType.PAWN, Color.BLACK);
        }
        pieces[56] = Piece.of(PieceType.ROOK, Color.BLACK);
        pieces[57] = Piece.of(PieceType.KNIGHT, Color.BLACK);
        pieces[58] = Piece.of(PieceType.BISHOP, Color.BLACK);
        pieces[59] = Piece.of(PieceType.QUEEN, Color.BLACK);
        pieces[60] = Piece.of(PieceType.KING, Color.BLACK);
        pieces[61] = Piece.of(PieceType.BISHOP, Color.BLACK);
        pieces[62] = Piece.of(PieceType.KNIGHT, Color.BLACK);
        pieces[63] = Piece.of(PieceType.ROOK, Color.BLACK);
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
