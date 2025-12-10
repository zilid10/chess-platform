package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.pieces.*;

import java.util.*;

public class Board {
    private final Piece[][] board;
    private static final int KING_FILE = 4;
    private static final int KINGSIDE_ROOK_FILE = 7;
    private static final int QUEENSIDE_ROOK_FILE = 0;
    private static final int BLACK_BACK_RANK = 7;
    private static final int WHITE_BACK_RANK = 0;
    private Piece.Color turnColor;
    private Position lastMoveFrom;
    private Position lastMoveTo;
    private int halfMoveClock; // For fifty-move rule
    private final Map<Integer, Integer> positionHistory; // For threefold repetition detection

    public Board() {
        board = new Piece[8][8];
        turnColor = Piece.Color.WHITE;
        lastMoveFrom = null;
        lastMoveTo = null;
        halfMoveClock = 0;
        positionHistory = new HashMap<>();
        initializeBoard();
        positionHistory.put(getBoardHash(), 1); // Add initial position
    }
    
    private void initializeBoard() {
        // Initialize pawns
        for (int i = 0; i < 8; i++) {
            board[i][1] = new Pawn(Piece.Color.WHITE);
            board[i][6] = new Pawn(Piece.Color.BLACK);
        }
        
        // Initialize rooks
        board[0][0] = new Rook(Piece.Color.WHITE);
        board[7][0] = new Rook(Piece.Color.WHITE);
        board[0][7] = new Rook(Piece.Color.BLACK);
        board[7][7] = new Rook(Piece.Color.BLACK);
        
        // Initialize knights
        board[1][0] = new Knight(Piece.Color.WHITE);
        board[6][0] = new Knight(Piece.Color.WHITE);
        board[1][7] = new Knight(Piece.Color.BLACK);
        board[6][7] = new Knight(Piece.Color.BLACK);
        
        // Initialize bishops
        board[2][0] = new Bishop(Piece.Color.WHITE);
        board[5][0] = new Bishop(Piece.Color.WHITE);
        board[2][7] = new Bishop(Piece.Color.BLACK);
        board[5][7] = new Bishop(Piece.Color.BLACK);
        
        // Initialize queens
        board[3][0] = new Queen(Piece.Color.WHITE);
        board[3][7] = new Queen(Piece.Color.BLACK);
        
        // Initialize kings
        board[4][0] = new King(Piece.Color.WHITE);
        board[4][7] = new King(Piece.Color.BLACK);
    }
    
    public Piece getPiece(Position position) {
        return board[position.x()][position.y()];
    }
    
    public boolean isWhiteTurn() {
        return turnColor == Piece.Color.WHITE;
    }

    public Piece.Color getTurnColor() {
        return turnColor;
    }

    public Position getLastMoveFrom() {
        return lastMoveFrom;
    }
    
    public Position getLastMoveTo() {
        return lastMoveTo;
    }

    public boolean makeMove(Position from, Position to) {
        Piece piece = getPiece(from);
        if (piece == null || piece.getColor() != turnColor) {
            return false;
        }

        List<Position> validMoves = getValidMovesForPiece(from);
        if (!validMoves.contains(to)) {
            return false;
        }

        // Track for fifty-move rule: reset if pawn move or capture
        Piece capturedPiece = getPiece(to);
        boolean isPawnMove = piece.getType() == Piece.PieceType.PAWN;
        boolean isCapture = capturedPiece != null || isEnPassantMove(from, to);
        
        if (isPawnMove || isCapture) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }

        // Make the move
        board[to.x()][to.y()] = piece;
        board[from.x()][from.y()] = null;
        piece.setMoved();

        // Handle en passant capture
        if (isEnPassantMove(from, to)) {
            int captureY = piece.isWhite() ? to.y() - 1 : to.y() + 1;
            board[to.x()][captureY] = null; // Remove the captured pawn
        }
        
        // Handle castling - move the rook
        if (isCastlingMove(from, to)) {
            int rookFromX = to.x() > from.x() ? KINGSIDE_ROOK_FILE : QUEENSIDE_ROOK_FILE; // Kingside or queenside
            int rookToX = to.x() > from.x() ? to.x() - 1 : to.x() + 1;
            int y = from.y();

            // move the rook
            Piece rook = board[rookFromX][y];
            board[rookToX][y] = rook;
            board[rookFromX][y] = null;
            rook.setMoved();
        }
        
