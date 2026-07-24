package me.zilid.chessplatform.engine;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule and edge-case tests: check, checkmate/stalemate, castling, en passant,
 * promotion, draw conditions, and apply/undo. These are the targeted tests that
 * tell you WHICH rule broke when the perft counts in {@link PerftTest} diverge.
 */
class ChessEngineRulesTest {

    private static ChessEngine engineFrom(String fen) {
        return new ChessEngine(Position.fromFen(fen));
    }

    private static List<String> legalMovesFrom(ChessEngine engine, String square) {
        return engine.getValidMoves(square).stream().map(Square::toNotation).toList();
    }

    /** The castling field is the third space-separated part of a FEN, e.g. "KQkq". */
    private static String castlingRightsOf(ChessEngine engine) {
        return engine.getFen().split(" ")[2];
    }

    @Nested
    class TurnOrder {

        @Test
        void whiteMovesFirstAndTurnsAlternate() {
            ChessEngine engine = new ChessEngine();
            assertThat(engine.makeMove("e7", "e5", null)).isFalse();
            assertThat(engine.makeMove("e2", "e4", null)).isTrue();
            assertThat(engine.makeMove("d2", "d4", null)).isFalse();
            assertThat(engine.makeMove("e7", "e5", null)).isTrue();
        }
    }

    @Nested
    class CheckRules {

        @Test
        void kingCannotMoveIntoAttackedSquare() {
            // Black rook on f8 covers the f-file
            ChessEngine engine = engineFrom("5r1k/8/8/8/8/8/8/4K3 w - - 0 1");
            assertThat(engine.makeMove("e1", "f1", null)).isFalse();
            assertThat(engine.makeMove("e1", "d1", null)).isTrue();
        }

        @Test
        void pinnedPieceHasNoLegalMoves() {
            // White bishop on e2 is pinned to its king by the rook on e8
            ChessEngine engine = engineFrom("4r2k/8/8/8/8/8/4B3/4K3 w - - 0 1");
            assertThat(engine.getValidMoves("e2")).isEmpty();
        }

        @Test
        void movesThatIgnoreCheckAreIllegal() {
            ChessEngine engine = engineFrom("4r2k/8/8/8/8/8/8/R3K3 w - - 0 1");
            assertThat(engine.isInCheck()).isTrue();
            assertThat(engine.makeMove("a1", "a2", null)).isFalse();
            assertThat(engine.makeMove("e1", "d1", null)).isTrue();
        }
    }

    @Nested
    class CheckmateAndStalemate {

        @Test
        void foolsMateIsCheckmate() {
            ChessEngine engine = new ChessEngine();
            engine.makeMove("f2", "f3", null);
            engine.makeMove("e7", "e5", null);
            engine.makeMove("g2", "g4", null);
            engine.makeMove("d8", "h4", null);
            assertThat(engine.isCheckmate()).isTrue();
            assertThat(engine.isStalemate()).isFalse();
        }

        @Test
        void backRankMateIsCheckmate() {
            // White rook on e8; black king boxed in by its own pawns
            ChessEngine engine = engineFrom("4R1k1/5ppp/8/8/8/8/8/6K1 b - - 0 1");
            assertThat(engine.isCheckmate()).isTrue();
        }

        @Test
        void checkWithAnEscapeIsNotCheckmate() {
            ChessEngine engine = engineFrom("4r2k/8/8/8/8/8/8/4K3 w - - 0 1");
            assertThat(engine.isInCheck()).isTrue();
            assertThat(engine.isCheckmate()).isFalse();
        }

        @Test
        void noMovesButNoCheckIsStalemate() {
            // Black king on h8 has no legal moves but is not attacked
            ChessEngine engine = engineFrom("7k/5Q2/8/8/8/8/8/K7 b - - 0 1");
            assertThat(engine.isStalemate()).isTrue();
            assertThat(engine.isCheckmate()).isFalse();
        }
    }

    @Nested
    class Castling {

        private static final String BOTH_SIDES_OPEN = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";

