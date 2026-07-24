package me.zilid.chessplatform.engine;

import java.util.regex.Pattern;

public record Square(int x, int y) {
    public Square {
        if (x < 0 || y < 0 || x > 7 || y > 7) {
            throw new IllegalArgumentException("Square out of bounds: (" + x + ", " + y + "), expected x and y in range 0-7");
        }
    }

    public static final Pattern SquarePattern = Pattern.compile("[a-h][1-8]");
    public static Square fromNotation(String notation) {
        if (!SquarePattern.matcher(notation).matches()) {
            throw new IllegalArgumentException("Invalid notation: " + notation);
        }

        return new Square(notation.charAt(0) - 'a', notation.charAt(1) - '1');
    }

    public String toNotation() {
        return String.valueOf(new char[]{(char) (x + 'a'), (char)(y + '1')});
    }

    public static boolean isValid(int x, int y) {
        return x < 0 || y < 0 || x > 7 || y > 7;
    }
}
