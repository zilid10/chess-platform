package me.zilid.chessplatform.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public class MoveGenerator {
    private static final int[][] BISHOP_DIRS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] ROOK_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] ALL_DIRS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] KNIGHT_JUMPS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
    };

    public static boolean isLegalMove(Position position, Move move) {
        return legalMoves(position, move.from()).contains(move);
    }

    public static Optional<Move> findLegalMove(
            Position position, Square from, Square to, @Nullable PieceType promotionType) {
        return legalMoves(position, from).stream()
                .filter(move ->
                        move.from().equals(from) && move.to().equals(to) && move.promotionType() == promotionType)
                .findAny();
    }

    public static List<Square> legalDestinations(Position position, Square square) {
        return legalMoves(position, square).stream().map(Move::to).distinct().toList();
    }

    public static List<Move> legalMoves(Position position, Color color) {
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            Square square = new Square(i);
            Piece piece = position.getPieceAt(square);
            if (piece != null && piece.color() == color) {
                moves.addAll(legalMoves(position, square));
            }
        }
        return moves;
    }

    public static List<Move> legalMoves(Position position, Square square) {
        Color color = position.getTurnColor();
        List<Move> moves = new ArrayList<>();
        for (Move move : pseudoLegalMoves(position, square)) {
            UndoInfo undo = position.applyMove(move);
            if (!position.getBoard().isInCheck(color)) {
                moves.add(move);
            }
            position.undoMove(move, undo);
        }
        return moves;
    }

    public static List<Move> pseudoLegalMoves(Position position, Square square) {
        Board board = position.getBoard();
        Color turnColor = position.getTurnColor();
        Piece piece = board.pieceAt(square);
        if (piece == null || piece.color() != turnColor) {
            return List.of();
        }
        List<Move> moves = new ArrayList<>();

        switch (piece.type()) {
            case PieceType.QUEEN -> slidingMoves(board, square, ALL_DIRS, PieceType.QUEEN, moves);
            case PieceType.ROOK -> slidingMoves(board, square, ROOK_DIRS, PieceType.ROOK, moves);
            case PieceType.BISHOP -> slidingMoves(board, square, BISHOP_DIRS, PieceType.BISHOP, moves);
            case PieceType.KNIGHT -> jumpingMoves(board, square, KNIGHT_JUMPS, PieceType.KNIGHT, moves);
            case PieceType.KING -> {
                jumpingMoves(board, square, ALL_DIRS, PieceType.KING, moves);
                castlingMoves(position, moves);
            }
            case PieceType.PAWN -> pawnMoves(position, square, moves);
        }

        return moves;
    }

    private static void slidingMoves(Board board, Square from, int[][] dirs, PieceType type, List<Move> moves) {
        Piece piece = Objects.requireNonNull(board.pieceAt(from));
        for (int[] dir : dirs) {
            int file = from.file() + dir[0];
            int rank = from.rank() + dir[1];
            while (Square.isValid(file, rank)) {
                Square to = Square.of(file, rank);
                Piece target = board.pieceAt(to);
                if (target != null) {
                    if (Piece.isEnemyPiece(piece, target)) {
                        moves.add(Move.normal(from, to, type));
                    }
                    break;
                }
                moves.add(Move.normal(from, to, type));
                file += dir[0];
                rank += dir[1];
            }
        }
    }

    private static void jumpingMoves(Board board, Square from, int[][] dirs, PieceType type, List<Move> moves) {
        Piece piece = Objects.requireNonNull(board.pieceAt(from));
        for (int[] dir : dirs) {
            int file = from.file() + dir[0];
            int rank = from.rank() + dir[1];
            if (!Square.isValid(file, rank)) {
                continue;
            }
            Square to = Square.of(file, rank);
            Piece target = board.pieceAt(to);
            if (target == null || Piece.isEnemyPiece(piece, target)) {
                moves.add(Move.normal(from, to, type));
            }
        }
    }

    private static void pawnMoves(Position position, Square from, List<Move> moves) {
        Color color = position.getTurnColor();
        Piece piece = Objects.requireNonNull(position.getPieceAt(from));
        int startingRank = color.isWhite() ? 1 : 6;
        int promotionRank = color.isWhite() ? 7 : 0;
        int forward = color.isWhite() ? 1 : -1;
        int file = from.file();
        int rank = from.rank();
        int oneUp = rank + forward;
        int twoUp = rank + forward + forward;

        // pawn push one square up (can promote)
        if (Square.isValid(file, oneUp) && position.getPieceAt(Square.of(file, oneUp)) == null) {
            if (oneUp == promotionRank) {
                for (PieceType promotionType : Move.PROMOTION_CHOICES) {
                    moves.add(Move.promotion(from, Square.of(file, oneUp), promotionType));
                }
            } else {
                moves.add(Move.normal(from, Square.of(file, oneUp), PieceType.PAWN));
            }
        }

        // pawn push two squares up
        if (rank == startingRank
                && position.getPieceAt(Square.of(file, oneUp)) == null
                && Square.isValid(file, twoUp)
                && position.getPieceAt(Square.of(file, twoUp)) == null) {
            moves.add(Move.doublePush(from, Square.of(file, twoUp)));
        }

        // en-passant capture
        Square enPassantTarget = position.getEnPassantTarget();
        if (enPassantTarget != null
                && enPassantTarget.rank() == oneUp
                && Math.abs(enPassantTarget.file() - file) == 1) {
            moves.add(Move.enPassant(from, enPassantTarget));
        }

        // pawn capture (can promote)
        for (int dir : List.of(-1, 1)) {
            if (!Square.isValid(file + dir, oneUp)) {
                continue;
            }
            Piece oneUpPiece = position.getPieceAt(Square.of(file + dir, oneUp));
            if (oneUpPiece == null || Piece.isFriendlyPiece(piece, oneUpPiece)) {
                continue;
            }
            Square captureSquare = Square.of(file + dir, oneUp);
            if (oneUp == promotionRank) {
                for (PieceType promotionType : Move.PROMOTION_CHOICES) {
                    moves.add(Move.promotion(from, captureSquare, promotionType));
                }
            } else {
                moves.add(Move.normal(from, captureSquare, PieceType.PAWN));
            }
        }
    }

    private static void castlingMoves(Position position, List<Move> moves) {
        Color color = position.getTurnColor();
        Color attackerColor = position.getTurnColor().opposite();
        String rank = color.isWhite() ? "1" : "8";
        Board board = position.getBoard();
        if (board.isInCheck(color)) {
            return;
        }

        // kingside castle: the squares between can't be attacked or blocked by other pieces
        Square fFile = Square.fromNotation("f" + rank);
        Square gFile = Square.fromNotation("g" + rank);
        if (position.getCastlingRights().has(color, CastlingSide.KINGSIDE)
                && board.pieceAt(fFile) == null
                && board.pieceAt(gFile) == null
                && !board.isSquareAttackedBy(fFile, attackerColor)
                && !board.isSquareAttackedBy(gFile, attackerColor)) {
            moves.add(Move.castleKingside(color));
        }

        // queenside castle: the squares between can't be attacked or blocked by other pieces
        Square bFile = Square.fromNotation("b" + rank);
        Square cFile = Square.fromNotation("c" + rank);
        Square dFile = Square.fromNotation("d" + rank);
        if (position.getCastlingRights().has(color, CastlingSide.QUEENSIDE)
                && board.pieceAt(bFile) == null
                && board.pieceAt(cFile) == null
                && board.pieceAt(dFile) == null
                && !board.isSquareAttackedBy(cFile, attackerColor)
                && !board.isSquareAttackedBy(dFile, attackerColor)) {
            moves.add(Move.castleQueenside(color));
        }
    }
}
