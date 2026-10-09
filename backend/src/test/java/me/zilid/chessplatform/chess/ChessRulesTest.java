package me.zilid.chessplatform.chess;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import me.zilid.chessplatform.chess.format.Fen;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Rule and edge-case tests: check, checkmate/stalemate, castling, en passant, promotionType, draw conditions, and
 * apply/undo. These are the targeted tests that tell you WHICH rule broke when the perft counts in {@link PerftTest}
 * diverge.
 */
class ChessRulesTest {

    private static boolean makeMove(Position position, String from, String to, PieceType promotionType) {
        return MoveGenerator.findLegalMove(position, Square.fromNotation(from), Square.fromNotation(to), promotionType)
                .map(move -> {
                    position.applyMove(move);
                    return true;
                })
                .orElse(false);
    }

    private static List<String> legalMovesFrom(Position position, String square) {
        return MoveGenerator.legalDestinations(position, Square.fromNotation(square)).stream()
                .map(Square::toNotation)
                .toList();
    }

    /** The castling field is the third space-separated part of a FEN, e.g. "KQkq". */
    private static String castlingRightsOf(Position position) {
        return Fen.format(position).split(" ", -1)[2];
    }

    @Nested
    class TurnOrder {

        @Test
        void whiteMovesFirstAndTurnsAlternate() {
            Position position = Position.startingPosition();
            assertThat(makeMove(position, "e7", "e5", null)).isFalse();
            assertThat(makeMove(position, "e2", "e4", null)).isTrue();
            assertThat(makeMove(position, "d2", "d4", null)).isFalse();
            assertThat(makeMove(position, "e7", "e5", null)).isTrue();
        }
    }

    @Nested
    class CheckRules {

        @Test
        void kingCannotMoveIntoAttackedSquare() {
            // Black rook on f8 covers the f-file
            Position position = Fen.parse("5r1k/8/8/8/8/8/8/4K3 w - - 0 1");
            assertThat(makeMove(position, "e1", "f1", null)).isFalse();
            assertThat(makeMove(position, "e1", "d1", null)).isTrue();
        }

        @Test
        void pinnedPieceHasNoLegalMoves() {
            // White bishop on e2 is pinned to its king by the rook on e8
            Position position = Fen.parse("4r2k/8/8/8/8/8/4B3/4K3 w - - 0 1");
            assertThat(legalMovesFrom(position, "e2")).isEmpty();
        }

        @Test
        void movesThatIgnoreCheckAreIllegal() {
            Position position = Fen.parse("4r2k/8/8/8/8/8/8/R3K3 w - - 0 1");
            assertThat(position.isInCheck()).isTrue();
            assertThat(makeMove(position, "a1", "a2", null)).isFalse();
            assertThat(makeMove(position, "e1", "d1", null)).isTrue();
        }
    }

    @Nested
    class CheckmateAndStalemate {

        @Test
        void foolsMateIsCheckmate() {
            Position position = Position.startingPosition();
            makeMove(position, "f2", "f3", null);
            makeMove(position, "e7", "e5", null);
            makeMove(position, "g2", "g4", null);
            makeMove(position, "d8", "h4", null);
            assertThat(position.isCheckmate(position.getTurnColor())).isTrue();
            assertThat(position.isStalemate(position.getTurnColor())).isFalse();
        }

        @Test
        void backRankMateIsCheckmate() {
            // White rook on e8; black king boxed in by its own pawns
            Position position = Fen.parse("4R1k1/5ppp/8/8/8/8/8/6K1 b - - 0 1");
            assertThat(position.isCheckmate(position.getTurnColor())).isTrue();
        }

        @Test
        void checkWithAnEscapeIsNotCheckmate() {
            Position position = Fen.parse("4r2k/8/8/8/8/8/8/4K3 w - - 0 1");
            assertThat(position.isInCheck()).isTrue();
            assertThat(position.isCheckmate(position.getTurnColor())).isFalse();
        }