        @Test
        void bothCastlingMovesOfferedWhenPathIsClear() {
            ChessEngine engine = engineFrom(BOTH_SIDES_OPEN);
            assertThat(legalMovesFrom(engine, "e1")).contains("g1", "c1");
        }

        @Test
        void kingsideCastlingMovesKingAndRook() {
            ChessEngine engine = engineFrom(BOTH_SIDES_OPEN);
            assertThat(engine.makeMove("e1", "g1", null)).isTrue();
            assertThat(engine.getPosition().getPiece(Square.fromNotation("g1")).getType())
                    .isEqualTo(PieceType.KING);
            assertThat(engine.getPosition().getPiece(Square.fromNotation("f1")).getType())
                    .isEqualTo(PieceType.ROOK);
            assertThat(castlingRightsOf(engine)).isEqualTo("kq");
        }

        @Test
        void queensideCastlingMovesKingAndRook() {
            ChessEngine engine = engineFrom(BOTH_SIDES_OPEN);
            assertThat(engine.makeMove("e1", "c1", null)).isTrue();
            assertThat(engine.getPosition().getPiece(Square.fromNotation("c1")).getType())
                    .isEqualTo(PieceType.KING);
            assertThat(engine.getPosition().getPiece(Square.fromNotation("d1")).getType())
                    .isEqualTo(PieceType.ROOK);
        }

        @Test
        void cannotCastleThroughAttackedSquare() {
            // Black rook on f8 attacks f1, which the king would cross castling kingside
            ChessEngine engine = engineFrom("5r2/7k/8/8/8/8/8/R3K2R w KQ - 0 1");
            assertThat(legalMovesFrom(engine, "e1")).contains("c1").doesNotContain("g1");
        }

        @Test
        void cannotCastleWhileInCheck() {
            ChessEngine engine = engineFrom("4r3/7k/8/8/8/8/8/R3K2R w KQ - 0 1");
            assertThat(legalMovesFrom(engine, "e1")).doesNotContain("g1", "c1");
        }

        @Test
        void cannotCastleThroughOwnPieces() {
            ChessEngine engine = engineFrom("7k/8/8/8/8/8/8/RN2K1NR w KQ - 0 1");
            assertThat(legalMovesFrom(engine, "e1")).doesNotContain("g1", "c1");
        }

        @Test
        void kingMoveForfeitsBothCastlingRights() {
            ChessEngine engine = engineFrom(BOTH_SIDES_OPEN);
            engine.makeMove("e1", "e2", null);
            assertThat(castlingRightsOf(engine)).isEqualTo("kq");
        }

        @Test
        void rookMoveForfeitsThatSidesRight() {
            ChessEngine engine = engineFrom(BOTH_SIDES_OPEN);
            engine.makeMove("a1", "a2", null);
            assertThat(castlingRightsOf(engine)).isEqualTo("Kkq");
        }

        @Test
        void noCastlingWhenFenGrantsNoRights() {
            ChessEngine engine = engineFrom("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1");
            assertThat(legalMovesFrom(engine, "e1")).doesNotContain("g1", "c1");
        }
    }

    @Nested
    class EnPassant {

        @Test
        void enPassantCaptureRemovesThePassedPawn() {
            // Black just played d7-d5 past white's e5 pawn
            ChessEngine engine = engineFrom("rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3");
            assertThat(legalMovesFrom(engine, "e5")).contains("d6");
            assertThat(engine.makeMove("e5", "d6", null)).isTrue();
            assertThat(engine.getPosition().getPiece(Square.fromNotation("d5"))).isNull();
        }

        @Test
        void enPassantExpiresAfterOneMove() {
            ChessEngine engine = new ChessEngine();
            engine.makeMove("e2", "e4", null);
            engine.makeMove("a7", "a6", null);
            engine.makeMove("e4", "e5", null);
            engine.makeMove("d7", "d5", null); // en passant on d6 is now available...
            engine.makeMove("h2", "h3", null); // ...but white declines
            engine.makeMove("a6", "a5", null);
            assertThat(legalMovesFrom(engine, "e5")).doesNotContain("d6");
        }

