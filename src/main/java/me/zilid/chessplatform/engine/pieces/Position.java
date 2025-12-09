package me.zilid.chessplatform.engine.pieces;

import java.util.regex.Pattern;

public record Position(int x, int y) {
    public static Pattern notationPattern = Pattern.compile("[a-h][1-8]");
    public static Position fromNotation(String notation) {
        if (!notationPattern.matcher(notation).matches()) {
            throw new IllegalArgumentException("Invalid notation");
        }

        return new Position(notation.charAt(0) - 'a', notation.charAt(1) - '1');
    }
    public String toNotation() {
        return String.valueOf(new char[]{(char) (x + 'a'), (char)(y + '0')});
    }
}
