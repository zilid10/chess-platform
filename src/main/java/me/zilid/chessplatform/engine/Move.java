package me.zilid.chessplatform.engine;

public record Move(
        Square from,
        Square to,
        MoveType moveType,
        PieceType pieceType,
        PieceType captureType, // nullable
        PieceType promotionType // nullable
) {

    public Move {
        if (from == null || to == null || pieceType == null || moveType == null) {
            throw new IllegalArgumentException("Invalid move");
        }
        if (moveType == MoveType.PROMOTION && promotionType == null) {
            throw new IllegalArgumentException("Promotion must have promotionType");
        }
    }

    public boolean isCapture() {
        return captureType != null;
    }

    public boolean isPromotion() {
        return moveType == MoveType.PROMOTION && promotionType != null;
    }

    public boolean isEnPassant() {
        return moveType == MoveType.EN_PASSANT;
    }

    public boolean isCastle() {
        return moveType == MoveType.CASTLE_KINGSIDE || moveType == MoveType.CASTLE_QUEENSIDE;
    }

    public boolean isKingSide() {
        return moveType == MoveType.CASTLE_KINGSIDE;
    }

    public boolean isQueenSide() {
        return moveType == MoveType.CASTLE_QUEENSIDE;
    }

    public enum MoveType {
        NORMAL,
        EN_PASSANT,
        CASTLE_KINGSIDE,
        CASTLE_QUEENSIDE,
        PROMOTION
    }
}
