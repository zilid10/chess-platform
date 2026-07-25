package me.zilid.chessplatform.engine;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record Move(
        Square from,
        Square to,
        PieceType moved,
        MoveType type,
        PieceType promotionType // nullable
) {
    public static final Set<PieceType> PROMOTION_CHOICES = Collections.unmodifiableSet(
            EnumSet.of(PieceType.QUEEN, PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP));

    public Move {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(moved, "moved");
        Objects.requireNonNull(type, "type");

        if (from.equals(to)) {
            throw new IllegalArgumentException("from and to can't be the same");
        }
        if (promotionType != null && !PROMOTION_CHOICES.contains(promotionType)) {
            throw new IllegalArgumentException("promotion type can't be " + promotionType);
        }
    }

    public static Move doublePush(Square from, Square to) {
        return new Move(from, to, PieceType.PAWN, MoveType.DOUBLE_PUSH, null);
    }

    public static Move normal(Square from, Square to, PieceType moved) {
        return new Move(from, to, moved, MoveType.NORMAL, null);
    }

    public static Move enPassant(Square from, Square to) {
        return new Move(from, to, PieceType.PAWN, MoveType.EN_PASSANT, null);
    }

    public static Move promotion(Square from, Square to, PieceType promotionType) {
        Objects.requireNonNull(promotionType, "promotionType");
        return new Move(from, to, PieceType.PAWN, MoveType.PROMOTION, promotionType);
    }

    public static Move castleKingside(Color color) {
        int rank = color.isWhite() ? 0 : 7;
        return new Move(Square.of(4, rank), Square.of(6, rank), PieceType.KING, MoveType.CASTLE_KINGSIDE, null);
    }

    public static Move castleQueenside(Color color) {
        int rank = color.isWhite() ? 0 : 7;
        return new Move(Square.of(4, rank), Square.of(2, rank), PieceType.KING, MoveType.CASTLE_QUEENSIDE, null);
    }

    public boolean isPromotion() {
        return promotionType != null;
    }

    public boolean isEnPassant() {
        return type == MoveType.EN_PASSANT;
    }

    public boolean isCastle() {
        return type == MoveType.CASTLE_KINGSIDE || type == MoveType.CASTLE_QUEENSIDE;
    }
}
