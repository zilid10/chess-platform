package me.zilid.chessplatform.engine.formatter;

import me.zilid.chessplatform.engine.*;


public class SanFormatter {
    public String format(Position preMovePosition, Move move) {
        StringBuilder sb = new StringBuilder();
        if (move.type() == MoveType.CASTLE_KINGSIDE) {
            sb.append("O-O");
        } else if (move.type() == MoveType.CASTLE_QUEENSIDE) {
            sb.append("O-O-O");
        } else {
            // piece symbol (pawn symbol is empty string)
            sb.append(pieceTypeToSymbol(move.pieceType()));
            // add ambiguation
            String disambiguation = preMovePosition.getDisambiguation(move.from(), move.to());
            sb.append(disambiguation); // disambiguation for pawn and king is empty string

            // add 'x' for captures (including en-passant)
            if (move.isCapture()) {
                if (move.pieceType() == PieceType.PAWN || move.isEnPassant()) {
                    sb.append(move.from().toNotation().charAt(0));
                }
                sb.append('x');
            }

            // add destination square notation
            sb.append(move.to().toNotation());
        }

        if (move.isPromotion()) {
            sb.append("=").append(pieceTypeToSymbol(move.promotionType()));
        }

        UndoInfo undo = preMovePosition.applyMove(move);
        if (preMovePosition.isCheckmate(preMovePosition.getTurnColor())) {
            sb.append("#");
        } else if (preMovePosition.isInCheck(preMovePosition.getTurnColor())) {
            sb.append("+");
        }
        preMovePosition.undoMove(move, undo);

        return sb.toString();
    }

    public String pieceTypeToSymbol(PieceType pieceType) {
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
