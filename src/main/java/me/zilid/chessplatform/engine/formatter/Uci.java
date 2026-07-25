package me.zilid.chessplatform.engine.formatter;

import me.zilid.chessplatform.engine.Move;
import me.zilid.chessplatform.engine.PieceType;
import me.zilid.chessplatform.engine.Position;
import me.zilid.chessplatform.engine.Square;

import java.util.Optional;

public class Uci {
    private Uci() {
    }

    public static String format(Move move) {
        String base = move.from().toNotation() + move.to().toNotation();
        return move.isPromotion() ? base + promotionChar(move.promotionType()) : base;
    }

    public static String format(UciMove uciMove) {
        String base = uciMove.from().toNotation() + uciMove.to().toNotation();
        return uciMove.promotion() != null ? base + promotionChar(uciMove.promotion()) : base;
    }

    public static Optional<UciMove> parse(Position position, String uci) {
        if (uci.length() != 4 && uci.length() != 5) {
            return Optional.empty();
        }
        Square from = null;
        Square to = null;
        PieceType promotion = null;
        try {
            from = Square.fromNotation(uci.substring(0, 2));
            to = Square.fromNotation(uci.substring(2, 4));
            if (uci.length() == 5) {
                promotion = promotionType(uci.charAt(4));
            }
            return Optional.of(new UciMove(from, to, promotion));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
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
