package me.zilid.chessplatform.chess.game;

import me.zilid.chessplatform.chess.*;
import me.zilid.chessplatform.chess.format.Fen;
import me.zilid.chessplatform.chess.format.pgn.PgnFormatter;
import me.zilid.chessplatform.chess.game.clock.TimeControl;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.*;

/**
 * Represents a complete chess game with history and metadata
 */
public class Game {
    // chess engine (board information)
    private final Position position;

    // move history management
    private final List<Move> moves;
    private final List<UndoInfo> undoes;
    private final Map<Integer, Integer> repetitions;

    // game metadata
    private final Instant startTime;
    private final TimeControl timeControl;
    private volatile @Nullable Instant endTime;
    private volatile GameStatus status;
    private volatile @Nullable Player whitePlayer;
    private volatile @Nullable Player blackPlayer;
    private volatile @Nullable Color drawOfferedBy;

    public Game() {
        this(null, null);
    }

    public Game(@Nullable Player whitePlayer, @Nullable Player blackPlayer) {
        this(whitePlayer, blackPlayer, TimeControl.RAPID);
    }

    public Game(@Nullable Player whitePlayer, @Nullable Player blackPlayer, TimeControl timeControl) {
        this(whitePlayer, blackPlayer, timeControl, Instant.now());
    }

    private Game(@Nullable Player whitePlayer,
                 @Nullable Player blackPlayer,
                 TimeControl timeControl,
                 Instant startTime) {
        position = Position.startingPosition();
        moves = new ArrayList<>();
        undoes = new ArrayList<>();
        repetitions = new HashMap<>();
        repetitions.put(position.hashCode(), 1);
        status = GameStatus.ONGOING;
        this.startTime = startTime;
        this.timeControl = timeControl;
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
    }

    /**
     * Rebuild a game from a snapshot by replaying its moves from the starting position, so derived
     * state (position, undo history, repetition counts) never has to be persisted.
     */
    public static Game fromSnapshot(GameSnapshot snapshot,
                                    @Nullable Player whitePlayer,
                                    @Nullable Player blackPlayer) {
        Game game = new Game(whitePlayer, blackPlayer, snapshot.timeControl(), snapshot.startTime());
        for (Move move : snapshot.history()) {
            game.recordMove(move);
        }
        game.endTime = snapshot.endTime();
        game.status = snapshot.status();
        game.drawOfferedBy = snapshot.drawOfferedBy();
        return game;
    }

    /**
     * Make a move using chess notation
     */
    public synchronized boolean makeMove(String fromNotation, String toNotation, @Nullable PieceType promotionType) {
        if (status.isGameOver()) {
            return false; // GameService is already over
        }

        try {
            Square from = Square.fromNotation(fromNotation);
            Square to = Square.fromNotation(toNotation);

            // Make moves
            Move move = MoveGenerator.findLegalMove(position, from, to, promotionType)
                    .orElseThrow(() -> new IllegalArgumentException("no such moves"));
            recordMove(move);

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
    public synchronized List<Square> getValidMoves(String fromNotation) {
        Square from = Square.fromNotation(fromNotation);
        return MoveGenerator.legalDestinations(position, from);
    }

    private void recordMove(Move move) {
        undoes.add(position.applyMove(move));
        moves.add(move);
        repetitions.merge(position.hashCode(), 1, Integer::sum);
    }

    public synchronized GameSnapshot getGameSnapshot() {
        Player white = whitePlayer;
        Player black = blackPlayer;
        return new GameSnapshot(
                List.copyOf(moves),
                startTime,
                endTime,
                timeControl,
                status,
                white == null ? null : white.id(),
                black == null ? null : black.id(),
                drawOfferedBy);
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
    public synchronized void resign(Color color) {
        if (status.isGameOver()) {
            return;
        }

        status = color.isWhite() ? GameStatus.RESIGNED_BLACK_WINS : GameStatus.RESIGNED_WHITE_WINS;
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
        if (position.isCheckmate(position.getTurnColor())) {
            status = position.getTurnColor().isWhite() ? GameStatus.CHECKMATE_BLACK_WINS
                    : GameStatus.CHECKMATE_WHITE_WINS;
            endTime = Instant.now();
        } else if (position.isStalemate(position.getTurnColor())) {
            status = GameStatus.STALEMATE;
            endTime = Instant.now();
        } else if (isThreefoldRepetition()) {
            status = GameStatus.DRAW_BY_REPETITION;
            endTime = Instant.now();
        } else if (position.isFiftyMoveRule()) {
            status = GameStatus.DRAW_BY_FIFTY_MOVE_RULE;
            endTime = Instant.now();
        } else if (position.getBoard().isInsufficientMaterial()) {
            status = GameStatus.DRAW_BY_INSUFFICIENT_MATERIAL;
            endTime = Instant.now();
        }
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
        PgnFormatter pgnFormatter = new PgnFormatter();
        StringBuilder sb = new StringBuilder();
        sb.append("[StartTime \"").append(startTime).append("\"]\n");
        sb.append("[EndTime \"").append(endTime).append("\"]\n");
        sb.append("[Round \"").append(getRound()).append("\"]\n");
        sb.append("[White \"").append(pgnName(whitePlayer)).append("\"]\n");
        sb.append("[Black \"").append(pgnName(blackPlayer)).append("\"]\n");
        sb.append("[Result \"").append(status.getSymbol()).append("\"]\n");
        sb.append("[Termination \"").append(status.getDescription()).append("\"]\n");
        sb.append("\n");
        sb.append(pgnFormatter.format(Position.startingPosition(), moves)).append("\n");
        sb.append(status.getSymbol());
        return sb.toString();
    }

    // "?" is PGN's value for an unknown player
    private static String pgnName(@Nullable Player player) {
        return player == null ? "?" : player.displayName();
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
        return timeControl;
    }

    public @Nullable Instant getEndTime() {
        return endTime;
    }

    public @Nullable Player getWhitePlayer() {
        return whitePlayer;
    }

    public void setWhitePlayer(Player whitePlayer) {
        this.whitePlayer = whitePlayer;
    }

    public @Nullable Player getBlackPlayer() {
        return blackPlayer;
    }

    public void setBlackPlayer(Player blackPlayer) {
        this.blackPlayer = blackPlayer;
    }

    public synchronized @Nullable Color getDrawOfferedBy() {
        return drawOfferedBy;
    }

    public synchronized void offerDraw(Color by) {
        drawOfferedBy = by;
    }

    public synchronized void acceptDraw(Color by) {
        if (drawOfferedBy == null)
            return;
        if (drawOfferedBy != by.opposite())
            return;
        agreeDraw();
        drawOfferedBy = null;
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
