package me.zilid.chessplatform.chess.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.Move;
import me.zilid.chessplatform.chess.MoveGenerator;
import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.Position;
import me.zilid.chessplatform.chess.Square;
import me.zilid.chessplatform.chess.UndoInfo;
import me.zilid.chessplatform.chess.format.Fen;
import me.zilid.chessplatform.chess.format.pgn.PgnFormatter;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import org.jspecify.annotations.Nullable;

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
    private volatile @Nullable UserPrincipal whitePlayer;
    private volatile @Nullable UserPrincipal blackPlayer;
    private volatile @Nullable Color drawOfferedBy;

    public Game() {
        this(null, null);
    }

    public Game(@Nullable UserPrincipal whitePlayer, @Nullable UserPrincipal blackPlayer) {
        this(whitePlayer, blackPlayer, TimeControl.RAPID);
    }

    public Game(@Nullable UserPrincipal whitePlayer, @Nullable UserPrincipal blackPlayer, TimeControl timeControl) {
        this(whitePlayer, blackPlayer, timeControl, Instant.now());
    }

    private Game(@Nullable UserPrincipal whitePlayer,
            @Nullable UserPrincipal blackPlayer,
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
            @Nullable UserPrincipal whitePlayer,
            @Nullable UserPrincipal blackPlayer) {
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
        UserPrincipal white = whitePlayer;
        UserPrincipal black = blackPlayer;
        return new GameSnapshot(
                List.copyOf(moves),
                startTime,
                endTime,
                timeControl,
                status,
                white == null ? null : white.getId(),
                black == null ? null : black.getId(),
                drawOfferedBy);
    }

    /**
     * Get the current board state of the game
     */
    public synchronized String getFen() {
        return Fen.format(position);
    }

    public synchronized String getLastMoveFrom() {
        return moves.isEmpty() ? null : moves.getLast().from().toNotation();
    }

    public synchronized String getLastMoveTo() {
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
        sb.append("[White \"").append(whitePlayer.getUsername()).append("\"]\n");
        sb.append("[Black \"").append(blackPlayer.getUsername()).append("\"]\n");
        sb.append("[Result \"").append(status.getSymbol()).append("\"]\n");
        sb.append("[Termination \"").append(status.getDescription()).append("\"]\n");
        sb.append("\n");
        sb.append(pgnFormatter.format(Position.startingPosition(), moves)).append("\n");
        sb.append(status.getSymbol());
        return sb.toString();
    }

    /**
     * Check if now is the turn of the given user
     */
    public synchronized boolean isUserTurn(UserPrincipal currentUser) {
        return switch (position.getTurnColor()) {
            case WHITE -> currentUser.equals(whitePlayer);
            case BLACK -> currentUser.equals(blackPlayer);
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

    public @Nullable UserPrincipal getWhitePlayer() {
        return whitePlayer;
    }

    public void setWhitePlayer(UserPrincipal whitePlayer) {
        this.whitePlayer = whitePlayer;
    }

    public @Nullable UserPrincipal getBlackPlayer() {
        return blackPlayer;
    }

    public void setBlackPlayer(UserPrincipal blackPlayer) {
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
     * Get the color in this game of the given user, spectator will get a null
     */
    public @Nullable Color getPlayerColor(UserPrincipal currentUser) {
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
