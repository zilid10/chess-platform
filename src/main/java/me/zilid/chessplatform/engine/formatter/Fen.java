package me.zilid.chessplatform.engine.formatter;

import me.zilid.chessplatform.engine.*;
import me.zilid.chessplatform.engine.pieces.Piece;

public class Fen {

    public static Position read(String fen) {
        String[] parsedFen = fen.split("\\s+");
        if (parsedFen.length != 6) {
            throw new IllegalArgumentException("Invalid fen: " + fen);
        }

        Piece[] pieces = new Piece[64];
        Board board = new Board();
        // i = FEN rank row (rank 8 first), j = file; square (file j, rank 8-i) lives at board[j][7 - i]
        int rank = 7, file = 0;
        for (char c : parsedFen[0].toCharArray()) {
            switch (c) {
                case 'Q', 'q', 'K', 'k', 'R', 'r', 'B', 'b', 'N', 'n', 'P', 'p' -> {
                    board.put(Square.of(file, rank), Piece.fromNotation(c));
                    file++;
                }
                case '/' -> {
                    if (file != 8) {
                        throw new IllegalArgumentException("Too many files in FEN: " + fen);
                    }
                    if (rank <= 0) {
                        throw new IllegalArgumentException("Too many ranks in FEN: " + fen);
                    }
                    rank--;
                    file = 0;
                }
                default -> {
                    if (c > '8' || c < '1') {
                        throw new IllegalArgumentException("Invalid fen: " + fen);
                    }
                    file += c - '0';
                }
            }
            if (file > 8) {
                throw new IllegalArgumentException("Too many files in FEN: " + fen);
            }
        }
        if (file != 8 || rank != 0) {
            throw new IllegalArgumentException("Incomplete board in FEN: " + fen);
        }
        Color turnColor = Color.fromSymbol(parsedFen[1]);
        CastlingRights castlingRights = CastlingRights.fromSymbol(parsedFen[2]);
        Square enPassantTarget = parsedFen[3].equals("-") ? null : Square.fromNotation(parsedFen[3]);
        try {
            int halfMoveClock = Integer.parseInt(parsedFen[4]);
            int fullMoveClock = Integer.parseInt(parsedFen[5]);
            return new Position(new Board(pieces), turnColor, castlingRights, enPassantTarget, halfMoveClock, fullMoveClock);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid fen: " + fen, e);
        }
    }


    /**
     * Get the fen representation of the current position
     */
    public static String write(Position position) {
        StringBuilder fen = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int count = 0;
            for (int file = 0; file < 8; file++) {
                Piece piece = position.getBoard().pieceAt(Square.of(file, rank));
                if (piece != null && count != 0) {
                    fen.append(count);
                    fen.append(piece.getNotation());
                    count = 0;
                } else if (piece != null) {
                    fen.append(piece.getNotation());
                } else {
                    count++;
                }
                if (file == 7 && count != 0) {
                    fen.append(count);
                }
            }
            if (rank != 0) {
                fen.append("/");
            }
        }
        fen.append(" ").append(position.getTurnColor().getSymbol());
        fen.append(" ").append(position.getCastlingRights().getSymbol());
        fen.append(" ").append(position.getEnPassantTarget() == null ? "-" : position.getEnPassantTarget().toNotation());
        fen.append(" ").append(position.getHalfMoveClock());
        fen.append(" ").append(position.getFullMoveClock());
        return fen.toString();
    }
}