        @Test
        void noMovesButNoCheckIsStalemate() {
            // Black king on h8 has no legal moves but is not attacked
            Position position = Fen.parse("7k/5Q2/8/8/8/8/8/K7 b - - 0 1");
            assertThat(position.isStalemate(position.getTurnColor())).isTrue();
            assertThat(position.isCheckmate(position.getTurnColor())).isFalse();
        }
    }

    @Nested
    class Castling {

        private static final String BOTH_SIDES_OPEN = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";

        @Test
        void bothCastlingMovesOfferedWhenPathIsClear() {
            Position position = Fen.parse(BOTH_SIDES_OPEN);
            assertThat(legalMovesFrom(position, "e1")).contains("g1", "c1");
        }

        @Test
        void kingsideCastlingMovesKingAndRook() {
            Position position = Fen.parse(BOTH_SIDES_OPEN);
            assertThat(makeMove(position, "e1", "g1", null)).isTrue();
            assertThat(position.getPieceAt(Square.fromNotation("g1")).type()).isEqualTo(PieceType.KING);
            assertThat(position.getPieceAt(Square.fromNotation("f1")).type()).isEqualTo(PieceType.ROOK);
            assertThat(castlingRightsOf(position)).isEqualTo("kq");
        }

        @Test
        void queensideCastlingMovesKingAndRook() {
            Position position = Fen.parse(BOTH_SIDES_OPEN);
            assertThat(makeMove(position, "e1", "c1", null)).isTrue();
            assertThat(position.getPieceAt(Square.fromNotation("c1")).type()).isEqualTo(PieceType.KING);
            assertThat(position.getPieceAt(Square.fromNotation("d1")).type()).isEqualTo(PieceType.ROOK);
        }

        @Test
        void cannotCastleThroughAttackedSquare() {
            // Black rook on f8 attacks f1, which the king would cross castling kingside
            Position position = Fen.parse("5r2/7k/8/8/8/8/8/R3K2R w KQ - 0 1");
            assertThat(legalMovesFrom(position, "e1")).contains("c1").doesNotContain("g1");
        }

        @Test
        void cannotCastleWhileInCheck() {
            Position position = Fen.parse("4r3/7k/8/8/8/8/8/R3K2R w KQ - 0 1");
            assertThat(legalMovesFrom(position, "e1")).doesNotContain("g1", "c1");
        }

        @Test
        void cannotCastleThroughOwnPieces() {
            Position position = Fen.parse("7k/8/8/8/8/8/8/RN2K1NR w KQ - 0 1");
            assertThat(legalMovesFrom(position, "e1")).doesNotContain("g1", "c1");
        }

        @Test
        void kingMoveForfeitsBothCastlingRights() {
            Position position = Fen.parse(BOTH_SIDES_OPEN);
            makeMove(position, "e1", "e2", null);
            assertThat(castlingRightsOf(position)).isEqualTo("kq");
        }

        @Test
        void rookMoveForfeitsThatSidesRight() {
            Position position = Fen.parse(BOTH_SIDES_OPEN);
            makeMove(position, "a1", "a2", null);
            assertThat(castlingRightsOf(position)).isEqualTo("Kkq");
        }

        @Test
        void noCastlingWhenFenGrantsNoRights() {
            Position position = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1");
            assertThat(legalMovesFrom(position, "e1")).doesNotContain("g1", "c1");
        }
    }

    @Nested
    class EnPassant {

        @Test
        void enPassantCaptureRemovesThePassedPawn() {
            // Black just played d7-d5 past white's e5 pawn
            Position position = Fen.parse("rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3");
            assertThat(legalMovesFrom(position, "e5")).contains("d6");
            assertThat(makeMove(position, "e5", "d6", null)).isTrue();
            assertThat(position.getPieceAt(Square.fromNotation("d5"))).isNull();
        }

