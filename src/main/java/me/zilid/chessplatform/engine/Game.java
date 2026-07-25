package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.formatter.PgnWriter;
import me.zilid.chessplatform.model.entity.UserPrincipal;

import java.time.Instant;
import java.util.*;

/**
 * Represents a complete chess game with history and metadata
 */
public class Game {
    // chess engine (board information)
    private final ChessEngine engine;

    // move history management
    private final List<Move> history;
    private final Map<Integer, Integer> positionHistory;

    // game metadata
    private final Instant startTime;
    private volatile Instant endTime;
    private volatile GameStatus status;
    private volatile UserPrincipal whitePlayer;
    private volatile UserPrincipal blackPlayer;
    private volatile Color drawOfferedBy;

    public Game() {
        this(null, null);
    }

    public Game(UserPrincipal whitePlayer, UserPrincipal blackPlayer) {
        engine = new ChessEngine();
        history = new ArrayList<>();
        positionHistory = new HashMap<>();
        status = GameStatus.ONGOING;
        startTime = Instant.now();
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
    }

    public Game(ChessEngine engine,
                List<Move> history,
                Map<Integer, Integer> positionHistory,
                Instant startTime,
                Instant endTime,
                GameStatus status,
                UserPrincipal whitePlayer,
                UserPrincipal blackPlayer,
                Color drawOfferedBy) {
        this.engine = engine;
        this.history = history;
        this.positionHistory = positionHistory;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
        this.drawOfferedBy = drawOfferedBy;
    }

