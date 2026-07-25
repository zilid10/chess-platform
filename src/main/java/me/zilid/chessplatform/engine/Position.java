package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.formatter.Fen;

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
        Color moverColor = turnColor.opposite();
        int rank = move.from().rank();

        // remove the piece from the destination square and restore the piece to the source square (works for promotionType)
        Piece movedPiece = board.put(move.to(), null);
        if (move.isPromotion()) {
            board.put(move.from(), Piece.of(moverColor, PieceType.PAWN));
        } else {
            board.put(move.from(), movedPiece);
        }
        board.put(undo.capturedSquare(), undo.capturedPiece());

        // undo rook movement for castling
        if (move.isCastle()) {
            Square rookAfterCastle = move.isKingsideCastle() ? Square.of(KING_FILE + 1, rank) : Square.of(KING_FILE - 1, rank);
            Square rookFrom = move.isKingsideCastle() ? Square.of(KINGSIDE_ROOK_FILE, rank) : Square.of(QUEENSIDE_ROOK_FILE, rank);
            Piece rook = board.put(rookAfterCastle, null);
            board.put(rookFrom, rook);
        }

        turnColor = moverColor;
        castlingRights = undo.castlingRights();
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
        return Math.abs(to.file() - from.file()) == 2 && to.rank() == from.rank();
    }

    /**
     * check if the current position is checkmate
     */
    public boolean isCheckmate(Color color) {
        // No legal moves and in check -> checkmate
        return board.isInCheck(color) && MoveGenerator.legalMoves(this, color).isEmpty();
    }

    /**
     * Check if current position is a draw due to stalemate
     */
    public boolean isStalemate(Color color) {
        // No legal moves and not in check -> stalemate
        return !board.isInCheck(color) && MoveGenerator.legalMoves(this, color).isEmpty();
    }

    /**
     * Check if the current position is in check for the current color
     */
    public boolean isInCheck() {
        return board.isInCheck(turnColor);
    }

    /**
     * Check if fifty moves have been made without pawn move or capture
     */
    public boolean isFiftyMoveRule() {
        return halfMoveClock >= 100; // 100 half-moves = 50 full moves
    }

    /**
     * Generate a hash of the current board position
     */
    @Override
    public int hashCode() {
        // TODO: implement Zobrist as hash method
        return Fen.write(this).hashCode();
    }

}