        @Test
        void enPassantThatExposesOwnKingIsIllegal() {
            // Capturing d5xc6 would clear the 5th rank between white's king and the h5 rook
            ChessEngine engine = engineFrom("7k/8/8/K1pP3r/8/8/8/8 w - c6 0 2");
            assertThat(legalMovesFrom(engine, "d5")).contains("d6").doesNotContain("c6");
        }
    }

    @Nested
    class Promotion {

        @ParameterizedTest
        @EnumSource(value = PieceType.class, names = {"QUEEN", "ROOK", "BISHOP", "KNIGHT"})
        void pawnPromotesToChosenPiece(PieceType promotion) {
            ChessEngine engine = engineFrom("7k/P7/8/8/8/8/8/K7 w - - 0 1");
            assertThat(engine.makeMove("a7", "a8", promotion)).isTrue();
            assertThat(engine.getPosition().getPiece(Square.fromNotation("a8")).getType())
                    .isEqualTo(promotion);
        }

        @Test
        void pawnCanPromoteByCapturing() {
            // Black rook on b8 can be taken with promotion
            ChessEngine engine = engineFrom("1r5k/P7/8/8/8/8/8/K7 w - - 0 1");
            assertThat(legalMovesFrom(engine, "a7")).containsExactlyInAnyOrder("a8", "b8");
            assertThat(engine.makeMove("a7", "b8", PieceType.QUEEN)).isTrue();
            assertThat(engine.getPosition().getPiece(Square.fromNotation("b8")).getType())
                    .isEqualTo(PieceType.QUEEN);
        }
    }

    @Nested
    class DrawRules {

        @Test
        void fiftyMoveRuleTriggersAtHundredHalfMoves() {
            assertThat(engineFrom("7k/8/8/8/8/8/R7/K7 w - - 100 60").isFiftyMoveRule()).isTrue();
            assertThat(engineFrom("7k/8/8/8/8/8/R7/K7 w - - 99 60").isFiftyMoveRule()).isFalse();
        }

        @Test
        void insufficientMaterialCases() {
            assertThat(engineFrom("7k/8/8/8/8/8/8/K7 w - - 0 1").isInsufficientMaterial())
                    .as("king vs king").isTrue();
            assertThat(engineFrom("7k/8/8/8/8/8/8/KN6 w - - 0 1").isInsufficientMaterial())
                    .as("king and knight vs king").isTrue();
            assertThat(engineFrom("7k/8/8/8/8/8/8/KB6 w - - 0 1").isInsufficientMaterial())
                    .as("king and bishop vs king").isTrue();
            assertThat(engineFrom("2b4k/8/8/8/8/8/8/K4B2 w - - 0 1").isInsufficientMaterial())
                    .as("same-colored bishops").isTrue();
            assertThat(engineFrom("2b4k/8/8/8/8/8/8/K1B5 w - - 0 1").isInsufficientMaterial())
                    .as("opposite-colored bishops").isFalse();
            assertThat(engineFrom("7k/8/8/8/8/8/8/KQ6 w - - 0 1").isInsufficientMaterial())
                    .as("queen on the board").isFalse();
        }
    }

    @Nested
    class ApplyAndUndo {

        @Test
        void undoRestoresTheExactPosition() {
            ChessEngine engine = new ChessEngine();
            String before = engine.getFen();
            Move move = new Move(Square.fromNotation("e2"), Square.fromNotation("e4"),
                    Move.MoveType.NORMAL, PieceType.PAWN, null, null);

            UndoInfo undo = engine.applyMove(move);
            assertThat(engine.getFen()).isNotEqualTo(before);

            engine.undoMove(move, undo);
            assertThat(engine.getFen()).isEqualTo(before);
        }

        @Test
        void undoRestoresACapturedPiece() {
            String fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
            ChessEngine engine = engineFrom(fen);
            Move capture = new Move(Square.fromNotation("e5"), Square.fromNotation("g6"),
                    Move.MoveType.NORMAL, PieceType.KNIGHT, PieceType.PAWN, null);

            UndoInfo undo = engine.applyMove(capture);
            engine.undoMove(capture, undo);
            assertThat(engine.getFen()).isEqualTo(fen);
        }
    }
}
