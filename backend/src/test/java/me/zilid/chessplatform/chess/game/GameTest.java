package me.zilid.chessplatform.chess.game;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.Square;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameTest {
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static RegisteredPlayer player(String name) {
        return new RegisteredPlayer(UUID.randomUUID(), name);
    }

    private static Game game() {
        return new Game(player("white"), player("black"));
    }

    private static void play(Game game, String from, String to) {
        assertThat(game.makeMove(from, to, null)).as(from + to).isTrue();
    }

    @Test
    void invalidMoveLeavesBoardAndHistoryUnchanged() {
        Game game = game();

        assertThat(game.makeMove("e7", "e5", null)).isFalse();
        assertThat(game.makeMove("e2", "e5", null)).isFalse();
        assertThat(game.makeMove("bad", "e4", null)).isFalse();

        assertThat(game.getFen()).isEqualTo(START_FEN);
        assertThat(game.getMoves()).isEmpty();
        assertThat(game.getLastMoveFrom()).isNull();
        assertThat(game.getLastMoveTo()).isNull();
        assertThat(game.getTurnColor()).isEqualTo(Color.WHITE);
        assertThat(game.getStatus()).isEqualTo(GameStatus.ONGOING);
    }

    @Test
    void legalMoveUpdatesTurnHistoryAndLastMove() {
        Game game = game();
        assertThat(game.getValidMoves("e2"))
                .containsExactlyInAnyOrder(Square.fromNotation("e3"), Square.fromNotation("e4"));

        play(game, "e2", "e4");
        assertThat(game.getFen()).isEqualTo("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
        assertThat(game.getTurnColor()).isEqualTo(Color.BLACK);
        assertThat(game.getLastMoveFrom()).isEqualTo("e2");
        assertThat(game.getLastMoveTo()).isEqualTo("e4");
        assertThat(game.getMoves()).hasSize(1);
        assertThat(game.getRound()).isEqualTo(1);

        play(game, "e7", "e5");
        assertThat(game.getRound()).isEqualTo(2);
        assertThat(game.getMoves()).hasSize(2);
    }

    @Test
    void snapshotsAreIndependentOfLaterMoves() {
        Game game = game();
        GameSnapshot snapshot = game.getGameSnapshot();

        assertThat(snapshot.history()).isEmpty();
        play(game, "e2", "e4");
        assertThat(snapshot.history()).isEmpty();
        assertThatThrownBy(() -> snapshot.history().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void thirdOccurrenceOfStartingPositionEndsGame() {
        Game game = game();

        for (int cycle = 0; cycle < 2; cycle++) {
            play(game, "g1", "f3");
            play(game, "g8", "f6");
            play(game, "f3", "g1");
            play(game, "f6", "g8");
            assertThat(game.getStatus()).isEqualTo(cycle == 0 ? GameStatus.ONGOING : GameStatus.DRAW_BY_REPETITION);
        }

        assertThat(game.getEndTime()).isNotNull();
        assertThat(game.makeMove("e2", "e4", null)).isFalse();
    }

    @Test
    void restoredGameReplaysMovesAndKeepsMetadata() {
        Game game = new Game(player("white"), player("black"), TimeControl.BLITZ);
        play(game, "e2", "e4");
        play(game, "e7", "e5");
        game.offerDraw(Color.WHITE);

        Game restored = Game.fromSnapshot(game.getGameSnapshot(), game.getWhitePlayer(), game.getBlackPlayer());

        assertThat(restored.getFen()).isEqualTo(game.getFen());
        assertThat(restored.getMoves()).isEqualTo(game.getMoves());
        assertThat(restored.getStartTime()).isEqualTo(game.getStartTime());
        assertThat(restored.getTimeControl()).isEqualTo(TimeControl.BLITZ);
        assertThat(restored.getStatus()).isEqualTo(GameStatus.ONGOING);
        assertThat(restored.getDrawOfferedBy()).isEqualTo(Color.WHITE);
        assertThat(restored.getWhitePlayer()).isEqualTo(game.getWhitePlayer());
        assertThat(restored.getBlackPlayer()).isEqualTo(game.getBlackPlayer());
    }

    @Test
    void restoredGameStillCountsEarlierRepetitions() {
        Game game = game();
        play(game, "g1", "f3");
        play(game, "g8", "f6");
        play(game, "f3", "g1");
        play(game, "f6", "g8");
        play(game, "g1", "f3");
        play(game, "g8", "f6");
        play(game, "f3", "g1");

        Game restored = Game.fromSnapshot(game.getGameSnapshot(), game.getWhitePlayer(), game.getBlackPlayer());
        play(restored, "f6", "g8");

        assertThat(restored.getStatus()).isEqualTo(GameStatus.DRAW_BY_REPETITION);
    }

    @Test
    void snapshotOfGameWaitingForOpponentHasNoMissingPlayerId() {
        Game game = new Game(player("white"), null);

        GameSnapshot snapshot = game.getGameSnapshot();

        assertThat(snapshot.whitePlayerId()).isEqualTo(game.getWhitePlayer().id());
        assertThat(snapshot.blackPlayerId()).isNull();
    }

    @Test
    void playerIsRecognizedByIdAfterRenaming() {
        RegisteredPlayer white = player("white");
        Game game = new Game(white, player("black"));
        RegisteredPlayer renamed = new RegisteredPlayer(white.id(), "new-name");

        assertThat(game.isValidPlayer(renamed)).isTrue();
        assertThat(game.isUserTurn(renamed)).isTrue();
        assertThat(game.getPlayerColor(renamed)).isEqualTo(Color.WHITE);
    }

    @Test
    void oppositePlayerCanAcceptDrawAndGameStops() {
        Game game = game();
        game.offerDraw(Color.WHITE);
        game.acceptDraw(Color.WHITE);
        assertThat(game.getStatus()).isEqualTo(GameStatus.ONGOING);
        assertThat(game.getDrawOfferedBy()).isEqualTo(Color.WHITE);

        game.acceptDraw(Color.BLACK);
        assertThat(game.getStatus()).isEqualTo(GameStatus.DRAW_BY_AGREEMENT);
        assertThat(game.getDrawOfferedBy()).isNull();
        assertThat(game.getEndTime()).isNotNull();
        assertThat(game.makeMove("e2", "e4", null)).isFalse();
        assertThat(game.getFen()).isEqualTo(START_FEN);
    }

    @Test
    void resignationAwardsWinToOpponent() {
        Game game = game();
        game.resign(Color.WHITE);

        assertThat(game.getStatus()).isEqualTo(GameStatus.RESIGNED_BLACK_WINS);
        assertThat(game.getStatus().getSymbol()).isEqualTo("0-1");
        assertThat(game.getEndTime()).isNotNull();
        assertThat(game.makeMove("e2", "e4", null)).isFalse();
    }

    @Test
    void checkmateProducesPgnWithResult() {
        Game game = game();
        play(game, "f2", "f3");
        play(game, "e7", "e5");
        play(game, "g2", "g4");
        play(game, "d8", "h4");

        assertThat(game.getStatus()).isEqualTo(GameStatus.CHECKMATE_BLACK_WINS);
        assertThat(game.getNotation()).contains("[Result \"0-1\"]", "1. f3 e5 2. g4 Qh4#", "[Termination \"Black wins by checkmate\"]");
        assertThat(game.getNotation()).endsWith("0-1");
    }
}
