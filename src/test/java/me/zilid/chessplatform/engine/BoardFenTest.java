package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.pieces.Piece;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for FEN parsing and serialization. Round-tripping (parse a FEN, write it
 * back out, expect the identical string) is the backbone check: it exercises piece
 * placement, side to move, castling rights, en passant target, and both clocks at once.
 */
class BoardFenTest {

    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";

    @Test
    void defaultBoardProducesStartingFen() {
        assertThat(new Board().getFen()).isEqualTo(START);
    }

    @Test
    void startingPositionRoundTrips() {
        assertThat(new Board(START).getFen()).isEqualTo(START);
    }

    @Test
    void complexPositionRoundTrips() {
        assertThat(new Board(KIWIPETE).getFen()).isEqualTo(KIWIPETE);
    }

    @Test
    void placesPiecesOnCorrectSquares() {
        Board board = new Board(START);
        assertThat(board.getPiece(Position.fromNotation("e1")).getType()).isEqualTo(Piece.PieceType.KING);
        assertThat(board.getPiece(Position.fromNotation("e1")).getColor()).isEqualTo(Piece.Color.WHITE);
        assertThat(board.getPiece(Position.fromNotation("d8")).getType()).isEqualTo(Piece.PieceType.QUEEN);
        assertThat(board.getPiece(Position.fromNotation("d8")).getColor()).isEqualTo(Piece.Color.BLACK);
        assertThat(board.getPiece(Position.fromNotation("e4"))).isNull();
    }

    @Test
    void parsesSideToMove() {
        assertThat(new Board("7k/8/8/8/8/8/8/K7 b - - 0 1").getTurnColor()).isEqualTo(Piece.Color.BLACK);
    }

    @Test
    void parsesEnPassantTarget() {
        Board board = new Board("rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3");
        assertThat(board.getEnPassantTarget()).isEqualTo(Position.fromNotation("d6"));
    }

    @Test
    void rejectsMalformedFen() {
        assertThatThrownBy(() -> new Board("not a fen"))
                .isInstanceOf(IllegalArgumentException.class);
        // only seven ranks on the board
        assertThatThrownBy(() -> new Board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1"))
                .isInstanceOf(IllegalArgumentException.class);
        // nine files in the first rank
        assertThatThrownBy(() -> new Board("rnbqkbnrr/pppppppp/8/8/8/8/8/RNBQKBNR w KQkq - 0 1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
