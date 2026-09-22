package me.zilid.chessplatform.chess.game;

import me.zilid.chessplatform.chess.*;
import me.zilid.chessplatform.chess.format.Fen;
import me.zilid.chessplatform.chess.format.pgn.PgnFormatter;
import me.zilid.chessplatform.model.entity.UserPrincipal;
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
    private volatile @Nullable Instant endTime;
    private volatile GameStatus status;
    private volatile @Nullable UserPrincipal whitePlayer;
    private volatile @Nullable UserPrincipal blackPlayer;
    private volatile @Nullable Color drawOfferedBy;

    public Game() {
        this(null, null);
    }

    public Game(UserPrincipal whitePlayer, UserPrincipal blackPlayer) {
        position = Position.startingPosition();
        moves = new ArrayList<>();
        undoes = new ArrayList<>();
        repetitions = new HashMap<>();
        status = GameStatus.ONGOING;
        startTime = Instant.now();
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
    }

    public Game(Position position,
                List<Move> moves,
                List<UndoInfo> undoes,
                Map<Integer, Integer> repetitions,
                Instant startTime,
                Instant endTime,
                GameStatus status,
                UserPrincipal whitePlayer,
                UserPrincipal blackPlayer,
                Color drawOfferedBy) {
        this.position = position;
        this.moves = moves;
        this.undoes = undoes;
        this.repetitions = repetitions;
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
            position.applyMove(move);

            // Record the move with special move flags
            moves.add(move);
            int positionHash = position.hashCode();
            repetitions.merge(positionHash, 1, Integer::sum);

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

    public synchronized GameSnapshot getGameSnapshot() {
        return new GameSnapshot(
                getFen(),
                List.copyOf(moves),
                Map.copyOf(repetitions),
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
        if (position.isCheckmate(position.getTurnColor())) {
            status = position.getTurnColor().isWhite() ?
                    GameStatus.CHECKMATE_BLACK_WINS :
                    GameStatus.CHECKMATE_WHITE_WINS;
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
        if (drawOfferedBy == null) return;
        if (drawOfferedBy != by.opposite()) return;
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
