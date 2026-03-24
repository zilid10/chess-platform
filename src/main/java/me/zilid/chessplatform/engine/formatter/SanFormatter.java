package me.zilid.chessplatform.engine.formatter;

import me.zilid.chessplatform.engine.Board;
import me.zilid.chessplatform.engine.Move;
import me.zilid.chessplatform.engine.UndoInfo;
import me.zilid.chessplatform.engine.pieces.Piece;

public class SanFormatter {
    public String format(Board preMoveBoard, Move move) {
            StringBuilder sb = new StringBuilder();
            if (move.moveType() == Move.MoveType.CASTLE_KINGSIDE) {
                sb.append("O-O");
            } else if (move.moveType() == Move.MoveType.CASTLE_QUEENSIDE) {
                sb.append("O-O-O");
            } else {
                // piece symbol (pawn symbol is empty string)
                sb.append(move.pieceType().getSymbol());
                // add ambiguation
                String disambiguation = preMoveBoard.getDisambiguation(move.from(), move.to());
                sb.append(disambiguation); // disambiguation for pawn and king is empty string

                // add 'x' for captures (including en-passant)
                if (move.isCapture()) {
                    if (move.pieceType() == Piece.PieceType.PAWN || move.isEnPassant()) {
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

            UndoInfo undo = preMoveBoard.applyMove(move);
            if (preMoveBoard.isCheckmate(preMoveBoard.getTurnColor())) {
                sb.append("#");
            } else if (preMoveBoard.isInCheck(preMoveBoard.getTurnColor())) {
                sb.append("+");
            }
            preMoveBoard.undoMove(move, undo);

            return sb.toString();
    }
}
