package me.zilid.chessplatform.chess.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import me.zilid.chessplatform.chess.Move;
import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.Square;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UciTest {

    @Test
    void parsesAndFormatsOrdinaryMove() {
        UciMove move = new UciMove(Square.fromNotation("e2"), Square.fromNotation("e4"), null);

        assertThat(Uci.parse("e2e4")).isEqualTo(move);
        assertThat(Uci.format(move)).isEqualTo("e2e4");
        assertThat(Uci.format(Move.doublePush(move.from(), move.to()))).isEqualTo("e2e4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"q", "r", "b", "n"})
    void parsesAndFormatsEveryPromotionChoice(String suffix) {
        String notation = "a7a8" + suffix;
        UciMove parsed = Uci.parse(notation);

        PieceType promotion = parsed.promotion();
        assertThat(promotion).isNotNull().isIn(Move.PROMOTION_CHOICES);
        assertThat(Uci.format(parsed)).isEqualTo(notation);
        assertThat(Uci.format(Move.promotion(parsed.from(), parsed.to(), promotion)))
                .isEqualTo(notation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "e2e", "e2e4qz", "i2e4", "e2e9", "e7e8k", "E2E4"})
    void rejectsMalformedMove(String notation) {
        assertThatThrownBy(() -> Uci.parse(notation)).isInstanceOf(IllegalArgumentException.class);
    }
}