        @Test
        void enPassantExpiresAfterOneMove() {
            Position position = Position.startingPosition();
            makeMove(position, "e2", "e4", null);
            makeMove(position, "a7", "a6", null);
            makeMove(position, "e4", "e5", null);
            makeMove(position, "d7", "d5", null); // en passant on d6 is now available...
            makeMove(position, "h2", "h3", null); // ...but white declines
            makeMove(position, "a6", "a5", null);
            assertThat(legalMovesFrom(position, "e5")).doesNotContain("d6");
        }

        @Test
        void enPassantThatExposesOwnKingIsIllegal() {
            // Capturing d5xc6 would clear the 5th rank between white's king and the h5 rook
            Position position = Fen.parse("7k/8/8/K1pP3r/8/8/8/8 w - c6 0 2");
            assertThat(legalMovesFrom(position, "d5")).contains("d6").doesNotContain("c6");
        }
    }

    @Nested
    class EdgeFilePawns {

        @Test
        void cornerPawnsHaveNoOffBoardCaptures() {
            Position white = Position.startingPosition();
            assertThat(legalMovesFrom(white, "a2")).containsExactlyInAnyOrder("a3", "a4");
            assertThat(legalMovesFrom(white, "h2")).containsExactlyInAnyOrder("h3", "h4");

            Position black = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1");
            assertThat(legalMovesFrom(black, "a7")).containsExactlyInAnyOrder("a6", "a5");
            assertThat(legalMovesFrom(black, "h7")).containsExactlyInAnyOrder("h6", "h5");
        }
    }

    @Nested
    class Promotion {

        @ParameterizedTest
        @EnumSource(
                value = PieceType.class,
                names = {"QUEEN", "ROOK", "BISHOP", "KNIGHT"})
        void pawnPromotesToChosenPiece(PieceType promotion) {
            Position position = Fen.parse("7k/P7/8/8/8/8/8/K7 w - - 0 1");
            assertThat(makeMove(position, "a7", "a8", promotion)).isTrue();
            assertThat(position.getPieceAt(Square.fromNotation("a8")).type()).isEqualTo(promotion);
        }

        @Test
        void pawnCanPromoteByCapturing() {
            // Black rook on b8 can be taken with promotionType
            Position position = Fen.parse("1r5k/P7/8/8/8/8/8/K7 w - - 0 1");
            assertThat(legalMovesFrom(position, "a7")).containsExactlyInAnyOrder("a8", "b8");
            assertThat(makeMove(position, "a7", "b8", PieceType.QUEEN)).isTrue();
            assertThat(position.getPieceAt(Square.fromNotation("b8")).type()).isEqualTo(PieceType.QUEEN);
        }
    }

    @Nested
    class DrawRules {

        @Test
        void fiftyMoveRuleTriggersAtHundredHalfMoves() {
            assertThat(Fen.parse("7k/8/8/8/8/8/R7/K7 w - - 100 60").isFiftyMoveRule())
                    .isTrue();
            assertThat(Fen.parse("7k/8/8/8/8/8/R7/K7 w - - 99 60").isFiftyMoveRule())
                    .isFalse();
        }

