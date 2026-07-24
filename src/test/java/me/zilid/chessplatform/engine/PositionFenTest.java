package me.zilid.chessplatform.engine;

import me.zilid.chessplatform.engine.formatter.Fen;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for FEN parsing and serialization. Round-tripping (parse a FEN, write it
 * back out, expect the identical string) is the backbone check: it exercises piece
 * placement, side to move, castling rights, en passant target, and both clocks at once.
 */
class PositionFenTest {

    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";

    @Test
    void defaultBoardProducesStartingFen() {
        assertThat(Fen.write(Position.startingPosition())).isEqualTo(START);
    }

    @Test
    void startingPositionRoundTrips() {
        assertThat(Fen.write(Fen.read(START))).isEqualTo(START);
    }

    @Test
    void complexPositionRoundTrips() {
        assertThat(Fen.write(Fen.read(KIWIPETE))).isEqualTo(KIWIPETE);
    }

    @Test
    void placesPiecesOnCorrectSquares() {
        Position position = Fen.read(START);
        assertThat(position.getPieceAt(Square.fromNotation("e1")).getType()).isEqualTo(PieceType.KING);
        assertThat(position.getPieceAt(Square.fromNotation("e1")).getColor()).isEqualTo(Color.WHITE);
        assertThat(position.getPieceAt(Square.fromNotation("d8")).getType()).isEqualTo(PieceType.QUEEN);
        assertThat(position.getPieceAt(Square.fromNotation("d8")).getColor()).isEqualTo(Color.BLACK);
        assertThat(position.getPieceAt(Square.fromNotation("e4"))).isNull();
    }

    @Test
    void parsesSideToMove() {
        assertThat(Fen.read("7k/8/8/8/8/8/8/K7 b - - 0 1").getTurnColor()).isEqualTo(Color.BLACK);
    }

    @Test
    void parsesEnPassantTarget() {
        Position position = Fen.read("rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3");
        assertThat(position.getEnPassantTarget()).isEqualTo(Square.fromNotation("d6"));
    }

    @Test
    void rejectsMalformedFen() {
        assertThatThrownBy(() -> Fen.read("not a fen"))
                .isInstanceOf(IllegalArgumentException.class);
        // only seven ranks on the board
        assertThatThrownBy(() -> Fen.read("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1"))
                .isInstanceOf(IllegalArgumentException.class);
        // nine files in the first rank
        assertThatThrownBy(() -> Fen.read("rnbqkbnrr/pppppppp/8/8/8/8/8/RNBQKBNR w KQkq - 0 1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
