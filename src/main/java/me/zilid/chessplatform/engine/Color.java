package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.pieces.Piece;

public enum Color {
    WHITE, BLACK;

    public Color opposite() {
        return this == WHITE ? BLACK : WHITE;
    }

    public boolean isWhite() {
        return this == WHITE;
    }

    public boolean isBlack() {
        return this == BLACK;
    }

    public String getSymbol() {
        return switch (this) {
            case WHITE -> "w";
            case BLACK -> "b";
        };
    }

    public static Color fromSymbol(String color) {
        if (color.equals("w")) {
            return WHITE;
        }
        if (color.equals("b")) {
            return BLACK;
        }
        throw new IllegalArgumentException("invalid color: " + color);
    }


}
