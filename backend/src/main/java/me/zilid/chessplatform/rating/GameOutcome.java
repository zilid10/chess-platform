package me.zilid.chessplatform.rating;

public enum GameOutcome {
    WHITE_WINS(1.0),
    DRAW(0.5),
    BLACK_WINS(0.0);

    private final double whiteScore;

    GameOutcome(double score) {
        whiteScore = score;
    }

    public double whiteScore() {
        return whiteScore;
    }

    public double blackScore() {
        return 1.0 - whiteScore;
    }
}
