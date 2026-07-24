package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.formatter.Fen;

import java.util.ArrayList;
import java.util.List;

public class Position {
    private List<PieceType> PROMOTION_CHOICES = List.of(PieceType.QUEEN, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK);
    private static final int KING_FILE = 4;
    private static final int KINGSIDE_ROOK_FILE = 7;
    private static final int QUEENSIDE_ROOK_FILE = 0;
    private static final int BLACK_BACK_RANK = 7;
    private static final int WHITE_BACK_RANK = 0;

    private final Board board;
    private Color turnColor;
    private CastlingRights castlingRights;
    private Square enPassantTarget;
    private int halfMoveClock;
    private int fullMoveClock;

    public Position(Board board, Color turnColor, CastlingRights castlingRights, Square enPassantTarget, int halfMoveClock, int fullMoveClock) {
        this.board = board;
        this.turnColor = turnColor;
        this.castlingRights = castlingRights;
        this.enPassantTarget = enPassantTarget;
        this.halfMoveClock = halfMoveClock;
        this.fullMoveClock = fullMoveClock;
    }

    public static Position startingPosition() {
        return new Position(Board.initial(), Color.WHITE, CastlingRights.ALL, null, 0, 1);
    }

    public Board getBoard() {
        return board;
    }

    public Piece getPieceAt(Square square) {
        return board.pieceAt(square);
    }

    public Color getTurnColor() {
        return turnColor;
    }

    public Square getEnPassantTarget() {
        return enPassantTarget;
    }

    public CastlingRights getCastlingRights() {
        return castlingRights;
    }

    public int getHalfMoveClock() {
        return halfMoveClock;
    }