    /**
     * Make a move using chess notation
     */
    public synchronized boolean makeMove(String fromNotation, String toNotation, PieceType promotionType) {
        if (status.isGameOver()) {
            return false; // GameService is already over
        }

        try {
            Square from = Square.fromNotation(fromNotation);
            Square to = Square.fromNotation(toNotation);

            // Make moves
            Move move = MoveGenerator.findLegalMove(engine.getPosition(), from, to, promotionType)
                    .orElseThrow(() -> new IllegalArgumentException("no such moves"));
            engine.getPosition().applyMove(move);

            // Record the move with special move flags
            history.add(move);
            int boardHash = engine.getPosition().hashCode();
            positionHistory.put(boardHash, positionHistory.getOrDefault(boardHash, 0) + 1);

            // Update game status
            updateGameStatus();

            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Get valid moves for a piece at the given position
     */
    public synchronized List<Square> getValidMoves(String position) {
        return engine.getValidMoves(position);
    }

    public synchronized GameSnapShot getGameSnapshot() {
        return new GameSnapShot(
                getFen(),
                List.copyOf(history),
                Map.copyOf(positionHistory),
                startTime,
                endTime,
                status,
                whitePlayer.getId(),
                blackPlayer.getId(),
                drawOfferedBy
        );
    }

    /**
     * Get the current board state of the game
     */
    public synchronized String getFen() {
        return engine.getFen();
    }

    public synchronized String getLastMoveFrom() {
        return history.isEmpty() ? null : history.getLast().from().toNotation();
    }

    public synchronized String getLastMoveTo() {
        return history.isEmpty() ? null : history.getLast().to().toNotation();
    }

    /**
     * Resign the game for the current player
     */
    public synchronized void resign(Color color) {
        if (status.isGameOver()) {
            return;
        }

        status = color.isWhite() ?
                GameStatus.RESIGNED_BLACK_WINS :
                GameStatus.RESIGNED_WHITE_WINS;
        endTime = Instant.now();
    }

    /**
     * Offer/accept a draw
     */
    public synchronized void agreeDraw() {
        if (status.isGameOver()) {
            return;
        }

        status = GameStatus.DRAW_BY_AGREEMENT;
        endTime = Instant.now();
    }

    /**
     * Update the game status based on current board state
     */
    private synchronized void updateGameStatus() {
        if (engine.isCheckmate()) {
            status = engine.getTurnColor().isWhite() ?
                    GameStatus.CHECKMATE_BLACK_WINS :
                    GameStatus.CHECKMATE_WHITE_WINS;
            endTime = Instant.now();
        } else if (engine.isStalemate()) {
            status = GameStatus.STALEMATE;
            endTime = Instant.now();
        } else if (isThreefoldRepetition()) {
            status = GameStatus.DRAW_BY_REPETITION;
            endTime = Instant.now();
        } else if (engine.isFiftyMoveRule()) {
            status = GameStatus.DRAW_BY_FIFTY_MOVE_RULE;
            endTime = Instant.now();
        } else if (engine.isInsufficientMaterial()) {
            status = GameStatus.DRAW_BY_INSUFFICIENT_MATERIAL;
            endTime = Instant.now();
        }
    }

    private synchronized boolean isThreefoldRepetition() {
        return positionHistory.getOrDefault(engine.getPosition().hashCode(), 0) >= 3;
    }


    public synchronized Color getTurnColor() {
        return engine.getTurnColor();
    }

    public synchronized int getRound() {
        return (history.size() / 2) + 1;
    }

    public synchronized List<Move> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public synchronized String getNotation() {
        if (!isGameOver()) {
            return "";
        }
        PgnWriter pgnWriter = new PgnWriter();
        StringBuilder sb = new StringBuilder();
        sb.append("[StartTime \"").append(startTime).append("\"]\n");
        sb.append("[EndTime \"").append(endTime).append("\"]\n");
        sb.append("[Round \"").append(getRound()).append("\"]\n");
        sb.append("[White \"").append(whitePlayer.getUsername()).append("\"]\n");
        sb.append("[Black \"").append(blackPlayer.getUsername()).append("\"]\n");
        sb.append("[Result \"").append(status.getSymbol()).append("\"]\n");
        sb.append("[Termination \"").append(status.getDescription()).append("\"]\n");
        sb.append("\n");
        sb.append(pgnWriter.format(Position.startingPosition(), history)).append("\n");
        sb.append(status.getSymbol());
        return sb.toString();
    }

    /**
     * Check if now is the turn of the given user
     */
    public synchronized boolean isUserTurn(UserPrincipal currentUser) {
        return switch (engine.getTurnColor()) {
            case WHITE -> currentUser.equals(whitePlayer);
            case BLACK -> currentUser.equals(blackPlayer);
        };
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public UserPrincipal getWhitePlayer() {
        return whitePlayer;
    }

    public void setWhitePlayer(UserPrincipal whitePlayer) {
        this.whitePlayer = whitePlayer;
    }

    public UserPrincipal getBlackPlayer() {
        return blackPlayer;
    }

    public void setBlackPlayer(UserPrincipal blackPlayer) {
        this.blackPlayer = blackPlayer;
    }

    public synchronized Color getDrawOfferedBy() {
        return drawOfferedBy;
    }

    public synchronized boolean offerDraw(Color by) {
        drawOfferedBy = by;
        return true;
    }

    public synchronized boolean acceptDraw(Color by) {
        if (drawOfferedBy == null) return false;
        if (drawOfferedBy != by.opposite()) return false;
        agreeDraw();
        drawOfferedBy = null;
        return true;
    }

    public GameStatus getStatus() {
        return status;
    }

    public boolean isGameOver() {
        GameStatus gameStatus = status;
        return gameStatus.isGameOver();
    }

    /**
     * Get the color in this game of the given user, spectator will get a null
     */
    public Color getPlayerColor(UserPrincipal currentUser) {
        if (currentUser.equals(whitePlayer)) {
            return Color.WHITE;
        }
        if (currentUser.equals(blackPlayer)) {
            return Color.BLACK;
        }
        return null;
    }

    /**
     * Check if the given user is the player of the game
     */
    public boolean isValidPlayer(UserPrincipal currentUser) {
        return currentUser.equals(whitePlayer) || currentUser.equals(blackPlayer);
    }
}
