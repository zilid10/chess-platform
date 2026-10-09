package me.zilid.chessplatform.chess.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.Piece;
import me.zilid.chessplatform.chess.Position;
import me.zilid.chessplatform.chess.Square;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for FEN parsing and serialization. Round-tripping (parse a FEN, format it back out, expect the identical
 * string) is the backbone check: it exercises piece placement, side to move, castling rights, en passant target, and
 * both clocks at once.
 */
class PositionFenTest {

    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";

    @Test
    void defaultBoardProducesStartingFen() {
        assertThat(Fen.format(Position.startingPosition())).isEqualTo(START);
    }

    @Test
    void startingPositionRoundTrips() {
        assertThat(Fen.format(Fen.parse(START))).isEqualTo(START);
    }

    @Test
    void complexPositionRoundTrips() {
        assertThat(Fen.format(Fen.parse(KIWIPETE))).isEqualTo(KIWIPETE);
    }

    @Test
    void positionWithoutCastlingRightsRoundTrips() {
        String fen = "7k/8/8/8/8/8/8/K7 b - - 12 34";
        assertThat(Fen.format(Fen.parse(fen))).isEqualTo(fen);
    }

    @Test
    void placesPiecesOnCorrectSquares() {
        Position position = Fen.parse(START);
        assertThat(position.getPieceAt(Square.fromNotation("e1"))).isEqualTo(Piece.WHITE_KING);
        assertThat(position.getPieceAt(Square.fromNotation("d8"))).isEqualTo(Piece.BLACK_QUEEN);
        assertThat(position.getPieceAt(Square.fromNotation("e4"))).isNull();
    }

    @Test
    void parsesSideToMove() {
        assertThat(Fen.parse("7k/8/8/8/8/8/8/K7 b - - 0 1").getTurnColor()).isEqualTo(Color.BLACK);
    }

    @Test
    void parsesEnPassantTarget() {
        Position position = Fen.parse("rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3");
        assertThat(position.getEnPassantTarget()).isEqualTo(Square.fromNotation("d6"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "'empty string', ''",
        "'not a fen', 'not a valid fen string'",
        "'only seven ranks on the board', 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1'",
        "'nine files in the first rank', 'rnbqkbnrr/pppppppp/8/8/8/8/8/RNBQKBNR w KQkq - 0 1'",
    })
    void rejectsMalformedFen(String name, String fen) {
        assertThatThrownBy(() -> Fen.parse(fen)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "'starting position', 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'",
        "'kiwipete', 'r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1'",
        "'position without castling rights', '7k/8/8/8/8/8/8/K7 b - - 12 34'",
    })
    void roundTripsValidFen(String name, String fen) {
        assertThat(Fen.format(Fen.parse(fen))).isEqualTo(fen);
    }
}
