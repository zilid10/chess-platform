package me.zilid.chessplatform.chess.pieces;

import me.zilid.chessplatform.chess.ChessEngine;
import me.zilid.chessplatform.chess.Square;
import me.zilid.chessplatform.chess.format.Fen;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Movement tests for each piece type. Every test follows the same shape:
 * build a position from a FEN string, ask for the legal moves of one piece,
 * and assert the exact set of destination squares.
 */
class PieceTest {

    private static List<String> legalMovesFrom(String fen, String square) {
        return new ChessEngine(Fen.parse(fen)).getValidMoves(square).stream()
                .map(Square::toNotation)
                .toList();
    }

    @Nested
    class KnightMoves {

        @Test
        void knightInCenterHasEightMoves() {
            assertThat(legalMovesFrom("k7/8/8/8/4N3/8/8/7K w - - 0 1", "e4"))
                    .containsExactlyInAnyOrder("d6", "f6", "c5", "g5", "c3", "g3", "d2", "f2");
        }

        @Test
        void knightInCornerHasTwoMoves() {
            assertThat(legalMovesFrom("k7/8/8/8/8/8/8/N6K w - - 0 1", "a1"))
                    .containsExactlyInAnyOrder("b3", "c2");
        }

        @Test
        void knightCannotLandOnFriendlyPieces() {
            // White pawns occupy d2 and f2
            assertThat(legalMovesFrom("k7/8/8/8/4N3/8/3P1P2/7K w - - 0 1", "e4"))
                    .containsExactlyInAnyOrder("d6", "f6", "c5", "g5", "c3", "g3");
        }

        @Test
        void knightCapturesEnemyPiece() {
            assertThat(legalMovesFrom("k7/8/3p4/8/4N3/8/8/7K w - - 0 1", "e4"))
                    .contains("d6");
        }
    }

    @Nested
    class RookMoves {

        @Test
        void rookOnOpenBoardHasFourteenMoves() {
            assertThat(legalMovesFrom("k7/8/8/8/4R3/8/8/7K w - - 0 1", "e4"))
                    .hasSize(14)
                    .contains("e1", "e8", "a4", "h4");
        }

        @Test
        void rookStopsAtCaptureAndBeforeFriendlyPiece() {
            // Black pawn on e5 (capturable), white pawn on h4 (blocks)
            assertThat(legalMovesFrom("k7/8/8/4p3/4R2P/8/8/7K w - - 0 1", "e4"))
                    .containsExactlyInAnyOrder("e5", "e3", "e2", "e1", "a4", "b4", "c4", "d4", "f4", "g4");
        }
    }

    @Nested
    class BishopMoves {

        @Test
        void bishopInCenterHasThirteenMoves() {
            assertThat(legalMovesFrom("7k/8/8/8/4B3/8/8/K7 w - - 0 1", "e4"))
                    .hasSize(13)
                    .contains("a8", "h7", "h1", "b1");
        }

        @Test
        void bishopIsBlockedByFriendlyPiece() {
            // White pawn on g6 cuts off the northeast diagonal
            assertThat(legalMovesFrom("7k/8/6P1/8/4B3/8/8/K7 w - - 0 1", "e4"))
                    .contains("f5")
                    .doesNotContain("g6", "h7");
        }
    }

    @Nested
    class QueenMoves {

        @Test
        void queenInCenterHasTwentySevenMoves() {
            assertThat(legalMovesFrom("7k/8/8/8/4Q3/8/8/K7 w - - 0 1", "e4"))
                    .hasSize(27);
        }
    }

    @Nested
    class KingMoves {

        @Test
        void kingInCenterHasEightMoves() {
            assertThat(legalMovesFrom("7k/8/8/8/4K3/8/8/8 w - - 0 1", "e4"))
                    .containsExactlyInAnyOrder("d3", "d4", "d5", "e3", "e5", "f3", "f4", "f5");
        }

        @Test
        void kingAvoidsAttackedSquares() {
            // Black rook on f1 covers the entire f-file
            assertThat(legalMovesFrom("7k/8/8/8/4K3/8/8/5r2 w - - 0 1", "e4"))
                    .containsExactlyInAnyOrder("d3", "d4", "d5", "e3", "e5");
        }
    }

    @Nested
    class PawnMoves {

        @Test
        void pawnCanPushOneOrTwoFromStartingRank() {
            assertThat(legalMovesFrom("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "e2"))
                    .containsExactlyInAnyOrder("e3", "e4");
        }

        @Test
        void pawnCanOnlyPushOneAfterLeavingStartingRank() {
            assertThat(legalMovesFrom("7k/8/8/8/8/4P3/8/K7 w - - 0 1", "e3"))
                    .containsExactlyInAnyOrder("e4");
        }

        @Test
        void pawnIsBlockedByPieceAhead() {
            assertThat(legalMovesFrom("7k/8/8/4p3/4P3/8/8/K7 w - - 0 1", "e4"))
                    .isEmpty();
        }

        @Test
        void pawnCapturesDiagonally() {
            // Black pawns on d5, e5, f5: push blocked, both captures available
            assertThat(legalMovesFrom("7k/8/8/3ppp2/4P3/8/8/K7 w - - 0 1", "e4"))
                    .containsExactlyInAnyOrder("d5", "f5");
        }

        @Test
        void blackPawnMovesTowardRankOne() {
            assertThat(legalMovesFrom("7k/4p3/8/8/8/8/8/K7 b - - 0 1", "e7"))
                    .containsExactlyInAnyOrder("e6", "e5");
        }
    }
}
