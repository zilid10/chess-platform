package me.zilid.chessplatform.engine;

/**
 * Represents the current status of a chess game
 */
public enum GameStatus {
    ONGOING("GameService in progress"),
    CHECKMATE_WHITE_WINS("White wins by checkmate"),
    CHECKMATE_BLACK_WINS("Black wins by checkmate"),
    RESIGNED_WHITE_WINS("White wins by resignation"),
    RESIGNED_BLACK_WINS("Black wins by resignation"),
    STALEMATE("Draw by stalemate"),
    DRAW_BY_REPETITION("Draw by threefold repetition"),
    DRAW_BY_FIFTY_MOVE_RULE("Draw by fifty-move rule"),
    DRAW_BY_INSUFFICIENT_MATERIAL("Draw by insufficient material"),
    DRAW_BY_AGREEMENT("Draw by agreement");

    private final String description;
    
    GameStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }

    public String getSymbol() {
        if (isWhiteWin()) {
            return "1-0";
        }
        if (isBlackWin()) {
            return "0-1";
        }
        if (isDraw()) {
            return "0-0";
        }
        return "";
    }

    public boolean isGameOver() {
        return this != ONGOING;
    }

    public boolean isWhiteWin() {
        return this == CHECKMATE_WHITE_WINS || this == RESIGNED_WHITE_WINS;
    }

    public boolean isBlackWin() {
        return this == CHECKMATE_BLACK_WINS || this == RESIGNED_BLACK_WINS;
    }

    public boolean isDraw() {
        return this == STALEMATE ||
                this == DRAW_BY_REPETITION ||
                this == DRAW_BY_FIFTY_MOVE_RULE ||
                this == DRAW_BY_INSUFFICIENT_MATERIAL ||
                this == DRAW_BY_AGREEMENT;
    }
}