        // Track last move for en passant
        lastMoveFrom = from;
        lastMoveTo = to;

        turnColor = turnColor.opposite();
        
        // Add current position to history for threefold repetition
        int boardHash = getBoardHash();
        positionHistory.put(boardHash, positionHistory.getOrDefault(boardHash, 0));
        
        return true;
    }

    public List<Position> getValidMovesForPiece(Position piecePosition) {
        Piece piece = getPiece(piecePosition);
        if (piece == null) {
            return List.of();
        }

        List<Position> pseudoLegalMoves = piece.getValidMoves(piecePosition, board);

        // Try to add en passant moves for pawns
        if (piece.getType() == Piece.PieceType.PAWN) {
            pseudoLegalMoves.addAll(getEnPassantMoves(piecePosition));
        }

        // Try to add castling moves for king
        if (piece.getType() == Piece.PieceType.KING) {
            pseudoLegalMoves.addAll(getCastlingMoves(piecePosition));
        }

        List<Position> legalMoves = new ArrayList<>();

        for (Position move : pseudoLegalMoves) {
            // Simulate the move
            boolean isEnPassant = isEnPassantMove(piecePosition, move);
            boolean isCastling = isCastlingMove(piecePosition, move);

            Piece capturedPiece = board[move.x()][move.y()];
            Piece enPassantCaptured = null;

            board[move.x()][move.y()] = piece;
            board[piecePosition.x()][piecePosition.y()] = null;

            // Handle en passant in simulation
            if (isEnPassant) {
                int captureY = piece.isWhite() ? move.y() - 1 : move.y() + 1;
                enPassantCaptured = board[move.x()][captureY];
                board[move.x()][captureY] = null;
            }

            // Handle castling in simulation
            Piece rookMoved = null;
            int rookFromX = 0, rookToX = 0, rookY = 0;
            if (isCastling) {
                rookFromX = move.x() > piecePosition.x() ? 7 : 0;
                rookToX = move.x() > piecePosition.x() ? move.x() - 1 : move.x() + 1;
                rookY = piecePosition.y();
                rookMoved = board[rookFromX][rookY];
                board[rookToX][rookY] = rookMoved;
                board[rookFromX][rookY] = null;
            }

            // Check if this move leaves the king in check
            if (!isInCheck(piece.getColor())) {
                legalMoves.add(move);
            }

            // Undo the move
            board[piecePosition.x()][piecePosition.y()] = piece;
            board[move.x()][move.y()] = capturedPiece;
            if (isEnPassant && enPassantCaptured != null) {
                int captureY = piece.isWhite() ? move.y() - 1 : move.y() + 1;
                board[move.x()][captureY] = enPassantCaptured;
            }
            if (isCastling && rookMoved != null) {
                board[rookFromX][rookY] = rookMoved;
                board[rookToX][rookY] = null;
            }
        }

        return legalMoves;
    }

    /**
     * Get possible en passant moves for a pawn at the given position
     */
    private List<Position> getEnPassantMoves(Position pawnPosition) {
        Piece pawn = getPiece(pawnPosition);
        if (getEnPassantPosition().isEmpty()) {
            return List.of();
        }
        Position position = getEnPassantPosition().get();
        if (pawn.getControlledSquares(pawnPosition, board).contains(position)) {
            return List.of(position);
        }
        return List.of();
    }

    /**
     * Get possible castling moves for a king at the given position
     */
    private List<Position> getCastlingMoves(Position kingPosition) {
        List<Position> castlingMoves = new ArrayList<>();
        Piece piece = getPiece(kingPosition);
        if (piece == null) {
            return castlingMoves;
        }

        Piece.Color color = piece.getColor();
        // Kingside castling
        if (canCastle(color, true)){
            castlingMoves.add(new Position(6, kingPosition.y()));
        }

        // Queenside castling
        if (canCastle(color, false)) {
            castlingMoves.add(new Position(2, kingPosition.y()));
        }

        return castlingMoves;
    }

    /**
     * Check if the king can castle.
     */
    private boolean canCastle(Piece.Color color, boolean kingside) {
        int rank = switch (color) {
            case Piece.Color.WHITE -> WHITE_BACK_RANK;
            case Piece.Color.BLACK -> BLACK_BACK_RANK;
        };
        if (!hasCastlingRight(color, kingside)) {
            return false;
        }
        Piece king = board[KING_FILE][rank];
        boolean notInCheck = !isInCheck(king.getColor());
        boolean noPiecesBetween = kingside
                ? board[5][rank] == null && board[6][rank] == null
                : board[2][rank] == null && board[3][rank] == null && board[1][rank] == null ;
        boolean noSquareUnderAttackBetween = kingside
                ? !isSquareUnderAttack(new Position(5, rank), king.getColor()) && !isSquareUnderAttack(new Position(6, rank), king.getColor())
                : !isSquareUnderAttack(new Position(2, rank), king.getColor()) && !isSquareUnderAttack(new Position(3, rank), king.getColor());
        return noPiecesBetween && notInCheck && noSquareUnderAttackBetween;
    }

    /**
     * Check if a square is under attack by the opponent
     */
    private boolean isSquareUnderAttack(Position square, Piece.Color color) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.getColor() != color) {
                    Position enemyPosition = new Position(x, y);
                    List<Position> controlledSquares = piece.getControlledSquares(enemyPosition, board);
                    if (controlledSquares.contains(square)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isCheckmate(Piece.Color color) {
        // Not in check, so not checkmate
        if (!isInCheck(color)) {
            return false;
        }

        // Check if there are any legal moves
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.getColor() == color) {
                    Position piecePosition = new Position(x, y);
                    List<Position> legalMoves = getValidMovesForPiece(piecePosition);
                    if (!legalMoves.isEmpty()) {
                        return false; // Found a legal move
                    }
                }
            }
        }

        return true; // No legal moves and in check -> checkmate
    }

    public boolean isStalemate(Piece.Color color) {
        if (isInCheck(color)) {
            return false; // In check, so not stalemate
        }

        // Check if there are any legal moves
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.getColor() == color) {
                    Position piecePosition = new Position(x, y);
                    List<Position> legalMoves = getValidMovesForPiece(piecePosition);
                    if (!legalMoves.isEmpty()) {
                        return false; // Found a legal move
                    }
                }
            }
        }

        return true; // No legal moves and not in check -> stalemate
    }

    public boolean isInCheck(Piece.Color color) {
        // Find the king
        Position kingPosition = null;
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.getType() == Piece.PieceType.KING && piece.getColor() == color) {
                    kingPosition = new Position(x, y);
                    break;
                }
            }
            if (kingPosition != null) break;
        }

        if (kingPosition == null) {
            return false; // No king found
        }

        // Check if any enemy piece can attack the king
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.getColor() != color) {
                    Position enemyPosition = new Position(x, y);
                    List<Position> moves = piece.getControlledSquares(enemyPosition, board);
                    if (moves.contains(kingPosition)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if a move is an en passant capture
     */
    private boolean isEnPassantMove(Position from, Position to) {
        Piece piece = board[from.x()][from.y()];
        if (piece == null || piece.getType() != Piece.PieceType.PAWN) {
            return false;
        }
        
        // En passant is a diagonal move to an empty square
        if (board[to.x()][to.y()] == null && from.x() != to.x()) {
            return getEnPassantMoves(from).contains(to);
        }
        
        return false;
    }
    
    /**
     * Check if a move is a castling move
     */
    private boolean isCastlingMove(Position from, Position to) {
        Piece piece = board[from.x()][from.y()];
        if (piece == null || piece.getType() != Piece.PieceType.KING) {
            return false;
        }
        
        // Castling is a 2-square king move horizontally
        return Math.abs(to.x() - from.x()) == 2 && to.y() == from.y();
    }

    public String getFen() {
        StringBuilder fen = new StringBuilder();
        for (int y = 7; y >= 0; y--) {
            int count = 0;
            for (int x = 0; x < 8; x++) {
                Piece piece = board[x][y];
                if (piece != null && count != 0) {
                    fen.append(count);
                    fen.append(piece.getSymbol());
                    count = 0;
                } else if (piece != null) {
                    fen.append(piece.getSymbol());
                } else {
                    count++;
                }
                if (x == 7 && count != 0) {
                    fen.append(count);
                }
            }
            if (y != 0) {
                fen.append("/");
            }
        }
        fen.append(" ").append(turnColor.getSymbol());
        fen.append(" ").append(getCastleRights());
        fen.append(" ").append(getEnPassantPosition().map(Position::toNotation).orElse("-"));
        fen.append(" ").append(halfMoveClock);
        fen.append(" ").append(halfMoveClock / 2 + 1);
        return fen.toString();
    }

    private Optional<Position> getEnPassantPosition() {
        Piece lastMovedPiece = getPiece(lastMoveTo);

        if (lastMovedPiece != null && lastMovedPiece.getType() == Piece.PieceType.PAWN && Math.abs(lastMoveTo.y() - lastMoveFrom.y()) == 2) {
            return Optional.of(new Position(lastMoveTo.x(), (lastMoveFrom.y() + lastMoveTo.y()) / 2));
        }
        return Optional.empty();
    }

    private boolean hasCastlingRight(Piece.Color color, boolean kingside) {
        int rank = switch (color) {
            case Piece.Color.WHITE -> WHITE_BACK_RANK;
            case Piece.Color.BLACK -> BLACK_BACK_RANK;
        };
        Piece king = board[KING_FILE][rank];
        Piece rook = kingside ? board[KINGSIDE_ROOK_FILE][rank] : board[QUEENSIDE_ROOK_FILE][rank];
        return king != null && rook != null &&
                king.getType() == Piece.PieceType.KING && rook.getType() == Piece.PieceType.ROOK &&
                king.getColor() == rook.getColor() &&
                !king.hasMoved() && !rook.hasMoved();
    }

    private String getCastleRights() {
        StringBuilder sb = new StringBuilder();
        if (hasCastlingRight(Piece.Color.WHITE, true)) sb.append("K");
        if (hasCastlingRight(Piece.Color.WHITE, false)) sb.append("Q");
        if (hasCastlingRight(Piece.Color.BLACK, true)) sb.append("k");
        if (hasCastlingRight(Piece.Color.BLACK, false)) sb.append("q");

        if (sb.isEmpty()) sb.append("-");
        return sb.toString();
    }
    
    /**
     * Generate a hash of the current board position for threefold repetition detection
     */
    private int getBoardHash() {
        StringBuilder boardRepresentation = new StringBuilder();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                Piece piece = board[x][y];
                boardRepresentation.append(piece == null ? "." : piece.getSymbol());
            }
        }
        boardRepresentation.append(turnColor.getSymbol());
        return boardRepresentation.toString().hashCode();
    }
    
    /**
     * Check if the current position has occurred three times (threefold repetition)
     */
    public boolean isThreefoldRepetition() {
        return positionHistory.getOrDefault(getBoardHash(), 0) >= 3;
    }
    
    /**
     * Check if fifty moves have been made without pawn move or capture
     */
    public boolean isFiftyMoveRule() {
        return halfMoveClock >= 100; // 100 half-moves = 50 full moves
    }


    public boolean isInsufficientMaterial() {
        List<Piece> otherPieces = new ArrayList<>();
        List<Position> bishopPositions = new ArrayList<>();
        int whiteCount = 0, blackCount = 0;
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null) {
                    if (piece.isWhite()) {
                        whiteCount++;
                    } else {
                        blackCount++;
                    }
                    if (piece.getType() == Piece.PieceType.BISHOP) {
                        bishopPositions.add(new Position(x, y));
                    }
                    if (piece.getType() != Piece.PieceType.KING) {
                        otherPieces.add(piece);
                    }
                }
            }
        }

        // King vs King
        if (whiteCount == 1 && blackCount == 1) {
            return true;
        }

        // King and Bishop vs King or King and Knight vs King
        if (otherPieces.size() == 1 && (otherPieces.getFirst().getType() == Piece.PieceType.KNIGHT || otherPieces.getFirst().getType() == Piece.PieceType.BISHOP)) {
            return true;
        }

        // King and Bishop vs King and Bishop (same color bishop)
        if (blackCount == 2 && whiteCount == 2 && bishopPositions.size() == 2) {
            Position b1 = bishopPositions.get(0);
            Position b2 = bishopPositions.get(1);
            return (b1.x() + b1.y()) % 2 == (b2.x() + b2.y()) % 2;
        }

        return false;
    }

}
