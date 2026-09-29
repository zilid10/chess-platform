package me.zilid.chessplatform.chess.game;

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
    DRAW_BY_AGREEMENT("Draw by agreement"),
    FLAGGED_WHITE_WINS("White wins by flag"),
    FLAGGED_BLACK_WINS("Black wins by flag"),
    // A player ran out of time, but their opponent had no way to checkmate
    DRAW_BY_TIMEOUT_VS_INSUFFICIENT_MATERIAL("Draw by timeout vs insufficient material"),
    // A first move did not come in time; the game has no result and is not rated or archived
    ABORTED("Game aborted");

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
            return "1/2-1/2";
        }
        return "";
    }

    public String getReason() {
        return switch (this) {
            case CHECKMATE_BLACK_WINS, CHECKMATE_WHITE_WINS -> "CHECKMATE";
            case FLAGGED_WHITE_WINS, FLAGGED_BLACK_WINS -> "FLAGGED";
            case STALEMATE -> "STALEMATE";
            case RESIGNED_BLACK_WINS, RESIGNED_WHITE_WINS -> "RESIGNATION";
            case DRAW_BY_AGREEMENT -> "ACCEPT_DRAW";
            case DRAW_BY_REPETITION -> "THREE_FOLD_REPETITION";
            case DRAW_BY_FIFTY_MOVE_RULE -> "FIFTY_MOVE_RULE";
            case DRAW_BY_INSUFFICIENT_MATERIAL -> "INSUFFICIENT_MATERIAL";
            case DRAW_BY_TIMEOUT_VS_INSUFFICIENT_MATERIAL -> "FLAGGED_INSUFFICIENT_MATERIAL";
            case ABORTED -> "ABORTED";
            case ONGOING -> "";
        };
    }

    public boolean isGameOver() {
        return this != ONGOING;
    }

    public boolean isWhiteWin() {
        return this == CHECKMATE_WHITE_WINS || this == RESIGNED_WHITE_WINS || this == FLAGGED_WHITE_WINS;
    }

    public boolean isBlackWin() {
        return this == CHECKMATE_BLACK_WINS || this == RESIGNED_BLACK_WINS || this == FLAGGED_BLACK_WINS;
    }

    public boolean isDraw() {
        return this == STALEMATE ||
                this == DRAW_BY_REPETITION ||
                this == DRAW_BY_FIFTY_MOVE_RULE ||
                this == DRAW_BY_INSUFFICIENT_MATERIAL ||
                this == DRAW_BY_TIMEOUT_VS_INSUFFICIENT_MATERIAL ||
                this == DRAW_BY_AGREEMENT;
    }
}
