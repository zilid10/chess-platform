package me.zilid.chessplatform.chess.format.pgn;

import me.zilid.chessplatform.chess.*;
import me.zilid.chessplatform.chess.format.Fen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SanPgnFormatterTest {
    private static Stream<Arguments> sanCases() {
        return Stream.of(
                Arguments.of("pawn push", "7k/8/8/8/8/8/4P3/K7 w - - 0 1", "e2", "e4", null, "e4"),
                Arguments.of("pawn capture", "7k/8/8/3p4/4P3/8/8/K7 w - - 0 1", "e4", "d5", null, "exd5"),
                Arguments.of("en passant", "7k/8/8/3pP3/8/8/8/K7 w - d6 0 1", "e5", "d6", null, "exd6"),
                Arguments.of("kingside castle", "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1", "g1", null, "O-O"),
                Arguments.of("queenside castle", "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1", "c1", null, "O-O-O"),
                Arguments.of("file disambiguation", "7k/8/8/8/8/8/3N3N/K7 w - - 0 1", "d2", "f3", null, "Ndf3"),
                Arguments.of("rank disambiguation", "7k/8/8/8/3N4/8/3N4/K7 w - - 0 1", "d2", "f3", null, "N2f3"),
                Arguments.of("full disambiguation", "7k/8/8/8/3N4/8/3N3N/K7 w - - 0 1", "d2", "f3", null, "Nd2f3"),
                Arguments.of("promotion with check", "7k/P7/8/8/8/8/8/K7 w - - 0 1", "a7", "a8", PieceType.QUEEN, "a8=Q+")
        );
    }

    private static Move legalMove(Position position, String from, String to, PieceType promotion) {
        return MoveGenerator.findLegalMove(position, Square.fromNotation(from), Square.fromNotation(to), promotion)
                .orElseThrow();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sanCases")
    void formatsSanWithoutChangingPosition(
            String name, String fen, String from, String to, PieceType promotion, String expected) {
        Position position = Fen.parse(fen);
        Move move = legalMove(position, from, to, promotion);

        assertThat(SanFormatter.format(position, move)).as(name).isEqualTo(expected);
        assertThat(Fen.format(position)).as(name + " leaves the position intact").isEqualTo(fen);
    }

    @Test
    void pgnNumbersMovesAndMarksCheckmate() {
        Position replay = Position.startingPosition();
        List<Move> moves = new ArrayList<>();
        for (String[] squares : new String[][]{
                {"f2", "f3"}, {"e7", "e5"}, {"g2", "g4"}, {"d8", "h4"}}) {
            Move move = legalMove(replay, squares[0], squares[1], null);
            moves.add(move);
            replay.applyMove(move);
        }

        assertThat(PgnFormatter.format(Position.startingPosition(), moves))
                .isEqualTo("1. f3 e5 2. g4 Qh4#");
    }
}
