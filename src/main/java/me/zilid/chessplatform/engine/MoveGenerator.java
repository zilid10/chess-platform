package me.zilid.chessplatform.engine;

import java.util.ArrayList;
import java.util.List;

public class MoveGenerator {
    private static final int[][] BISHOP_DIRS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] ROOK_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] ALL_DIRS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] KNIGHT_JUMPS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KNIGHT = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING = {{0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}};
    private static final int[][] ORTHO = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    private static final int[][] DIAG = {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};

    public static List<Square> legalDestinations(Position position, Square square) {
        return legalMoves(position, square).stream()
                .map(Move::to)
                .toList();
    }

    public static List<Move> legalMoves(Position position) {
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            Square square = new Square(i);
            if (position.getPieceAt(square) != null) {
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
            if (!position.isInCheck(color)) {
                moves.add(move);
            }
            position.undoMove(move, undo);
        }
        return moves;
    }

    public static List<Move> pseudoLegalMoves(Position position) {
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            Square square = new Square(i);
            if (position.getPieceAt(square) != null) {
                moves.addAll(pseudoLegalMoves(position, square));
            }
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
                castlingMoves(position, square, moves);
            }
            case PieceType.PAWN -> pawnMoves(position, square, moves);
        }

        return moves;
    }

    public static boolean isLegalMove(Position position, Move move) {
        return legalMoves(position, move.from()).contains(move);
    }

    private static boolean isSquareAttackedBy(Position position, Square square, Color attacker) {
        int file = square.file();
        int rank = square.rank();
        int back = attacker.isWhite() ? -1 : 1;
        if (hasPiece(position, file + 1, rank + back, attacker, PieceType.PAWN)
                || hasPiece(position, file - 1, rank + back, attacker, PieceType.PAWN)) {
            return true;
        }
        for (int[] dir : KNIGHT) {
            if (hasPiece(position, file + dir[0], rank + dir[1], attacker, PieceType.KNIGHT)) {
                return true;
            }
        }
        for (int[] dir : KING) {
            if (hasPiece(position, file + dir[0], rank + dir[1], attacker, PieceType.KING)) {
                return true;
            }
        }
        for (int[] dir : ORTHO) {
            Piece piece = firstPieceOnRay(position, square, dir);
            if (piece != null && piece.color() == attacker && (piece.type() == PieceType.QUEEN || piece.type() == PieceType.ROOK)) {
                return true;
            }
        }
        for (int[] dir : DIAG) {
            Piece piece = firstPieceOnRay(position, square, dir);
            if (piece != null && piece.color() == attacker && (piece.type() == PieceType.QUEEN || piece.type() == PieceType.BISHOP)) {
                return true;
            }
        }
        return false;
    }

    private static Piece firstPieceOnRay(Position position, Square square, int[] dir) {
        int file = square.file() + dir[0];
        int rank = square.rank() + dir[1];
        while (Square.isValid(file, rank)) {
            Piece piece = position.getPieceAt(Square.of(file, rank));
            if (piece != null) {
                return piece;
            }
            file += dir[0];
            rank += dir[1];
        }
        return null;
    }

    private static boolean hasPiece(Position position, int file, int rank, Color color, PieceType type) {
        if (!Square.isValid(file, rank)) {
            return false;
        }
        Piece piece = position.getPieceAt(Square.of(file, rank));
        return piece != null && piece.color() == color && piece.type() == type;
    }

    private static void slidingMoves(Board board, Square from, int[][] dirs, PieceType type, List<Move> moves) {
        Piece piece = board.pieceAt(from);
        for (int[] dir : dirs) {
            int file = from.file() + dir[0];
            int rank = from.rank() + dir[1];
            while (Square.isValid(file, rank)) {
                Square to = Square.of(file, rank);
                Piece target = board.pieceAt(to);
                if (target != null && Piece.isFriendlyPiece(piece, target)) {
                    break;
                }
                moves.add(Move.normal(from, to, type));
                file += dir[0];
                rank += dir[1];
            }
        }
    }

    private static void jumpingMoves(Board board, Square from, int[][] dirs, PieceType type, List<Move> moves) {
        Piece piece = board.pieceAt(from);
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
        Piece piece = position.getPieceAt(from);
        int startingRank = color.isWhite() ? 1 : 6;
        int promotionRank = color.isWhite() ? 7 : 0;
        int forward = color.isWhite() ? 1 : -1;
        int file = from.file();
        int rank = from.rank();
        int oneUp = rank + forward;
        int twoUp = rank + forward + forward;

        if (Square.isValid(file, oneUp) && position.getPieceAt(Square.of(file, oneUp)) == null) {
            if (oneUp == promotionRank) {
                for (PieceType promotionType : Move.PROMOTION_CHOICES) {
                    moves.add(Move.promotion(from, Square.of(file, oneUp), promotionType));
                }
            } else {
                moves.add(Move.normal(from, Square.of(file, oneUp), PieceType.PAWN));
            }
        }

        if (rank == startingRank && position.getPieceAt(Square.of(file, oneUp)) == null &&
                Square.isValid(file, twoUp) && position.getPieceAt(Square.of(file, twoUp)) == null) {
            moves.add(Move.doublePush(from, Square.of(file, twoUp)));
        }

        for (int dir : List.of(-1, 1)) {
            if (!Square.isValid(file + dir, oneUp) || position.getPieceAt(Square.of(file + dir, oneUp)) == null
                    || Piece.isFriendlyPiece(piece, position.getPieceAt(Square.of(file + dir, oneUp)))) {
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

    private static void castlingMoves(Position position, Square from, List<Move> moves) {
        Color color = position.getTurnColor();
        Color attackerColor = position.getTurnColor().opposite();
        String rank = color.isWhite() ? "1" : "8";

        Square fFile = Square.fromNotation("f" + rank);
        Square gFile = Square.fromNotation("g" + rank);
        if (position.getCastlingRights().has(color, CastlingSide.KINGSIDE) && position.getPieceAt(fFile) == null
                && position.getPieceAt(gFile) == null && isSquareAttackedBy(position, fFile, attackerColor)
                && isSquareAttackedBy(position, gFile, attackerColor)) {
            moves.add(Move.castleKingside(color));
        }

        Square bFile = Square.fromNotation("b" + rank);
        Square cFile = Square.fromNotation("c" + rank);
        Square dFile = Square.fromNotation("d" + rank);

        if (position.getCastlingRights().has(color, CastlingSide.QUEENSIDE) && position.getPieceAt(bFile) == null
                && position.getPieceAt(cFile) == null && position.getPieceAt(dFile) == null
                && !isSquareAttackedBy(position, cFile, attackerColor) && !isSquareAttackedBy(position, dFile, attackerColor)) {
            moves.add(Move.castleQueenside(color));
        }
    }
}
