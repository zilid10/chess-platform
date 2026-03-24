package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.formatter.PgnWriter;
import me.zilid.chessplatform.engine.pieces.Piece;
import me.zilid.chessplatform.model.dto.ActiveGameState;
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
    private volatile Piece.Color drawOfferedBy;

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
                Piece.Color drawOfferedBy) {
        this.engine = engine;
        this.history = history;
        this.positionHistory = positionHistory;
        this.startTime = startTime;
        this.endTime = endTime ;
        this.status = status;
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
        this.drawOfferedBy = drawOfferedBy;
    }

    public synchronized boolean makeMove(String from, String to) {
        return makeMove(from, to, Piece.PieceType.QUEEN);
    }
    /**
     * Make a move using chess notation
     */
    public synchronized boolean makeMove(String from, String to, Piece.PieceType promotionType) {
        if (status.isGameOver()) {
            return false; // GameService is already over
        }

        try {
            Position fromPos = Position.fromNotation(from);
            Position toPos = Position.fromNotation(to);
            Move.MoveType moveType = Move.MoveType.NORMAL;

            // Get piece info before move
            Piece movingPiece = engine.getBoard().getPiece(fromPos);
            if (movingPiece == null) {
                return false;
            }

            Piece capturedPiece = engine.getBoard().getPiece(toPos);
            Piece.PieceType capturedType = capturedPiece != null ? capturedPiece.getType() : null;

            // Check for special moves before making the move
            boolean isEnPassant = isEnPassantMove(fromPos, toPos);
            boolean isCastling = isCastlingMove(fromPos, toPos);
            boolean isKingsideCastle = isCastling && toPos.x() > fromPos.x();
            boolean isPromotion = movingPiece.getType() == Piece.PieceType.PAWN && (
                    (getTurnColor().isWhite() && toPos.y() == 7) || (getTurnColor().isBlack() && toPos.y() == 0));
            if (isCastling) {
                moveType = isKingsideCastle ? Move.MoveType.CASTLE_KINGSIDE : Move.MoveType.CASTLE_QUEENSIDE;
            } else if (isPromotion) {
                moveType = Move.MoveType.PROMOTION;
            } else if (isEnPassant) {
                moveType = Move.MoveType.EN_PASSANT;
                capturedType = Piece.PieceType.PAWN;
            }

            // Attempt the move
            boolean success = engine.makeMove(from, to, promotionType);
            if (!success) {
                return false;
            }

            // Record the move with special move flags
            Move move = new Move(fromPos, toPos, moveType, movingPiece.getType(), capturedType, promotionType);
            history.add(move);
            int boardHash = engine.getBoard().getBoardHash();
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
    public synchronized List<Position> getValidMoves(String position) {
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
    public synchronized void resign(Piece.Color color) {
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
     * Check if a move is an en passant capture
     */
    private synchronized boolean isEnPassantMove(Position from, Position to) {
        return engine.getBoard().isEnPassantMove(from, to);
    }

    /**
     * Check if a move is a castling move
     */
    private synchronized boolean isCastlingMove(Position from, Position to) {
        return engine.getBoard().isCastlingMove(from, to);
    }

    /**
     * Update the game status based on current board state
     */
    private synchronized void updateGameStatus() {
        if (engine.isCheckmate()) {
            status = engine.isWhiteTurn() ?
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
        return positionHistory.getOrDefault(engine.getBoard().getBoardHash(), 0) >= 3;
    }


    public synchronized Piece.Color getTurnColor() {
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
        sb.append(pgnWriter.format(new Board(), history)).append("\n");
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

    public synchronized Piece.Color getDrawOfferedBy() {
        return drawOfferedBy;
    }

    public synchronized boolean offerDraw(Piece.Color by) {
        drawOfferedBy = by;
        return true;
    }

    public synchronized boolean acceptDraw(Piece.Color by) {
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
    public Piece.Color getPlayerColor(UserPrincipal currentUser) {
        if (currentUser.equals(whitePlayer)) {
            return Piece.Color.WHITE;
        }
        if (currentUser.equals(blackPlayer)) {
            return Piece.Color.BLACK;
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
