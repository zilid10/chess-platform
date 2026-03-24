package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.pieces.Piece;

import java.util.regex.Pattern;

public class CastlingRights {
    private int rights;

    private static final int WHITE_KINGSIDE = 1;
    private static final int WHITE_QUEENSIDE = 1 << 1;
    private static final int BLACK_KINGSIDE = 1 << 2;
    private static final int BLACK_QUEENSIDE = 1 << 3;

    public CastlingRights() {
        rights = WHITE_KINGSIDE | WHITE_QUEENSIDE | BLACK_KINGSIDE | BLACK_QUEENSIDE;
    }

    public CastlingRights(int rights) {
        this.rights = rights;
    }

    public int getRightsRaw() {
        return rights;
    }

    public boolean whiteKingside() {
        return (WHITE_KINGSIDE & rights) != 0;
    }

    public boolean whiteQueenside() {
        return (WHITE_QUEENSIDE & rights) != 0;
    }

    public boolean blackKingside() {
        return (BLACK_KINGSIDE & rights) != 0;
    }

    public boolean blackQueenside() {
        return (BLACK_QUEENSIDE & rights) != 0;
    }

    public void removeWhiteKingside() {
        rights &= ~WHITE_KINGSIDE;
    }

    public void removeWhiteQueenside() {
        rights &= ~WHITE_QUEENSIDE;
    }

    public void removeBlackKingside() {
        rights &= ~BLACK_KINGSIDE;
    }

    public void removeBlackQueenside() {
        rights &= ~BLACK_QUEENSIDE;
    }

    public void removeBlack() {
        rights &= ~BLACK_KINGSIDE;
        rights &= ~BLACK_QUEENSIDE;
    }

    public void removeWhite() {
        rights &= ~WHITE_KINGSIDE;
        rights &= ~WHITE_QUEENSIDE;
    }

    public boolean hasCastlingRight(Piece.Color color, boolean isKingside) {
        return switch (color) {
            case WHITE -> isKingside ? whiteKingside() : whiteQueenside();
            case BLACK -> isKingside ? blackKingside() : blackQueenside();
        };
    }

    public String getSymbol() {
        if (rights == 0) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        if ((rights & WHITE_KINGSIDE) != 0) {
            sb.append("K");
        }
        if ((rights & WHITE_QUEENSIDE) != 0) {
            sb.append("Q");
        }
        if ((rights & BLACK_KINGSIDE) != 0) {
            sb.append("k");
        }
        if ((rights & BLACK_QUEENSIDE) != 0) {
            sb.append("q");
        }
        return sb.toString();
    }

    private static final Pattern castlingRightsPattern = Pattern.compile("^(K?Q?k?q?|-)$");

    public static CastlingRights fromSymbol(String string) {
        if (string == null || string.isEmpty()) {
            throw new IllegalArgumentException("string is null or empty");
        }
        if (string.equals("-")) {
            return new CastlingRights(0);
        }

        int rights = 0;
        for (char c : string.toCharArray()) {
            switch (c) {
                case 'K' -> {
                    if ((rights & WHITE_KINGSIDE) != 0) {
                        throw new IllegalArgumentException("illegal castling rights symbol: duplicate K");
                    }
                    rights |= WHITE_KINGSIDE;
                }
                case 'Q' -> {
                    if ((rights & WHITE_QUEENSIDE) != 0) {
                        throw new IllegalArgumentException("illegal castling rights symbol: duplicate Q");
                    }
                    rights |= WHITE_QUEENSIDE;
                }
                case 'k' -> {
                    if ((rights & BLACK_KINGSIDE) != 0) {
                        throw new IllegalArgumentException("illegal castling rights symbol: duplicate k");
                    }
                    rights |= BLACK_KINGSIDE;
                }
                case 'q' -> {
                    if ((rights & BLACK_QUEENSIDE) != 0) {
                        throw new IllegalArgumentException("illegal castling rights symbol: duplicate q");
                    }
                    rights |= BLACK_QUEENSIDE;
                }
                default -> {
                    throw new IllegalArgumentException("illegal castling rights symbol: " + c);
                }
            }
        }
        return new CastlingRights(rights);
    }
}
