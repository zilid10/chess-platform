package me.zilid.chessplatform.chess.format;

import me.zilid.chessplatform.chess.Move;
import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.Square;

public class Uci {
    private Uci() {
    }

    public static String format(Move move) {
        String base = move.from().toNotation() + move.to().toNotation();
        return move.promotionType() != null ? base + promotionChar(move.promotionType()) : base;
    }

    public static String format(UciMove uciMove) {
        String base = uciMove.from().toNotation() + uciMove.to().toNotation();
        return uciMove.promotion() != null ? base + promotionChar(uciMove.promotion()) : base;
    }

    public static UciMove parse(String uci) {
        if (uci.length() != 4 && uci.length() != 5) {
            throw new IllegalArgumentException("Invalid uci: " + uci);
        }
        
        Square from = Square.fromNotation(uci.substring(0, 2));
        Square to = Square.fromNotation(uci.substring(2, 4));

        PieceType promotion = null;
        if (uci.length() == 5) {
            promotion = promotionType(uci.charAt(4));
        }
        return new UciMove(from, to, promotion);
    }

    private static PieceType promotionType(char c) {
        return switch (c) {
            case 'q' -> PieceType.QUEEN;
            case 'r' -> PieceType.ROOK;
            case 'n' -> PieceType.KNIGHT;
            case 'b' -> PieceType.BISHOP;
            default -> throw new IllegalArgumentException("Unknown promotion type: " + c);
        };
    }

    private static char promotionChar(PieceType pieceType) {
        return switch (pieceType) {
            case QUEEN -> 'q';
            case ROOK -> 'r';
            case BISHOP -> 'b';
            case KNIGHT -> 'n';
            default -> throw new IllegalArgumentException("Invalid promotion piece type: " + pieceType);
        };
    }
}
