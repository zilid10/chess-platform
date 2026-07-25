package me.zilid.chessplatform.chess;

import me.zilid.chessplatform.chess.formatter.Fen;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Perft ("performance test") counts every legal move sequence to a fixed depth and
 * compares against <a href="https://www.chessprogramming.org/Perft_Results">published reference values</a>.
 * The positions were designed to stress castling, en passant, pins, and promotions
 * simultaneously — a single wrong rule makes the node count diverge.
 */
class PerftTest {

    @ParameterizedTest(name = "perft(depth {1}) of {0} = {2}")
    @CsvSource({
            // starting position
            "'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', 1, 20",
            "'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', 2, 400",
            "'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', 3, 8902",
            "'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', 4, 197281",
            // Kiwipete: castling, en passant, pins and checks everywhere
            "'r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1', 1, 48",
            "'r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1', 2, 2039",
            "'r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1', 3, 97862",
            // Position 3: endgame with en passant edge cases
            "'8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1', 1, 14",
            "'8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1', 2, 191",
            "'8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1', 3, 2812",
            "'8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1', 4, 43238",
            // Position 4: promotionType-heavy position
            "'r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1', 1, 6",
            "'r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1', 2, 264",
            "'r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1', 3, 9467",
            // Position 5: bugs found in other engines, concentrated into one position
            "'rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8', 1, 44",
            "'rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8', 2, 1486",
            "'rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8', 3, 62379",
            // Position 6: An alternative Perft given by Steven Edwards
            "'r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10', 1, 46",
            "'r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10', 2, 2079",
            "'r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10', 3, 89890",
    })
    void perftMatchesKnownNodeCounts(String fen, int depth, long expected) {
        assertThat(perft(Fen.parse(fen), depth)).isEqualTo(expected);
    }

    private long perft(Position position, int depth) {
        if (depth == 0) {
            return 1;
        }
        long nodes = 0;
        for (Move move : MoveGenerator.legalMoves(position, position.getTurnColor())) {
            UndoInfo undo = position.applyMove(move);
            nodes += perft(position, depth - 1);
            position.undoMove(move, undo);
        }
        return nodes;
    }
}
