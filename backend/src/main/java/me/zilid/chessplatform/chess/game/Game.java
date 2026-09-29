package me.zilid.chessplatform.chess.game;

import me.zilid.chessplatform.chess.*;
import me.zilid.chessplatform.chess.format.Fen;
import me.zilid.chessplatform.chess.format.pgn.PgnFormatter;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Represents a complete chess game with history and metadata
 * <p>
 * Each side's first move has to come within {@link #FIRST_MOVE_TIMEOUT}: White's once both seats are taken, Black's
 * after White's first move. A game whose first moves do not come in time is aborted, with no result.
 */
public class Game {
    public static final Duration FIRST_MOVE_TIMEOUT = Duration.ofSeconds(30);

    // chess engine (board information)
    private final Position position;

    // move history management
    private final List<Move> moves;
    private final List<UndoInfo> undoes;
    private final Map<Integer, Integer> repetitions;

    // game metadata
    private final ChessClock clock;
    private final Instant startTime;
    private volatile @Nullable Instant endTime;
    private volatile GameStatus status;
    private volatile @Nullable Player whitePlayer;
    private volatile @Nullable Player blackPlayer;
    private volatile @Nullable Color drawOfferedBy;
    // While the chess clock has not started: when the side to move must have moved by, once both seats are taken
    private volatile @Nullable Instant firstMoveDeadline;

    public Game(ClockSetting clockSetting) {
        this(null, null, new ChessClock(clockSetting), Instant.now());
    }

    private Game(@Nullable Player whitePlayer,
                 @Nullable Player blackPlayer,
                 ChessClock clock,
                 Instant startTime) {
        this(Position.startingPosition(), whitePlayer, blackPlayer, clock, startTime);
    }

    private Game(Position position,
                 @Nullable Player whitePlayer,
                 @Nullable Player blackPlayer,
                 ChessClock clock,
                 Instant startTime) {
        this.position = position;
        moves = new ArrayList<>();
        undoes = new ArrayList<>();
        repetitions = new HashMap<>();
        repetitions.put(position.hashCode(), 1);
        status = GameStatus.ONGOING;
        this.clock = clock;
        this.startTime = startTime;
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
        this.firstMoveDeadline = whitePlayer != null && blackPlayer != null ? startTime.plus(FIRST_MOVE_TIMEOUT) : null;
    }

    public Game(@Nullable Player whitePlayer,
                @Nullable Player blackPlayer,
                ClockSetting clockSetting) {
        this(whitePlayer, blackPlayer, clockSetting, Instant.now());
    }

    public Game(@Nullable Player whitePlayer,
                @Nullable Player blackPlayer,
                ClockSetting clockSetting,
                Instant startTime) {
        this(whitePlayer, blackPlayer, new ChessClock(clockSetting), startTime);
    }

    public static Game restore(List<Move> moves, ClockSetting clockSetting,
                               Duration whiteRemaining,
                               Duration blackRemaining,
                               @Nullable Instant turnStartAt,
                               Color turnColor,
                               Instant startTime,
                               @Nullable Instant endTime,
                               GameStatus status,
                               @Nullable Player whitePlayer,
                               @Nullable Player blackPlayer,
                               @Nullable Color drawOfferedBy,
                               @Nullable Instant firstMoveDeadline) {
        ChessClock clock = ChessClock.restore(clockSetting, whiteRemaining,
                blackRemaining, turnColor, turnStartAt, status.isGameOver());
        Game game = new Game(whitePlayer, blackPlayer, clock, startTime);
        for (Move move : moves) {
            game.recordMove(move);
        }
        game.endTime = endTime;
        game.status = status;
        game.drawOfferedBy = drawOfferedBy;
        game.firstMoveDeadline = firstMoveDeadline;
        return game;
    }

    /**
     * A game from an arbitrary position, for tests. It cannot be stored: {@link #restore} replays moves from the
     * starting position.
     */
    static Game fromPosition(Position position, Player whitePlayer, Player blackPlayer, ClockSetting clockSetting) {
        return new Game(position, whitePlayer, blackPlayer, new ChessClock(clockSetting), Instant.now());
    }

    // "?" is PGN's value for an unknown player
    private static String pgnName(@Nullable Player player) {
        return player == null ? "?" : player.displayName();
    }

