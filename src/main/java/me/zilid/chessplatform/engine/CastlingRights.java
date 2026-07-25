package me.zilid.chessplatform.engine;

import java.util.Arrays;

public record CastlingRights(int rights) {
    private static final int WHITE_KINGSIDE = mask(Color.WHITE, CastlingSide.KINGSIDE);
    private static final int WHITE_QUEENSIDE = mask(Color.WHITE, CastlingSide.QUEENSIDE);
    private static final int BLACK_KINGSIDE = mask(Color.BLACK, CastlingSide.KINGSIDE);
    private static final int BLACK_QUEENSIDE = mask(Color.BLACK, CastlingSide.QUEENSIDE);
    private static final int ALL_RIGHTS = WHITE_KINGSIDE | WHITE_QUEENSIDE | BLACK_KINGSIDE | BLACK_QUEENSIDE;
    public static final CastlingRights ALL = new CastlingRights(ALL_RIGHTS);
    private static final int NO_RIGHTS = 0;
    public static final CastlingRights NONE = new CastlingRights(NO_RIGHTS);

    private static final int[] SQUARE_MASK = new int[64];

    static {
        Arrays.fill(SQUARE_MASK, ALL_RIGHTS);
        SQUARE_MASK[Square.fromNotation("a1").index()] &= ~WHITE_QUEENSIDE;
        SQUARE_MASK[Square.fromNotation("e1").index()] &= ~(WHITE_KINGSIDE | WHITE_QUEENSIDE);
        SQUARE_MASK[Square.fromNotation("h1").index()] &= ~WHITE_KINGSIDE;

        SQUARE_MASK[Square.fromNotation("a8").index()] &= ~BLACK_QUEENSIDE;
        SQUARE_MASK[Square.fromNotation("e8").index()] &= ~(BLACK_KINGSIDE | BLACK_QUEENSIDE);
        SQUARE_MASK[Square.fromNotation("h8").index()] &= ~BLACK_KINGSIDE;
    }

    public CastlingRights {
        if (rights < 0 || rights > 15) {
            throw new IllegalArgumentException("Illegal rights value: " + rights);
        }
    }

    private static int mask(Color color, CastlingSide side) {
        return 1 << (color.ordinal() * 2 + side.ordinal());
    }

    public boolean has(Color color, CastlingSide side) {
        return (rights & mask(color, side)) != 0;
    }

    public boolean has(Color color) {
        return has(color, CastlingSide.KINGSIDE) || has(color, CastlingSide.QUEENSIDE);
    }

    public CastlingRights with(Color color, CastlingSide side) {
        return new CastlingRights(rights | mask(color, side));
    }

    public CastlingRights without(Color color, CastlingSide side) {
        return new CastlingRights(rights & ~mask(color, side));
    }

    public CastlingRights without(Color color) {
        return new CastlingRights(rights & ~mask(color, CastlingSide.KINGSIDE) & ~mask(color, CastlingSide.QUEENSIDE));
    }

    public CastlingRights afterMove(Square from, Square to) {
        return new CastlingRights(rights & SQUARE_MASK[from.index()] & SQUARE_MASK[to.index()]);
    }
}
