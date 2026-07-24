package me.zilid.chessplatform.engine.formatter;

import me.zilid.chessplatform.engine.PieceType;
import me.zilid.chessplatform.engine.Position;
import me.zilid.chessplatform.engine.Move;
import me.zilid.chessplatform.engine.UndoInfo;

public class SanFormatter {
    public String format(Position preMovePosition, Move move) {
            StringBuilder sb = new StringBuilder();
            if (move.moveType() == Move.MoveType.CASTLE_KINGSIDE) {
                sb.append("O-O");
            } else if (move.moveType() == Move.MoveType.CASTLE_QUEENSIDE) {
                sb.append("O-O-O");
            } else {
                // piece symbol (pawn symbol is empty string)
                sb.append(move.pieceType().getSymbol());
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
                sb.append("=").append(move.promotionType().getSymbol());
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
}
