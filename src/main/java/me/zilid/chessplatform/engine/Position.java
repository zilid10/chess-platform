package me.zilid.chessplatform.engine;

import java.util.regex.Pattern;

public record Position(int x, int y) {
    public Position {
        if (x < 0 || y < 0 || x > 7 || y > 7) {
            throw new IllegalArgumentException("Position out of bounds: (" + x + ", " + y + "), expected x and y in range 0-7");
        }
    }

    public static final Pattern positionPattern = Pattern.compile("[a-h][1-8]");
    public static Position fromNotation(String notation) {
        if (!positionPattern.matcher(notation).matches()) {
            throw new IllegalArgumentException("Invalid notation: " + notation);
        }

        return new Position(notation.charAt(0) - 'a', notation.charAt(1) - '1');
    }

    public String toNotation() {
        return String.valueOf(new char[]{(char) (x + 'a'), (char)(y + '1')});
    }
}