    public int getFullMoveClock() {
        return fullMoveClock;
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
                int file = capturedSquare.file();
                int rank = capturedSquare.rank();
                capturedSquare = turnColor.isWhite() ? Square.of(file, rank - 1) : Square.of(file, rank + 1);
            }
        }

        boolean successful = makeMove(move.from(), move.to(), move.promotionType());
        if (!successful) {
            throw new IllegalStateException("Cannot apply move " + move + " to " + this);
        }

        return new UndoInfo(capturedSquare, undoRights, undoEnPassantTarget, undoHalfMoveClock, undoFullMoveClock);
    }

    public void undoMove(Move move, UndoInfo undo) {
        Color capturedColor = turnColor;
        Color moverColor = turnColor.opposite();
        int toX = move.to().x();
        int toY = move.to().y();
        int fromX = move.from().x();
        int fromY = move.from().y();

        // remove the piece from the destination square and restore the piece to the source square (works for promotion)
        board.put(move.to(), null);
        board.put(move.from(), Piece.of(moverColor, move.pieceType()))

        // restore the captured piece
        if (move.isCapture()) {
            board.put(undo.capturedSquare(), Piece.of(capturedColor, move.captureType()));
        }
        // undo rook movement for castling
        if (move.moveType() == MoveType.CASTLE_KINGSIDE) {
            Square rookSquare = Square.of(KINGSIDE_ROOK_FILE, fromY);
            Square kingSquare = Square.of(KING_FILE, fromY);
            board.put(rookSquare, board.pieceAt(kingSquare));
            board.put(Square.of(KING_FILE + 1, fromY), null);
        } else if (move.moveType() == MoveType.CASTLE_QUEENSIDE) {
            board.put(Square.of(QUEENSIDE_ROOK_FILE, fromY), board.pieceAt(Square.of(KING_FILE - 1, fromY)));
            board.put(Square.of(KING_FILE - 1, fromY), null);
        }

        turnColor = moverColor;
        castlingRights = new CastlingRights(undo.rights());
        enPassantTarget = undo.enPassantTarget();
        halfMoveClock = undo.halfMoveClock();
        fullMoveClock = undo.fullMoveClock();
    }

    public boolean makeMove(Square from, Square to, PieceType promotionType) {
        Piece piece = getPieceAt(from);
        if (piece == null || piece.color() != turnColor) {
            return false;
        }

        List<Square> validMoves = MoveGenerator.legalDestinations(this, from);
        if (!validMoves.contains(to)) {
            return false;
        }

        // Track for fifty-move rule: reset if pawn move or capture
        Piece capturedPiece = getPieceAt(to);
        boolean isPawnMove = piece.type() == PieceType.PAWN;
        boolean isCapture = capturedPiece != null || isEnPassantMove(from, to);

        if (isPawnMove || isCapture) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }
        if (piece.color().isBlack()) {
            fullMoveClock++;
        }

        boolean isEnPassant = isEnPassantMove(from, to);
        boolean isCastling = isCastlingMove(from, to);

        // Make the move
        board.put(to, piece);
        board.put(from, null);

        // Handle Pawn Promotion
        if (piece.type() == PieceType.PAWN) {
            int rank = piece.color().isWhite() ? BLACK_BACK_RANK : WHITE_BACK_RANK;
            if (to.rank() == rank) {
                if (!PROMOTION_CHOICES.contains(promotionType)) {
                    throw new IllegalStateException("Invalid promotion type: " + promotionType);
                }
                board.put(to, Piece.of(turnColor, promotionType));
            }
        }

        // Handle en passant capture
        if (isEnPassant) {
            int capturedRank = piece.color().isWhite() ? to.rank() - 1 : to.rank() + 1;
            Square capturedSquare = Square.of(to.file(), capturedRank);
            board.put(capturedSquare, null); // Remove the captured pawn
        }

        // Handle castling - move the rook
        if (isCastling) {
            int rookFromFile = to.file() > from.file() ? KINGSIDE_ROOK_FILE : QUEENSIDE_ROOK_FILE; // Kingside or queenside
            int rookToFile = to.file() > from.file() ? to.file() - 1 : to.file() + 1;
            int rookRank = from.rank();

            Square rookFromSquare = Square.of(rookFromFile, rookRank);
            Square rookToSquare = Square.of(rookToFile, rookRank);
            // move the rook
            Piece rook = board.put(rookFromSquare, null);
            board.put(rookToSquare, rook);
        }

        // Track the castling rights
        updateCastlingRights(piece, capturedPiece, from, to);

        // Track en passant target; only valid for the single reply to a double push
        if (isPawnMove && from.file() == to.file() && Math.abs(from.rank() - to.rank()) == 2) {
            enPassantTarget = Square.of(from.file(), (from.rank() + to.rank()) / 2);
        } else {
            enPassantTarget = null;
        }

        turnColor = turnColor.opposite();
        return true;
    }

    public List<Square> getValidMovesForPiece(Square pieceSquare) {
        Piece piece = getPieceAt(pieceSquare);
        if (piece == null) {
            return List.of();
        }

        List<Square> pseudoLegalMoves = piece.getValidMoves(pieceSquare, board);

        // Try to add en passant moves for pawns
        if (piece.type() == PieceType.PAWN) {
            pseudoLegalMoves.addAll(getEnPassantMoves(pieceSquare));
        }

        // Try to add castling moves for king
        if (piece.type() == PieceType.KING) {
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
                int captureY = piece.color().isWhite() ? move.y() - 1 : move.y() + 1;
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
            if (!isInCheck(piece.color())) {
                legalMoves.add(move);
            }

            // Undo the move
            board.put(pieceSquare, piece);
            board.put(move, capturedPiece);
            if (isEnPassant && enPassantCaptured != null) {
                int captureY = piece.color().isWhite() ? move.y() - 1 : move.y() + 1;
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
        Piece pawn = getPieceAt(pawnSquare);
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
        Piece piece = board.pieceAt(from);
        if (piece == null || piece.type() != PieceType.PAWN) {
            return false;
        }

        // En passant is a diagonal move to an empty square
        if (board.pieceAt(to) == null && from.x() != to.x()) {
            return getEnPassantMoves(from).contains(to);
        }

        return false;
    }

    /**
     * Get possible castling moves for a king at the given position
     */
    private List<Square> getCastlingMoves(Square kingSquare) {
        List<Square> castlingMoves = new ArrayList<>();
        Piece piece = getPieceAt(kingSquare);
        if (piece == null) {
            return castlingMoves;
        }

        Color color = piece.color();
        // Kingside castling
        if (canCastle(color, CastlingSide.KINGSIDE)) {
            castlingMoves.add(Square.of(KING_FILE + 2, kingSquare.y()));
        }

        // Queenside castling
        if (canCastle(color, CastlingSide.QUEENSIDE)) {
            castlingMoves.add(Square.of(KING_FILE - 2, kingSquare.y()));
        }

        return castlingMoves;
    }

    /**
     * Check if the king can castle.
     */
    private boolean canCastle(Color color, CastlingSide side) {
        int rank = switch (color) {
            case Color.WHITE -> WHITE_BACK_RANK;
            case Color.BLACK -> BLACK_BACK_RANK;
        };
        if (!castlingRights.has(color, side)) {
            return false;
        }
        Piece king = board[KING_FILE][rank];
        boolean notInCheck = !isInCheck(king.color());
        boolean noPiecesBetween = side == CastlingSide.KINGSIDE;
                ? board[5][rank] == null && board[6][rank] == null
                : board[2][rank] == null && board[3][rank] == null && board[1][rank] == null;
        boolean noSquareUnderAttackBetween = side == CastlingSide.KINGSIDE
                ? !isSquareUnderAttack(Square.of(5, rank), king.color()) && !isSquareUnderAttack(Square.of(6, rank), king.color())
                : !isSquareUnderAttack(Square.of(2, rank), king.color()) && !isSquareUnderAttack(Square.of(3, rank), king.color());
        return noPiecesBetween && notInCheck && noSquareUnderAttackBetween;
    }

    /**
     * Check if a move is a castling move
     */
    public boolean isCastlingMove(Square from, Square to) {
        Piece piece = board.pieceAt(from);
        if (piece == null || piece.type() != PieceType.KING) {
            return false;
        }

        // Castling is a 2-square king move horizontally
        return Math.abs(to.x() - from.x()) == 2 && to.y() == from.y();
    }

    private void updateCastlingRights(Piece piece, Piece capturedPiece, Square from, Square to) {
        if (piece.type() == PieceType.KING) {
            castlingRights = castlingRights.without(piece.color());
        }

        if (piece.type() == PieceType.ROOK) {
            updateCastlingRightsByRook(piece.color(), from);
        }

        if (capturedPiece != null && capturedPiece.type() == PieceType.ROOK) {
            // en-passant can't happen in the corner, so it's safe to pass `to` to the function
            updateCastlingRightsByRook(capturedPiece.color(), to);
        }
    }

    private void updateCastlingRightsByRook(Color color, Square rook) {
        CastlingSide side = rook.file() == KINGSIDE_ROOK_FILE ? CastlingSide.KINGSIDE : CastlingSide.QUEENSIDE;
        castlingRights = castlingRights.without(color, side);

//        if (color.isWhite() && rook.x() == KINGSIDE_ROOK_FILE && rook.y() == WHITE_BACK_RANK) {
//            castlingRights = castlingRights.without(color, CastlingSide.KINGSIDE);
//        } else if (color.isWhite() && rook.x() == QUEENSIDE_ROOK_FILE && rook.y() == WHITE_BACK_RANK) {
//            castlingRights = castlingRights.withoutWhiteQueenside();
//        } else if (color.isBlack() && rook.x() == KINGSIDE_ROOK_FILE && rook.y() == BLACK_BACK_RANK) {
//            castlingRights = castlingRights.withoutBlackKingside();
//        } else if (color.isBlack() && rook.x() == QUEENSIDE_ROOK_FILE && rook.y() == BLACK_BACK_RANK) {
//            castlingRights = castlingRights.withoutBlackQueenside();
//        }
    }

    /**
     * Check if a square is under attack by the opponent
     */
    private boolean isSquareUnderAttack(Square square, Color color) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board.pieceAt(Square.of(x, y));
                if (piece != null && piece.color() != color) {
                    Square enemySquare = Square.of(x, y);
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
    public boolean isCheckmate(Color color) {
        // Not in check, so not checkmate
        if (!isInCheck(color)) {
            return false;
        }

        // Check if there are any legal moves
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board.pieceAt(Square.of(x, y));
                if (piece != null && piece.color() == color) {
                    Square pieceSquare = Square.of(x, y);
                    List<Square> legalMoves = MoveGenerator.legalDestinations(this, pieceSquare);
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
    public boolean isStalemate(Color color) {
        if (isInCheck(color)) {
            return false; // In check, so not stalemate
        }

        // Check if there are any legal moves
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board.pieceAt(Square.of(x, y));
                if (piece != null && piece.color() == color) {
                    Square pieceSquare = Square.of(x, y);
                    List<Square> legalMoves = MoveGenerator.legalDestinations(this, pieceSquare);
                    if (!legalMoves.isEmpty()) {
                        return false; // Found a legal move
                    }
                }
            }
        }

        return true; // No legal moves and not in check -> stalemate
    }

    public boolean isInCheck(Color color) {
        // Find the king
        Square kingSquare = null;
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board.pieceAt(Square.of(x, y));
                if (piece != null && piece.type() == PieceType.KING && piece.color() == color) {
                    kingSquare = Square.of(x, y);
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
                Piece piece = board.pieceAt(Square.of(x, y));
                if (piece != null && piece.color() != color) {
                    Square enemySquare = Square.of(x, y);
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
                Piece piece = board.pieceAt(Square.of(x, y));
                if (piece != null) {
                    if (piece.color().isWhite()) {
                        whiteCount++;
                    } else {
                        blackCount++;
                    }
                    if (piece.type() == PieceType.BISHOP) {
                        bishopSquares.add(Square.of(x, y));
                    }
                    if (piece.type() != PieceType.KING) {
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
        if (otherPieces.size() == 1 && (otherPieces.getFirst().type() == PieceType.KNIGHT || otherPieces.getFirst().type() == PieceType.BISHOP)) {
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
     * Generate a hash of the current board position
     */
    @Override
    public int hashCode() {
        return Fen.write(this).hashCode();
    }

    /**
     * calculate the disambiguation string (when multiple same pieces can move to the same square, requires disambiguation)
     */
    public String getDisambiguation(Square from, Square to) {
        Piece movingPiece = board.pieceAt(from);
        if (movingPiece == null || movingPiece.type() == PieceType.PAWN || movingPiece.type() == PieceType.KING) {
            return "";
        }

        boolean needDisambiguation = false;
        boolean sameFile = false;
        boolean sameRank = false;

        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                // exclude self
                if (x == from.x() && y == from.y()) continue;

                Piece other = board.pieceAt(Square.of(x, y));

                if (other != null && other.color() == movingPiece.color() && other.type() == movingPiece.type()) {


                    List<Square> moves = MoveGenerator.legalDestinations(this, Square.of(x, y));

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
