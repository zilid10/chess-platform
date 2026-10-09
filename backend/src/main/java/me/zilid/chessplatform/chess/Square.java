package me.zilid.chessplatform.chess;

import java.util.regex.Pattern;

public record Square(int index) {
    public static final Pattern SquarePattern = Pattern.compile("[a-h][1-8]");

    public Square {
        if (index < 0 || index >= 64) {
            throw new IllegalArgumentException("Square index out of range: " + index);
        }
    }

    public static Square of(int file, int rank) {
        if (!isValid(file, rank)) {
            throw new IllegalArgumentException(
                    "Square out of bounds: (" + file + ", " + rank + "), expected file and rank in range 0-7");
        }
        return new Square(rank * 8 + file);
    }

    public static boolean isValid(int file, int rank) {
        return file >= 0 && rank >= 0 && file < 8 && rank < 8;
    }

    public static Square fromNotation(String notation) {
        if (!SquarePattern.matcher(notation).matches()) {
            throw new IllegalArgumentException("Invalid notation: " + notation);
        }

        return Square.of(notation.charAt(0) - 'a', notation.charAt(1) - '1');
    }

    public int file() {
        return index % 8;
    }

    public int rank() {
        return index / 8;
    }

    public String toNotation() {
        return String.valueOf(new char[] {(char) (file() + 'a'), (char) (rank() + '1')});
    }
}