        @Test
        void insufficientMaterialCases() {
            assertThat(Fen.parse("7k/8/8/8/8/8/8/K7 w - - 0 1").getBoard().isInsufficientMaterial())
                    .as("king vs king")
                    .isTrue();
            assertThat(Fen.parse("7k/8/8/8/8/8/8/KN6 w - - 0 1").getBoard().isInsufficientMaterial())
                    .as("king and knight vs king")
                    .isTrue();
            assertThat(Fen.parse("7k/8/8/8/8/8/8/KB6 w - - 0 1").getBoard().isInsufficientMaterial())
                    .as("king and bishop vs king")
                    .isTrue();
            assertThat(Fen.parse("2b4k/8/8/8/8/8/8/K4B2 w - - 0 1").getBoard().isInsufficientMaterial())
                    .as("same-colored bishops")
                    .isTrue();
            assertThat(Fen.parse("2b4k/8/8/8/8/8/8/K1B5 w - - 0 1").getBoard().isInsufficientMaterial())
                    .as("opposite-colored bishops")
                    .isFalse();
            assertThat(Fen.parse("7k/8/8/8/8/8/8/KQ6 w - - 0 1").getBoard().isInsufficientMaterial())
                    .as("queen on the board")
                    .isFalse();
            assertThat(Fen.parse("7k/8/8/8/8/8/8/KQB5 w - - 0 1").getBoard().isInsufficientMaterial())
                    .as("queen beside a single minor piece")
                    .isFalse();
            assertThat(Fen.parse("7k/8/8/8/8/8/P7/KB6 w - - 0 1").getBoard().isInsufficientMaterial())
                    .as("pawn beside a single minor piece")
                    .isFalse();
            assertThat(Fen.parse("6nk/8/8/8/8/8/8/KN6 w - - 0 1").getBoard().isInsufficientMaterial())
                    .as("knight vs knight")
                    .isFalse();
        }

        @ParameterizedTest(name = "{0}: White can mate = {1}, Black can mate = {2}")
        @CsvSource({
            "k7/8/8/8/8/8/8/QK6 w - - 0 1, true, false", // lone king cannot mate
            "k7/8/8/8/8/8/8/NK6 w - - 0 1, false, false", // knight vs lone king
            "kr6/8/8/8/8/8/8/NK6 w - - 0 1, true, true", // a rook can hem its own king in against a knight
            "kq6/8/8/8/8/8/8/NK6 w - - 0 1, false, true", // a queen cannot: it would capture or be pinned
            "kn6/8/8/8/8/8/8/NK6 w - - 0 1, true, true", // knight vs knight
            "k7/1p6/8/8/8/8/8/BK6 w - - 0 1, true, true", // a pawn can block for a bishop
            "kr6/8/8/8/8/8/8/BK6 w - - 0 1, false, true", // a rook cannot block for a bishop
            "k1b5/8/8/8/8/8/8/1K3B2 w - - 0 1, false, false", // bishops on the same square color
            "k1b5/8/8/8/8/8/8/1KB5 w - - 0 1, true, true", // bishops on opposite square colors
            "k7/8/8/8/8/8/8/NNK5 w - - 0 1, true, false", // two knights
            "k7/8/8/8/8/8/8/BNK5 w - - 0 1, true, false", // bishop and knight
            "k7/8/8/8/8/8/8/1BBK4 w - - 0 1, true, false", // bishops on both square colors
        })
        void matingMaterialPerSide(String fen, boolean whiteCanMate, boolean blackCanMate) {
            Board board = Fen.parse(fen).getBoard();

            assertThat(board.hasMatingMaterial(Color.WHITE)).as("White").isEqualTo(whiteCanMate);
            assertThat(board.hasMatingMaterial(Color.BLACK)).as("Black").isEqualTo(blackCanMate);
        }
    }

    @Nested
    class ApplyAndUndo {

        @Test
        void undoRestoresTheExactPosition() {
            Position position = Position.startingPosition();
            String before = Fen.format(position);
            Move move = Move.doublePush(Square.fromNotation("e2"), Square.fromNotation("e4"));

            UndoInfo undo = position.applyMove(move);
            assertThat(Fen.format(position)).isNotEqualTo(before);

            position.undoMove(move, undo);
            assertThat(Fen.format(position)).isEqualTo(before);
        }

        @Test
        void undoRestoresACapturedPiece() {
            String fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
            Position position = Fen.parse(fen);
            Move capture = Move.normal(Square.fromNotation("e5"), Square.fromNotation("g6"), PieceType.KNIGHT);

            UndoInfo undo = position.applyMove(capture);
            position.undoMove(capture, undo);
            assertThat(Fen.format(position)).isEqualTo(fen);
        }
    }
}
