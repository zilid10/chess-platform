package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.pieces.Piece;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Perft ("performance test") counts every legal move sequence to a fixed depth and
 * compares against published reference values (https://www.chessprogramming.org/Perft_Results).
 * The positions were designed to stress castling, en passant, pins, and promotions
 * simultaneously — a single wrong rule makes the node count diverge.
 */
class PerftTest {

    private static final List<Piece.PieceType> PROMOTION_CHOICES = List.of(
            Piece.PieceType.QUEEN, Piece.PieceType.ROOK, Piece.PieceType.BISHOP, Piece.PieceType.KNIGHT);

    @ParameterizedTest(name = "perft(depth {1}) of {0} = {2}")
    @CsvSource({
            // starting position
            "'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', 1, 20",
            "'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', 2, 400",
            "'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', 3, 8902",
            "'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', 4, 197281",
            // 'Kiwipete': castling, en passant, pins and checks everywhere
            "'r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1', 1, 48",
            "'r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1', 2, 2039",
            "'r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1', 3, 97862",
            // endgame with en passant edge cases
            "'8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1', 1, 14",
            "'8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1', 2, 191",
            "'8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1', 3, 2812",
            "'8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1', 4, 43238",
            // promotion-heavy position
            "'r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1', 1, 6",
            "'r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1', 2, 264",
            "'r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1', 3, 9467",
            // bugs found in other engines, concentrated into one position
            "'rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8', 1, 44",
            "'rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8', 2, 1486",
            "'rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8', 3, 62379",
    })
    void perftMatchesKnownNodeCounts(String fen, int depth, long expected) {
        assertThat(perft(new Board(fen), depth)).isEqualTo(expected);
    }

    private long perft(Board board, int depth) {
        if (depth == 0) {
            return 1;
        }
        long nodes = 0;
        for (Move move : generateLegalMoves(board)) {
            UndoInfo undo = board.applyMove(move);
            nodes += perft(board, depth - 1);
            board.undoMove(move, undo);
        }
        return nodes;
    }

    private List<Move> generateLegalMoves(Board board) {
        List<Move> moves = new ArrayList<>();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Position from = new Position(x, y);
                Piece piece = board.getPiece(from);
                if (piece == null || piece.getColor() != board.getTurnColor()) {
                    continue;
                }
                for (Position to : board.getValidMovesForPiece(from)) {
                    moves.addAll(toMoves(board, piece, from, to));
                }
            }
        }
        return moves;
    }

    /** Wraps a (from, to) pair into full Move records; a promotion square yields four moves. */
    private List<Move> toMoves(Board board, Piece piece, Position from, Position to) {
        Piece.PieceType type = piece.getType();

        if (board.isCastlingMove(from, to)) {
            Move.MoveType side = to.x() > from.x()
                    ? Move.MoveType.CASTLE_KINGSIDE
                    : Move.MoveType.CASTLE_QUEENSIDE;
            return List.of(new Move(from, to, side, type, null, null));
        }

        if (board.isEnPassantMove(from, to)) {
            return List.of(new Move(from, to, Move.MoveType.EN_PASSANT, type, Piece.PieceType.PAWN, null));
        }

        Piece captured = board.getPiece(to);
        Piece.PieceType captureType = captured == null ? null : captured.getType();

        if (type == Piece.PieceType.PAWN && (to.y() == 0 || to.y() == 7)) {
            List<Move> promotions = new ArrayList<>();
            for (Piece.PieceType choice : PROMOTION_CHOICES) {
                promotions.add(new Move(from, to, Move.MoveType.PROMOTION, type, captureType, choice));
            }
            return promotions;
        }

        return List.of(new Move(from, to, Move.MoveType.NORMAL, type, captureType, null));
    }
}
