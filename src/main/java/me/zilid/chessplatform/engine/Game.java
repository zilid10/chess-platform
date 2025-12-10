package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.pieces.Piece;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a complete chess game with history and metadata
 */
public class Game {
    private final ChessEngine engine;
    private final MoveHistory history;
    private GameStatus status;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    private String whitePlayer;
    private String blackPlayer;
    
    public Game() {
        this("White", "Black");
    }
    
    public Game(String whitePlayer, String blackPlayer) {
        this.engine = new ChessEngine();
        this.history = new MoveHistory();
        this.status = GameStatus.ONGOING;
        this.startTime = LocalDateTime.now();
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
    }

    /**
     * Make a move using chess notation
     */
    public boolean makeMove(String from, String to) {
        if (status.isGameOver()) {
            return false; // GameService is already over
        }

        try {
            Position fromPos = Position.fromNotation(from);
            Position toPos = Position.fromNotation(to);

            String disambiguation = engine.getBoard().getDisambiguation(fromPos, toPos);

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
            if (isEnPassant) {
                capturedType = Piece.PieceType.PAWN;
            }

            // Attempt the move
            boolean success = engine.makeMove(from, to);
            if (!success) {
                return false;
            }

            // Check game state after move
            boolean isCheck = engine.isInCheck();
            boolean isCheckmate = engine.isCheckmate();

            // Record the move with special move flags
            Move move = new Move(fromPos, toPos, movingPiece.getType(),
                                capturedType, isCheck, isCheckmate,
                                isEnPassant, isCastling, isKingsideCastle, disambiguation);
            history.addMove(move);

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
    public List<Position> getValidMoves(String position) {
        return engine.getValidMoves(position);
    }

    /**
     * Get the current board state of the game
     */
    public String getFen() {
        return engine.getFen();
    }

    /**
     * Resign the game for the current player
     */
    public void resign(Piece.Color color) {
        if (status.isGameOver()) {
            return;
        }

        status = color.isWhite() ?
                GameStatus.RESIGNED_BLACK_WINS :
                GameStatus.RESIGNED_WHITE_WINS;
        endTime = LocalDateTime.now();
    }

    /**
     * Offer/accept a draw
     */
    public void agreeDraw() {
        if (status.isGameOver()) {
            return;
        }

        status = GameStatus.DRAW_BY_AGREEMENT;
        endTime = LocalDateTime.now();
    }

    /**
     * Get the game status
     */
    public GameStatus getStatus() {
        return status;
    }

    /**
     * Check if a move is an en passant capture
     */
    private boolean isEnPassantMove(Position from, Position to) {
        return engine.getBoard().isEnPassantMove(from, to);
    }

    /**
     * Check if a move is a castling move
     */
    private boolean isCastlingMove(Position from, Position to) {
        return engine.getBoard().isCastlingMove(from, to);
    }

    /**
     * Update the game status based on current board state
     */
    private void updateGameStatus() {
        if (engine.isCheckmate()) {
            status = engine.isWhiteTurn() ?
                    GameStatus.CHECKMATE_BLACK_WINS :
                    GameStatus.CHECKMATE_WHITE_WINS;
            endTime = LocalDateTime.now();
        } else if (engine.isStalemate()) {
            status = GameStatus.STALEMATE;
            endTime = LocalDateTime.now();
        } else if (engine.isThreefoldRepetition()) {
            status = GameStatus.DRAW_BY_REPETITION;
            endTime = LocalDateTime.now();
        } else if (engine.isFiftyMoveRule()) {
            status = GameStatus.DRAW_BY_FIFTY_MOVE_RULE;
            endTime = LocalDateTime.now();
        } else if (engine.isInsufficientMaterial()) {
            status = GameStatus.DRAW_BY_INSUFFICIENT_MATERIAL;
            endTime = LocalDateTime.now();
        }
    }

    /**
     * Get the move history
     */
    public MoveHistory getHistory() {
        return history;
    }

    /**
     * Get the chess engine
     */
    public ChessEngine getEngine() {
        return engine;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public String getWhitePlayer() {
        return whitePlayer;
    }
    
    public void setWhitePlayer(String whitePlayer) {
        this.whitePlayer = whitePlayer;
    }
    
    public String getBlackPlayer() {
        return blackPlayer;
    }
    
    public void setBlackPlayer(String blackPlayer) {
        this.blackPlayer = blackPlayer;
    }
    
    public boolean isGameOver() {
        return status.isGameOver();
    }

    public Piece.Color getTurnColor() {
        return engine.getTurnColor();
    }

    public String getNotation() {
        if (!isGameOver()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[StartTime \"").append(startTime).append("\"]\n");
        sb.append("[EndTime \"").append(endTime).append("\"]\n");
        sb.append("[Round \"").append(history.getRounds()).append("\"]\n");
        sb.append("[White \"").append(whitePlayer).append("\"]\n");
        sb.append("[Black \"").append(blackPlayer).append("\"]\n");
        sb.append("[Result \"").append(status.getSymbol()).append("\"]\n");
        sb.append("[Termination\"").append(status.getDescription()).append("\"]\n");
        sb.append("\n");
        sb.append(history.getNotation()).append("\n");
        sb.append(status.getSymbol());
        return sb.toString();
    }
}
