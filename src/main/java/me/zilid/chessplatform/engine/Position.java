package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.formatter.Fen;

import java.util.ArrayList;
import java.util.List;

public class Position {
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
        // track the undo information
        CastlingRights undoCastlingRights = castlingRights;
        Square undoEnPassantTarget = enPassantTarget;
        int undoHalfMoveClock = halfMoveClock;
        int undoFullMoveClock = fullMoveClock;
        Square capturedSquare = move.to();
        Piece capturedPiece = null;
        boolean isPawnMove = board.pieceAt(move.from()).type() == PieceType.PAWN;

        // update the board
        if (move.isEnPassant()) {
            capturedSquare = enPassantTarget;
            Piece piece = board.put(move.from(), null);
            board.put(move.to(), piece);
            capturedPiece = board.put(capturedSquare, null);
        } else if (move.isCastle()) {
            int rookFile = move.isKingsideCastle() ? KINGSIDE_ROOK_FILE : QUEENSIDE_ROOK_FILE;
            int offset = move.isKingsideCastle() ? -1 : 1;
            Square rookFrom = Square.of(rookFile, move.to().rank());
            Square rookTo = Square.of(move.to().file() + offset, move.to().rank());
            Piece king = board.put(move.from(), null);
            Piece rook = board.put(rookFrom, null);
            board.put(move.to(), king);
            board.put(rookTo, rook);
        } else {
            Piece piece = board.put(move.from(), null);
            if (move.isPromotion()) {
                capturedPiece = board.put(move.to(), Piece.of(turnColor, move.promotionType()));
            } else {
                capturedPiece = board.put(move.to(), piece);
            }
        }

        // update the metadata of the position
        boolean isCapture = capturedPiece != null;
        if (isPawnMove || isCapture) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }
        if (turnColor.isBlack()) {
            fullMoveClock++;
        }
        turnColor = turnColor.opposite();
        castlingRights = castlingRights.afterMove(move.from(), move.to());
        enPassantTarget = move.isDoublePush() ? Square.of(move.from().file(), (move.from().rank() + move.to().rank()) / 2) : null;
        return new UndoInfo(capturedSquare, capturedPiece, undoCastlingRights, undoEnPassantTarget, undoHalfMoveClock, undoFullMoveClock);
    }

    public void undoMove(Move move, UndoInfo undo) {
        Color capturedColor = turnColor;
        Color moverColor = turnColor.opposite();
        int toX = move.to().x();
        int toY = move.to().y();
        int fromX = move.from().x();
        int fromY = move.from().y();

        // remove the piece from the destination square and restore the piece to the source square (works for promotionType)
        board.put(move.to(), null);
        board.put(move.from(), Piece.of(moverColor, move.pieceType()));

        // restore the captured piece
        if (move.isCapture()) {
            board.put(undo.capturedSquare(), Piece.of(capturedColor, move.captureType()));
        }
        // undo rook movement for castling
        if (move.type() == MoveType.CASTLE_KINGSIDE) {
            Square rookSquare = Square.of(KINGSIDE_ROOK_FILE, fromY);
            Square kingSquare = Square.of(KING_FILE, fromY);
            board.put(rookSquare, board.pieceAt(kingSquare));
            board.put(Square.of(KING_FILE + 1, fromY), null);
        } else if (move.type() == MoveType.CASTLE_QUEENSIDE) {
            board.put(Square.of(QUEENSIDE_ROOK_FILE, fromY), board.pieceAt(Square.of(KING_FILE - 1, fromY)));
            board.put(Square.of(KING_FILE - 1, fromY), null);
        }

        turnColor = moverColor;
        castlingRights = new CastlingRights(undo.rights());
        enPassantTarget = undo.enPassantTarget();
        halfMoveClock = undo.halfMoveClock();
        fullMoveClock = undo.fullMoveClock();
    }

    /**
     * Check if a move is an en passant capture
     */
    public boolean isEnPassantMove(Square from, Square to) {
        Piece piece = board.pieceAt(from);
        return piece != null && piece.type() == PieceType.PAWN && from.file() != to.file() && Math.abs(from.rank() - to.rank()) == 1;
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
