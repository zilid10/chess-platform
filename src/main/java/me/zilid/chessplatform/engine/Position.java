package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.pieces.*;

import java.util.*;

public class Position {
    private static final String STARTING_POSITION_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final int KING_FILE = 4;
    private static final int KINGSIDE_ROOK_FILE = 7;
    private static final int QUEENSIDE_ROOK_FILE = 0;
    private static final int BLACK_BACK_RANK = 7;
    private static final int WHITE_BACK_RANK = 0;

    private final Piece[][] board;
    private Piece.Color turnColor;
    private CastlingRights castlingRights;
    private Square enPassantTarget;
    private int halfMoveClock;
    private int fullMoveClock;

    private Position(String fen) {
        board = new Piece[8][8];
        initializeFromFen(fen);
    }

    public static Position startingPosition() {
        return new Position(STARTING_POSITION_FEN);
    }

    public static Position fromFen(String fen) {
        return new Position(fen);
    }

    private void initializeFromFen(String fen) {
        String[] parsedFen = fen.split("\\s+");
        if (parsedFen.length != 6) {
            throw new IllegalArgumentException("Invalid fen: " + fen);
        }
        // i = FEN rank row (rank 8 first), j = file; square (file j, rank 8-i) lives at board[j][7 - i]
        int i = 0, j = 0;
        for (char c : parsedFen[0].toCharArray()) {
            if (j == 8 && c != '/') {
                throw new IllegalArgumentException("Too many files in FEN rank: " + fen);
            }
            switch (c) {
                case 'Q' -> board[j++][7 - i] = new Queen(Piece.Color.WHITE);
                case 'q' -> board[j++][7 - i] = new Queen(Piece.Color.BLACK);
                case 'K' -> board[j++][7 - i] = new King(Piece.Color.WHITE);
                case 'k' -> board[j++][7 - i] = new King(Piece.Color.BLACK);
                case 'R' -> board[j++][7 - i] = new Rook(Piece.Color.WHITE);
                case 'r' -> board[j++][7 - i] = new Rook(Piece.Color.BLACK);
                case 'B' -> board[j++][7 - i] = new Bishop(Piece.Color.WHITE);
                case 'b' -> board[j++][7 - i] = new Bishop(Piece.Color.BLACK);
                case 'N' -> board[j++][7 - i] = new Knight(Piece.Color.WHITE);
                case 'n' -> board[j++][7 - i] = new Knight(Piece.Color.BLACK);
                case 'P' -> board[j++][7 - i] = new Pawn(Piece.Color.WHITE);
                case 'p' -> board[j++][7 - i] = new Pawn(Piece.Color.BLACK);
                case '/' -> {
                    if (j != 8) {
                        throw new IllegalArgumentException("Invalid FEN rank width: " + fen);
                    }
                    if (i >= 7) {
                        throw new IllegalArgumentException("Too many ranks in FEN: " + fen);
                    }
                    i++;
                    j = 0;
                }
                default -> {
                    if (c > '8' || c < '1') {
                        throw new IllegalArgumentException("Invalid fen: " + fen);
                    }
                    j += c - '0';
                }
            }
            if (j > 8) {
                throw new IllegalArgumentException("Too many files in FEN rank: " + fen);
            }
        }
        if (i != 7 || j != 8) {
            throw new IllegalArgumentException("Incomplete board in FEN: " + fen);
        }
        turnColor = Piece.Color.fromSymbol(parsedFen[1]);
        castlingRights = CastlingRights.fromSymbol(parsedFen[2]);
        enPassantTarget = parsedFen[3].equals("-") ? null : Square.fromNotation(parsedFen[3]);
        try {
            halfMoveClock = Integer.parseInt(parsedFen[4]);
            fullMoveClock = Integer.parseInt(parsedFen[5]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid fen: " + fen, e);
        }
    }
    
    public Piece getPiece(Square square) {
        return board[square.x()][square.y()];
    }
    
    public boolean isWhiteTurn() {
        return turnColor.isWhite();
    }

    public Piece.Color getTurnColor() {
        return turnColor;
    }

    public Square getEnPassantTarget() {
        return enPassantTarget;
    }

    public UndoInfo applyMove(Move move) {
        int undoRights = castlingRights.rights();
        Square undoEnPassantTarget = enPassantTarget;
        int undoHalfMoveClock = halfMoveClock;
        int undoFullMoveClock = fullMoveClock;

        Square capturedSquare = null;
        if (move.isCapture()) {
            capturedSquare = move.to();
            if (move.isEnPassant()) {
                int x = capturedSquare.x();
                int y = capturedSquare.y();
                capturedSquare = turnColor.isWhite() ? new Square(x, y - 1) : new Square(x, y + 1);
            }
        }

        boolean successful = makeMove(move.from(), move.to(), move.promotionType());
        if (!successful) {
            throw new IllegalStateException("Cannot apply move " + move + " to " + this);
        }

        return new UndoInfo(capturedSquare, undoRights, undoEnPassantTarget, undoHalfMoveClock, undoFullMoveClock);
    }

    public void undoMove(Move move, UndoInfo undo) {
        Piece.Color capturedColor = turnColor;
        Piece.Color moverColor = turnColor.opposite();
        int toX = move.to().x();
        int toY = move.to().y();
        int fromX = move.from().x();
        int fromY = move.from().y();

        // remove the piece from the destination square and restore the piece to the source square (works for promotion)
        board[toX][toY] = null;
        board[fromX][fromY] = Piece.of(move.pieceType(), moverColor);

        // restore the captured piece
        if (move.isCapture()) {
            int captureX = undo.capturedSquare().x();
            int captureY = undo.capturedSquare().y();
            board[captureX][captureY] = Piece.of(move.captureType(), capturedColor);
        }
        // undo rook movement for castling
        if (move.moveType() == Move.MoveType.CASTLE_KINGSIDE) {
            board[KINGSIDE_ROOK_FILE][fromY] = board[KING_FILE + 1][fromY];
            board[KING_FILE + 1][fromY] = null;
        } else if (move.moveType() == Move.MoveType.CASTLE_QUEENSIDE) {
            board[QUEENSIDE_ROOK_FILE][fromY] = board[KING_FILE - 1][fromY];
            board[KING_FILE - 1][fromY] = null;
        }

        turnColor = moverColor;
        castlingRights = new CastlingRights(undo.rights());
        enPassantTarget = undo.enPassantTarget();
        halfMoveClock = undo.halfMoveClock();
        fullMoveClock = undo.fullMoveClock();
    }

    public boolean makeMove(Square from, Square to, Piece.PieceType promotionType) {
        Piece piece = getPiece(from);
        if (piece == null || piece.getColor() != turnColor) {
            return false;
        }

        List<Square> validMoves = getValidMovesForPiece(from);
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
        if (piece.getColor().isBlack()) {
            fullMoveClock++;
        }

        boolean isEnPassant = isEnPassantMove(from, to);
        boolean isCastling = isCastlingMove(from, to);

        // Make the move
        board[to.x()][to.y()] = piece;
        board[from.x()][from.y()] = null;

        // Handle Pawn Promotion
        if (piece.getType() == Piece.PieceType.PAWN) {
            int rank = piece.getColor().isWhite() ? BLACK_BACK_RANK : WHITE_BACK_RANK;
            if (to.y() == rank) {
                board[to.x()][to.y()] = switch (promotionType) {
                    case Piece.PieceType.QUEEN -> new Queen(piece.getColor());
                    case Piece.PieceType.ROOK ->  new Rook(piece.getColor());
                    case Piece.PieceType.KNIGHT -> new Knight(piece.getColor());
                    case Piece.PieceType.BISHOP -> new Bishop(piece.getColor());
                    default -> throw new IllegalStateException("Invalid promotion type");
                };
            }
        }

        // Handle en passant capture
        if (isEnPassant) {
            int captureY = piece.isWhite() ? to.y() - 1 : to.y() + 1;
            board[to.x()][captureY] = null; // Remove the captured pawn
        }
        
        // Handle castling - move the rook
        if (isCastling) {
            int rookFromX = to.x() > from.x() ? KINGSIDE_ROOK_FILE : QUEENSIDE_ROOK_FILE; // Kingside or queenside
            int rookToX = to.x() > from.x() ? to.x() - 1 : to.x() + 1;
            int y = from.y();

            // move the rook
            Piece rook = board[rookFromX][y];
            board[rookToX][y] = rook;
            board[rookFromX][y] = null;
        }

        // Track the castling rights
        updateCastlingRights(piece, capturedPiece, from, to);

        // Track en passant target; only valid for the single reply to a double push
        if (isPawnMove && from.x() == to.x() && Math.abs(from.y() - to.y()) == 2) {
            enPassantTarget = new Square(from.x(), (from.y() + to.y()) / 2);
        } else {
            enPassantTarget = null;
        }

        turnColor = turnColor.opposite();
        return true;
    }

    public List<Square> getValidMovesForPiece(Square pieceSquare) {
        Piece piece = getPiece(pieceSquare);
        if (piece == null) {
            return List.of();
        }

        List<Square> pseudoLegalMoves = piece.getValidMoves(pieceSquare, board);

        // Try to add en passant moves for pawns
        if (piece.getType() == Piece.PieceType.PAWN) {
            pseudoLegalMoves.addAll(getEnPassantMoves(pieceSquare));
        }

        // Try to add castling moves for king
        if (piece.getType() == Piece.PieceType.KING) {
            pseudoLegalMoves.addAll(getCastlingMoves(pieceSquare));
        }

        List<Square> legalMoves = new ArrayList<>();

        for (Square move : pseudoLegalMoves) {
            // Simulate the move
            boolean isEnPassant = isEnPassantMove(pieceSquare, move);
            boolean isCastling = isCastlingMove(pieceSquare, move);

            Piece capturedPiece = board[move.x()][move.y()];
            Piece enPassantCaptured = null;

            board[move.x()][move.y()] = piece;
            board[pieceSquare.x()][pieceSquare.y()] = null;

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
                rookFromX = move.x() > pieceSquare.x() ? 7 : 0;
                rookToX = move.x() > pieceSquare.x() ? move.x() - 1 : move.x() + 1;
                rookY = pieceSquare.y();
                rookMoved = board[rookFromX][rookY];
                board[rookToX][rookY] = rookMoved;
                board[rookFromX][rookY] = null;
            }

            // Check if this move leaves the king in check
            if (!isInCheck(piece.getColor())) {
                legalMoves.add(move);
            }

            // Undo the move
            board[pieceSquare.x()][pieceSquare.y()] = piece;
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
    private List<Square> getEnPassantMoves(Square pawnSquare) {
        Piece pawn = getPiece(pawnSquare);
        if (enPassantTarget == null) {
            return List.of();
        }
        if (pawn.getControlledSquares(pawnSquare, board).contains(enPassantTarget)) {
            return List.of(enPassantTarget);
        }
        return List.of();
    }

    /**
     * Check if a move is an en passant capture
     */
    public boolean isEnPassantMove(Square from, Square to) {
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
     * Get possible castling moves for a king at the given position
     */
    private List<Square> getCastlingMoves(Square kingSquare) {
        List<Square> castlingMoves = new ArrayList<>();
        Piece piece = getPiece(kingSquare);
        if (piece == null) {
            return castlingMoves;
        }

        Piece.Color color = piece.getColor();
        // Kingside castling
        if (canCastle(color, true)){
            castlingMoves.add(new Square(KING_FILE + 2, kingSquare.y()));
        }

        // Queenside castling
        if (canCastle(color, false)) {
            castlingMoves.add(new Square(KING_FILE - 2, kingSquare.y()));
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
        if (!castlingRights.hasCastlingRight(color, kingside)) {
            return false;
        }
        Piece king = board[KING_FILE][rank];
        boolean notInCheck = !isInCheck(king.getColor());
        boolean noPiecesBetween = kingside
                ? board[5][rank] == null && board[6][rank] == null
                : board[2][rank] == null && board[3][rank] == null && board[1][rank] == null;
        boolean noSquareUnderAttackBetween = kingside
                ? !isSquareUnderAttack(new Square(5, rank), king.getColor()) && !isSquareUnderAttack(new Square(6, rank), king.getColor())
                : !isSquareUnderAttack(new Square(2, rank), king.getColor()) && !isSquareUnderAttack(new Square(3, rank), king.getColor());
        return noPiecesBetween && notInCheck && noSquareUnderAttackBetween;
    }

    /**
     * Check if a move is a castling move
     */
    public boolean isCastlingMove(Square from, Square to) {
        Piece piece = board[from.x()][from.y()];
        if (piece == null || piece.getType() != Piece.PieceType.KING) {
            return false;
        }

        // Castling is a 2-square king move horizontally
        return Math.abs(to.x() - from.x()) == 2 && to.y() == from.y();
    }

    private void updateCastlingRights(Piece piece, Piece capturedPiece, Square from, Square to) {
        if (piece.getType() == Piece.PieceType.KING && piece.getColor().isWhite()) {
            castlingRights = castlingRights.withoutWhite();
        } else if (piece.getType() == Piece.PieceType.KING && piece.getColor().isBlack()) {
            castlingRights = castlingRights.withoutBlack();
        }

        if (piece.getType() == Piece.PieceType.ROOK) {
            updateCastlingRightsByRook(piece.getColor(), from);
        }

        if (capturedPiece != null && capturedPiece.getType() == Piece.PieceType.ROOK) {
            // en-passant can't happen in the corner, so it's safe to pass `to` to the function
            updateCastlingRightsByRook(capturedPiece.getColor(), to);
        }
    }

    private void updateCastlingRightsByRook(Piece.Color color, Square rook) {
        if (color.isWhite() && rook.x() == KINGSIDE_ROOK_FILE && rook.y() == WHITE_BACK_RANK) {
            castlingRights = castlingRights.withoutWhiteKingside();
        } else if (color.isWhite() && rook.x() == QUEENSIDE_ROOK_FILE && rook.y() == WHITE_BACK_RANK) {
            castlingRights = castlingRights.withoutWhiteQueenside();
        } else if (color.isBlack() && rook.x() == KINGSIDE_ROOK_FILE && rook.y() == BLACK_BACK_RANK) {
            castlingRights = castlingRights.withoutBlackKingside();
        } else if (color.isBlack() && rook.x() == QUEENSIDE_ROOK_FILE && rook.y() == BLACK_BACK_RANK) {
            castlingRights = castlingRights.withoutBlackQueenside();
        }
    }

    /**
     * Check if a square is under attack by the opponent
     */
    private boolean isSquareUnderAttack(Square square, Piece.Color color) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.getColor() != color) {
                    Square enemySquare = new Square(x, y);
                    List<Square> controlledSquares = piece.getControlledSquares(enemySquare, board);
                    if (controlledSquares.contains(square)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * check if the current position is checkmate
     */
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
                    Square pieceSquare = new Square(x, y);
                    List<Square> legalMoves = getValidMovesForPiece(pieceSquare);
                    if (!legalMoves.isEmpty()) {
                        return false; // Found a legal move
                    }
                }
            }
        }

        return true; // No legal moves and in check -> checkmate
    }

    /**
     * Check if current position is a draw due to stalemate
     */
    public boolean isStalemate(Piece.Color color) {
        if (isInCheck(color)) {
            return false; // In check, so not stalemate
        }

        // Check if there are any legal moves
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.getColor() == color) {
                    Square pieceSquare = new Square(x, y);
                    List<Square> legalMoves = getValidMovesForPiece(pieceSquare);
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
        Square kingSquare = null;
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.getType() == Piece.PieceType.KING && piece.getColor() == color) {
                    kingSquare = new Square(x, y);
                    break;
                }
            }
            if (kingSquare != null) break;
        }

        if (kingSquare == null) {
            return false; // No king found
        }

        // Check if any enemy piece can attack the king
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y];
                if (piece != null && piece.getColor() != color) {
                    Square enemySquare = new Square(x, y);
                    List<Square> moves = piece.getControlledSquares(enemySquare, board);
                    if (moves.contains(kingSquare)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
    
    /**
     * Check if fifty moves have been made without pawn move or capture
     */
    public boolean isFiftyMoveRule() {
        return halfMoveClock >= 100; // 100 half-moves = 50 full moves
    }

    /**
     * Check if the current position is a draw due to insufficient material
     */
    public boolean isInsufficientMaterial() {
        List<Piece> otherPieces = new ArrayList<>();
        List<Square> bishopSquares = new ArrayList<>();
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
                        bishopSquares.add(new Square(x, y));
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
        if (blackCount == 2 && whiteCount == 2 && bishopSquares.size() == 2) {
            Square b1 = bishopSquares.get(0);
            Square b2 = bishopSquares.get(1);
            return (b1.x() + b1.y()) % 2 == (b2.x() + b2.y()) % 2;
        }

        return false;
    }

    /**
     * Get the fen representation of the current position
     */
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
        fen.append(" ").append(castlingRights.getSymbol());
        fen.append(" ").append(enPassantTarget == null ? "-" : enPassantTarget.toNotation());
        fen.append(" ").append(halfMoveClock);
        fen.append(" ").append(fullMoveClock);
        return fen.toString();
    }

    /**
     * Generate a hash of the current board position
     */
    @Override
    public int hashCode() {
        return getFen().hashCode();
    }

    /**
     * calculate the disambiguation string (when multiple same pieces can move to the same square, requires disambiguation)
     */
    public String getDisambiguation(Square from, Square to) {
        Piece movingPiece = board[from.x()][from.y()];
        if (movingPiece == null || movingPiece.getType() == Piece.PieceType.PAWN || movingPiece.getType() == Piece.PieceType.KING) {
            return "";
        }

        boolean needDisambiguation = false;
        boolean sameFile = false;
        boolean sameRank = false;

        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                // exclude self
                if (x == from.x() && y == from.y()) continue;

                Piece other = board[x][y];

                if (other != null && other.getColor() == movingPiece.getColor() && other.getType() == movingPiece.getType()) {


                    List<Square> moves = getValidMovesForPiece(new Square(x, y));

                    if (moves.contains(to)) {
                        needDisambiguation = true;
                        if (x == from.x()) {
                            sameFile = true;
                        }
                        if (y == from.y()) {
                            sameRank = true;
                        }
                    }
                }
            }
        }

        // 1. If there are both file and rank ambiguity, use the full notation (e.g., d4, e5)
        // 2. If there are file ambiguity, use the rank number to disambiguate (1-8)
        // 3. If there are rank ambiguity, use the file to disambiguate (a-h)
        if (!needDisambiguation) {
            return "";
        }

        if (sameFile && sameRank) {
            return from.toNotation();
        }
        if (sameFile) {
            return String.valueOf(from.toNotation().charAt(1));
        }
        return String.valueOf(from.toNotation().charAt(0));
    }
}
