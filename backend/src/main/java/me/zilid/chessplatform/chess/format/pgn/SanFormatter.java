package me.zilid.chessplatform.chess.format.pgn;

import java.util.List;
import me.zilid.chessplatform.chess.*;

public class SanFormatter {
    public static String format(Position preMovePosition, Move move) {
        StringBuilder sb = new StringBuilder();
        if (move.type() == MoveType.CASTLE_KINGSIDE) {
            sb.append("O-O");
        } else if (move.type() == MoveType.CASTLE_QUEENSIDE) {
            sb.append("O-O-O");
        } else {
            // piece symbol (pawn symbol is empty string)
            sb.append(pieceTypeToSymbol(move.moved()));
            // add ambiguation
            String disambiguation = getDisambiguation(preMovePosition, move.to(), move.from());
            sb.append(disambiguation); // disambiguation for pawn and king is empty string

            // add 'x' for captures (including en-passant)
            boolean isCapture = move.isEnPassant() || preMovePosition.getPieceAt(move.to()) != null;
            if (isCapture) {
                if (move.moved() == PieceType.PAWN || move.isEnPassant()) {
                    sb.append(move.from().toNotation().charAt(0));
                }
                sb.append('x');
            }

            // add destination square notation
            sb.append(move.to().toNotation());
        }

        if (move.promotionType() != null) {
            sb.append("=").append(pieceTypeToSymbol(move.promotionType()));
        }

        UndoInfo undo = preMovePosition.applyMove(move);
        if (preMovePosition.isCheckmate(preMovePosition.getTurnColor())) {
            sb.append("#");
        } else if (preMovePosition.getBoard().isInCheck(preMovePosition.getTurnColor())) {
            sb.append("+");
        }
        preMovePosition.undoMove(move, undo);

        return sb.toString();
    }

    /**
     * calculate the disambiguation string (when multiple same pieces can move to the same square, requires
     * disambiguation)
     */
    private static String getDisambiguation(Position position, Square to, Square square) {
        Piece movingPiece = position.getBoard().pieceAt(square);
        if (movingPiece == null || movingPiece.type() == PieceType.PAWN || movingPiece.type() == PieceType.KING) {
            return "";
        }

        boolean needDisambiguation = false;
        boolean sameFile = false;
        boolean sameRank = false;

        for (int file = 0; file < 8; file++) {
            for (int rank = 0; rank < 8; rank++) {
                // exclude self
                if (file == square.file() && rank == square.rank()) continue;

                Piece other = position.getPieceAt(Square.of(file, rank));

                if (other != null && other.color() == movingPiece.color() && other.type() == movingPiece.type()) {

                    List<Square> moves = MoveGenerator.legalDestinations(position, Square.of(file, rank));

                    if (moves.contains(to)) {
                        needDisambiguation = true;
                        if (file == square.file()) {
                            sameFile = true;
                        }
                        if (rank == square.rank()) {
                            sameRank = true;
                        }
                    }
                }
            }
        }

        // 1. If there are both file and rank ambiguity, use the full notation (e.g., d4, e5)
        // 2. If there are file ambiguity, use the rank number to disambiguate (1-8)
        // 3. If there are rank ambiguity, use the file to disambiguate (a-h)
        if (!needDisambiguation) {
            return "";
        }

        if (sameFile && sameRank) {
            return square.toNotation();
        }
        if (sameFile) {
            return String.valueOf(square.toNotation().charAt(1));
        }
        return String.valueOf(square.toNotation().charAt(0));
    }

    private static String pieceTypeToSymbol(PieceType pieceType) {
        return switch (pieceType) {
            case PAWN -> "";
            case KNIGHT -> "N";
            case BISHOP -> "B";
            case ROOK -> "R";
            case QUEEN -> "Q";
            case KING -> "K";
        };
    }
}
