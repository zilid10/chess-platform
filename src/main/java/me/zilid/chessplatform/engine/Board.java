package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.pieces.*;

import java.util.*;

public class Board {
    private final Piece[][] board;
    private boolean whiteTurn;
    private Position lastMoveFrom;
    private Position lastMoveTo;
    private int halfMoveClock; // For fifty-move rule
    private final Map<Integer, Integer> positionHistory; // For threefold repetition
    
    public Board() {
        board = new Piece[8][8];
        whiteTurn = true;
        lastMoveFrom = null;
        lastMoveTo = null;
        halfMoveClock = 0;
        positionHistory = new HashMap<>();
        initializeBoard();
        positionHistory.put(getBoardHash(), 0); // Add initial position
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
        return whiteTurn;
    }
    
    public Position getLastMoveFrom() {
        return lastMoveFrom;
    }
    
    public Position getLastMoveTo() {
        return lastMoveTo;
    }

    public boolean makeMove(Position from, Position to) {
        Piece piece = getPiece(from);

        if (piece == null) {
            return false;
        }

        if (piece.isWhite() != whiteTurn) {
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
        
        // Handle castling - move the king and the rook
        if (isCastlingMove(from, to)) {
            int rookFromX = to.x() > from.x() ? 7 : 0; // Kingside or queenside
            int rookToX = to.x() > from.x() ? to.x() - 1 : to.x() + 1;
            int y = from.y();
            
            Piece rook = board[rookFromX][y];
            board[rookToX][y] = rook;
            board[rookFromX][y] = null;
            if (rook != null) {
                rook.setMoved();
            }
        }
        
        // Track last move for en passant
        lastMoveFrom = from;
        lastMoveTo = to;
        
        whiteTurn = !whiteTurn;
        
        // Add position to history for threefold repetition
        int boardHash = getBoardHash();
        positionHistory.put(boardHash, positionHistory.getOrDefault(boardHash, 0));
        
        return true;
    }

    public List<Position> getValidMovesForPiece(Position piecePosition) {
        Piece piece = getPiece(piecePosition);
        if (piece == null) {
            return new ArrayList<>();
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
            if (!isInCheck(piece.isWhite())) {
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

    public boolean isInCheck(boolean isWhite) {
        // Find the king
        Position kingPosition = null;
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.getType() == Piece.PieceType.KING && piece.isWhite() == isWhite) {
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
                if (piece != null && piece.isWhite() != isWhite) {
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
    
    public boolean isCheckmate(boolean isWhite) {
        // Not in check, so not checkmate
        if (!isInCheck(isWhite)) {
            return false;
        }
        
        // Check if there are any legal moves
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.isWhite() == isWhite) {
                    Position piecePosition = new Position(x, y);
                    List<Position> legalMoves = getValidMovesForPiece(piecePosition);
                    if (!legalMoves.isEmpty()) {
                        return false; // Found a legal move
                    }
                }
            }
        }
        
        return true; // No legal moves and in check = checkmate
    }
    
    public boolean isStalemate(boolean isWhite) {
        if (isInCheck(isWhite)) {
            return false; // In check, so not stalemate
        }
        
        // Check if there are any legal moves
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.isWhite() == isWhite) {
                    Position piecePosition = new Position(x, y);
                    List<Position> legalMoves = getValidMovesForPiece(piecePosition);
                    if (!legalMoves.isEmpty()) {
                        return false; // Found a legal move
                    }
                }
            }
        }
        
        return true; // No legal moves and not in check = stalemate
    }
    
    /**
     * Get possible en passant moves for a pawn at the given position
     */
    private List<Position> getEnPassantMoves(Position pawnPosition) {
        List<Position> enPassantMoves = new ArrayList<>();
        Piece pawn = getPiece(pawnPosition);
        if (pawn == null || pawn.getType() != Piece.PieceType.PAWN) {
            return enPassantMoves;
        }
        
        if (lastMoveFrom == null || lastMoveTo == null) {
            return enPassantMoves;
        }
        
        Piece lastMovedPiece = getPiece(lastMoveTo);
        if (lastMovedPiece == null || lastMovedPiece.getType() != Piece.PieceType.PAWN) {
            return enPassantMoves;
        }
        
        // Check if the last move was a two-square pawn advance
        int moveDistance = Math.abs(lastMoveTo.y() - lastMoveFrom.y());
        if (moveDistance != 2) {
            return enPassantMoves;
        }
        
        // Check if pawns are adjacent and on the correct rank
        int expectedRank = pawn.isWhite() ? 4 : 3; // 5th rank for white, 4th rank for black
        if (pawnPosition.y() != expectedRank) {
            return enPassantMoves;
        }
        
        // Check if the enemy pawn is adjacent
        if (Math.abs(pawnPosition.x() - lastMoveTo.x()) == 1 && pawnPosition.y() == lastMoveTo.y()) {
            int direction = pawn.isWhite() ? 1 : -1;
            Position enPassantSquare = new Position(lastMoveTo.x(), pawnPosition.y() + direction);
            enPassantMoves.add(enPassantSquare);
        }
        
        return enPassantMoves;
    }
    
    /**
     * Get possible castling moves for a king at the given position
     */
    private List<Position> getCastlingMoves(Position kingPosition) {
        List<Position> castlingMoves = new ArrayList<>();
        int y = kingPosition.y();

        // Kingside castling
        if (canCastle(y, true)){
            castlingMoves.add(new Position(6, y));
        }
        
        // Queenside castling
        if (canCastle(y, false)) {
            castlingMoves.add(new Position(2, y));
        }
        
        return castlingMoves;
    }

    /**
     * Check if the king can castle.
     */
    private boolean canCastle(int rank, boolean kingside) {
        if (rank != 0 && rank != 7) {
            return false;
        }
        Piece king = board[4][rank];
        Piece rook = kingside ? board[7][rank] : board[0][rank];
        if (king == null || rook == null) {
            return false;
        }
        boolean sameColor = rook.isWhite() == king.isWhite();
        boolean rookNotMoved = rook.getType() == Piece.PieceType.ROOK && !rook.hasMoved();
        boolean kingNotMoved = king.getType() == Piece.PieceType.KING && !king.hasMoved();
        boolean noPiecesBetween = kingside
                ? board[5][rank] == null && board[6][rank] == null
                : board[2][rank] == null && board[3][rank] == null;
        boolean notInCheck = !isInCheck(king.isWhite());
        boolean noSquareUnderAttackBetween = kingside
                ? !isSquareUnderAttack(new Position(5, rank), king.isWhite()) && !isSquareUnderAttack(new Position(6, rank), king.isWhite())
                : !isSquareUnderAttack(new Position(2, rank), king.isWhite()) && !isSquareUnderAttack(new Position(3, rank), king.isWhite());
        return sameColor && rookNotMoved && kingNotMoved && noPiecesBetween && notInCheck && noSquareUnderAttackBetween;
    }
    
    /**
     * Check if a square is under attack by the opponent
     */
    private boolean isSquareUnderAttack(Position square, boolean byWhite) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.isWhite() != byWhite) {
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
        boardRepresentation.append(whiteTurn ? "W" : "B");
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