    /**
     * Make a move using chess notation
     */
    public synchronized boolean makeMove(String fromNotation, String toNotation, @Nullable PieceType promotionType, Instant now) {
        if (status.isGameOver()) {
            return false; // GameService is already over
        }

        try {
            Square from = Square.fromNotation(fromNotation);
            Square to = Square.fromNotation(toNotation);

            // Make moves
            Move move = MoveGenerator.findLegalMove(position, from, to, promotionType)
                    .orElseThrow(() -> new IllegalArgumentException("no such moves"));
            // A move made after the mover's time ran out does not count; the game ends on time instead
            if (checkTimeout(now)) {
                return true;
            }
            clock.punch(now);
            recordMove(move);
            // After White's first move Black gets its own window; Black's first move starts the chess clock
            firstMoveDeadline = clock.isRunning() || !isFullySeated() ? null : now.plus(FIRST_MOVE_TIMEOUT);
            updateGameStatus(now);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public synchronized boolean makeMove(String fromNotation, String toNotation, @Nullable PieceType promotionType) {
        return makeMove(fromNotation, toNotation, promotionType, Instant.now());
    }

    /**
     * Get valid moves for a piece at the given position
     */
    public synchronized List<Square> getValidMoves(String fromNotation) {
        Square from = Square.fromNotation(fromNotation);
        return MoveGenerator.legalDestinations(position, from);
    }

    private void recordMove(Move move) {
        undoes.add(position.applyMove(move));
        moves.add(move);
        repetitions.merge(position.hashCode(), 1, Integer::sum);
    }

    /**
     * Get the current board state of the game
     */
    public synchronized String getFen() {
        return Fen.format(position);
    }

    public synchronized @Nullable String getLastMoveFrom() {
        return moves.isEmpty() ? null : moves.getLast().from().toNotation();
    }

    public synchronized @Nullable String getLastMoveTo() {
        return moves.isEmpty() ? null : moves.getLast().to().toNotation();
    }

    /**
     * Resign the game for the current player
     */
    public synchronized void resign(Color color, Instant now) {
        if (status.isGameOver()) {
            return;
        }
        onGameEnd(color.isWhite() ? GameStatus.RESIGNED_BLACK_WINS : GameStatus.RESIGNED_WHITE_WINS, now);
    }

    public synchronized void resign(Color color) {
        resign(color, Instant.now());
    }

    /**
     * Offer/accept a draw
     */
    private synchronized void agreeDraw(Instant now) {
        if (status.isGameOver()) {
            return;
        }
        onGameEnd(GameStatus.DRAW_BY_AGREEMENT, now);
    }

    /**
     * Update the game status based on current board state
     */
    private synchronized void updateGameStatus(Instant now) {
        if (status.isGameOver()) {
            return;
        }
        if (position.isCheckmate(position.getTurnColor())) {
            status = getTurnColor().isWhite() ? GameStatus.CHECKMATE_BLACK_WINS : GameStatus.CHECKMATE_WHITE_WINS;
        } else if (position.isStalemate(position.getTurnColor())) {
            status = GameStatus.STALEMATE;
        } else if (isThreefoldRepetition()) {
            status = GameStatus.DRAW_BY_REPETITION;
        } else if (position.isFiftyMoveRule()) {
            status = GameStatus.DRAW_BY_FIFTY_MOVE_RULE;
        } else if (position.getBoard().isInsufficientMaterial()) {
            status = GameStatus.DRAW_BY_INSUFFICIENT_MATERIAL;
        }
        if (status.isGameOver()) {
            onGameEnd(status, now);
        }
    }

    /**
     * Whether the game is still on but, at {@code now}, the side to move has run out of time or missed the deadline
     * for its first move.
     */
    public synchronized boolean hasTimedOut(Instant now) {
        return !status.isGameOver() && (clock.hasFlagged(now) || isFirstMoveOverdue(now));
    }

    private boolean isFirstMoveOverdue(Instant now) {
        Instant deadline = firstMoveDeadline;
        return !clock.isRunning() && deadline != null && !now.isBefore(deadline);
    }

    /**
     * End the game if a time limit has passed. A game whose first moves did not come in time is aborted. Otherwise
     * the side to move has run out of time and their opponent wins, unless the opponent could not checkmate by any
     * sequence of legal moves; then the game is drawn.
     *
     * @return {@code true} only if this call ended the game
     */
    public synchronized boolean checkTimeout(Instant now) {
        if (!hasTimedOut(now)) {
            return false;
        }
        if (isFirstMoveOverdue(now)) {
            onGameEnd(GameStatus.ABORTED, now);
            return true;
        }
        Color winner = getTurnColor().opposite();
        GameStatus result;
        if (!position.getBoard().hasMatingMaterial(winner)) {
            result = GameStatus.DRAW_BY_TIMEOUT_VS_INSUFFICIENT_MATERIAL;
        } else {
            result = winner.isWhite() ? GameStatus.FLAGGED_WHITE_WINS : GameStatus.FLAGGED_BLACK_WINS;
        }
        onGameEnd(result, now);
        return true;
    }

    /**
     * The instant at which {@link #checkTimeout} will end the game if nothing else happens first, or {@code null} if
     * the game is over or no time limit is running.
     */
    public synchronized @Nullable Instant timeoutDeadline() {
        Instant turnStartAt = clock.getTurnStartAt();
        if (status.isGameOver()) {
            return null;
        }
        if (turnStartAt == null) {
            return firstMoveDeadline;
        }
        return turnStartAt.plus(getTurnColor().isWhite() ? clock.getWhiteRemaining() : clock.getBlackRemaining());
    }

    /**
     * The time {@code color} has left at {@code now}, never below zero.
     */
    public synchronized Duration getRemaining(Color color, Instant now) {
        Duration remaining = clock.remaining(color, now);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public synchronized boolean isClockRunning() {
        return clock.isRunning();
    }

    private synchronized void onGameEnd(GameStatus endStatus, Instant now) {
        firstMoveDeadline = null;
        clock.stop(now);
        endTime = now;
        status = endStatus;
    }

    private synchronized boolean isThreefoldRepetition() {
        return repetitions.getOrDefault(position.hashCode(), 0) >= 3;
    }

    public synchronized Color getTurnColor() {
        return position.getTurnColor();
    }

    public synchronized int getRound() {
        return (moves.size() / 2) + 1;
    }

    public synchronized List<Move> getMoves() {
        return Collections.unmodifiableList(moves);
    }

    public synchronized String getNotation() {
        if (!isGameOver()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[StartTime \"").append(startTime).append("\"]\n");
        sb.append("[EndTime \"").append(endTime).append("\"]\n");
        sb.append("[Round \"").append(getRound()).append("\"]\n");
        sb.append("[White \"").append(pgnName(whitePlayer)).append("\"]\n");
        sb.append("[Black \"").append(pgnName(blackPlayer)).append("\"]\n");
        sb.append("[Result \"").append(status.getSymbol()).append("\"]\n");
        sb.append("[Termination \"").append(status.getDescription()).append("\"]\n");
        sb.append("\n");
        sb.append(PgnFormatter.format(Position.startingPosition(), moves)).append("\n");
        sb.append(status.getSymbol());
        return sb.toString();
    }

    /**
     * Check if now is the turn of the given player
     */
    public synchronized boolean isUserTurn(Player player) {
        return switch (position.getTurnColor()) {
            case WHITE -> player.equals(whitePlayer);
            case BLACK -> player.equals(blackPlayer);
        };
    }

    public Instant getStartTime() {
        return startTime;
    }

    public TimeControl getTimeControl() {
        return clock.getClockSetting().category();
    }

    public ClockSetting getClockSetting() {
        return clock.getClockSetting();
    }

    public Duration getWhiteRemaining() {
        return clock.getWhiteRemaining();
    }

    public Duration getBlackRemaining() {
        return clock.getBlackRemaining();
    }

    public @Nullable Instant getTurnStartAt() {
        return clock.getTurnStartAt();
    }

    public @Nullable Instant getEndTime() {
        return endTime;
    }

    public @Nullable Player getWhitePlayer() {
        return whitePlayer;
    }

    /**
     * Seat {@code player} as {@code color}. Taking the last open seat before any move starts White's window for the
     * first move.
     */
    public synchronized void seat(Color color, Player player, Instant now) {
        if (color.isWhite()) {
            whitePlayer = player;
        } else {
            blackPlayer = player;
        }
        if (isFullySeated() && !status.isGameOver() && !clock.isRunning() && firstMoveDeadline == null) {
            firstMoveDeadline = now.plus(FIRST_MOVE_TIMEOUT);
        }
    }

    private boolean isFullySeated() {
        return whitePlayer != null && blackPlayer != null;
    }

    /**
     * The time by which the side to move must make its first move, or {@code null} once the chess clock is running,
     * while a seat is open, or after the game ended.
     */
    public synchronized @Nullable Instant getFirstMoveDeadline() {
        return firstMoveDeadline;
    }

    public @Nullable Player getBlackPlayer() {
        return blackPlayer;
    }


    public synchronized @Nullable Color getDrawOfferedBy() {
        return drawOfferedBy;
    }

    public synchronized void offerDraw(Color by) {
        drawOfferedBy = by;
    }

    public synchronized void acceptDraw(Color by, Instant now) {
        if (drawOfferedBy == null)
            return;
        if (drawOfferedBy != by.opposite())
            return;
        agreeDraw(now);
        drawOfferedBy = null;
    }

    public synchronized void acceptDraw(Color by) {
        acceptDraw(by, Instant.now());
    }

    public GameStatus getStatus() {
        return status;
    }

    public boolean isGameOver() {
        GameStatus gameStatus = status;
        return gameStatus.isGameOver();
    }

    /**
     * Get the color in this game of the given player, spectator will get a null
     */
    public @Nullable Color getPlayerColor(Player player) {
        if (player.equals(whitePlayer)) {
            return Color.WHITE;
        }
        if (player.equals(blackPlayer)) {
            return Color.BLACK;
        }
        return null;
    }

    /**
     * Check if the given player is seated in the game
     */
    public boolean isValidPlayer(Player player) {
        return player.equals(whitePlayer) || player.equals(blackPlayer);
    }
}
