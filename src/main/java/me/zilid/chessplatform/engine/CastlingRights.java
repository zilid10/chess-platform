package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.pieces.Piece;
import org.apache.logging.log4j.util.Cast;

import java.util.regex.Pattern;

public record CastlingRights(int rights) {
    private static final int WHITE_KINGSIDE = 1;
    private static final int WHITE_QUEENSIDE = 1 << 1;
    private static final int BLACK_KINGSIDE = 1 << 2;
    private static final int BLACK_QUEENSIDE = 1 << 3;

    public CastlingRights() {
        this(WHITE_KINGSIDE | WHITE_QUEENSIDE | BLACK_KINGSIDE | BLACK_QUEENSIDE);
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

    public CastlingRights withoutWhiteKingside() {
        int newRights = rights & ~WHITE_KINGSIDE;
        return new CastlingRights(newRights);
    }

    public CastlingRights withoutWhiteQueenside() {
        int newRights = rights & ~WHITE_QUEENSIDE;
        return new CastlingRights(newRights);
    }

    public CastlingRights withoutBlackKingside() {
        int newRights = rights & ~BLACK_KINGSIDE;
        return new CastlingRights(newRights);
    }

    public CastlingRights withoutBlackQueenside() {
        int newRights = rights & ~BLACK_QUEENSIDE;
        return new CastlingRights(newRights);
    }

    public CastlingRights withoutBlack() {
        int newRights = rights & ~BLACK_KINGSIDE & ~BLACK_QUEENSIDE;
        return new CastlingRights(newRights);
    }

    public CastlingRights withoutWhite() {
        int newRights = rights & ~WHITE_KINGSIDE & ~WHITE_QUEENSIDE;
        return new CastlingRights(newRights);
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

    private static final Pattern castlingRightsPattern = Pattern.compile("^(-|(?!$)K?Q?k?q?)$");

    public static CastlingRights fromSymbol(String symbol) {
        if (symbol == null || !castlingRightsPattern.matcher(symbol).matches()) {
            throw new IllegalArgumentException("castling rights symbol is not valid: '" + symbol + "'");
        }

        int rights = 0;
        if (symbol.contains("K")) {
            rights |= WHITE_KINGSIDE;
        }
        if (symbol.contains("Q")) {
            rights |= WHITE_QUEENSIDE;
        }
        if (symbol.contains("k")) {
            rights |= BLACK_KINGSIDE;
        }
        if (symbol.contains("q")) {
            rights |= BLACK_QUEENSIDE;
        }
        return new CastlingRights(rights);
    }
}
