package me.zilid.chessplatform.chess.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.Square;
import me.zilid.chessplatform.chess.format.Fen;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GameTest {
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static RegisteredPlayer player(String name) {
        return new RegisteredPlayer(UUID.randomUUID(), name);
    }

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static Game game() {
        return TestGames.game(player("white"), player("black"));
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
    void moveListIsReadOnly() {
        Game game = game();
        play(game, "e2", "e4");

        assertThatThrownBy(() -> game.getMoves().clear()).isInstanceOf(UnsupportedOperationException.class);
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
        Game game = new Game(player("white"), player("black"), ClockSetting.ofMinutes(3, 2));
        play(game, "e2", "e4");
        play(game, "e7", "e5");
        game.offerDraw(Color.WHITE);

        Game restored = TestGames.copy(game);

        assertThat(restored.getFen()).isEqualTo(game.getFen());
        assertThat(restored.getMoves()).isEqualTo(game.getMoves());
        assertThat(restored.getStartTime()).isEqualTo(game.getStartTime());
        assertThat(restored.getClockSetting()).isEqualTo(ClockSetting.ofMinutes(3, 2));
        assertThat(restored.getTimeControl()).isEqualTo(TimeControl.BLITZ);
        assertThat(restored.getWhiteRemaining()).isEqualTo(game.getWhiteRemaining());
        assertThat(restored.getBlackRemaining()).isEqualTo(game.getBlackRemaining());
        assertThat(restored.getTurnStartAt()).isEqualTo(game.getTurnStartAt());
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

        Game restored = TestGames.copy(game);
        play(restored, "f6", "g8");

        assertThat(restored.getStatus()).isEqualTo(GameStatus.DRAW_BY_REPETITION);
    }

    @Test
    void copyOfGameWaitingForOpponentKeepsTheOpenSeat() {
        Game game = TestGames.game(player("white"), null);

        Game restored = TestGames.copy(game);

        assertThat(restored.getWhitePlayer()).isEqualTo(game.getWhitePlayer());
        assertThat(restored.getBlackPlayer()).isNull();
    }

    @Test
    void playerIsRecognizedByIdAfterRenaming() {
        RegisteredPlayer white = player("white");
        Game game = TestGames.game(white, player("black"));
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
        assertThat(game.getNotation())
                .contains("[Result \"0-1\"]", "1. f3 e5 2. g4 Qh4#", "[Termination \"Black wins by checkmate\"]");
        assertThat(game.getNotation()).endsWith("0-1");
    }

    @Nested
    class Timeout {
        private final Duration tenMinutes = Duration.ofMinutes(10);

        // White moves at T0 and Black at T0 + 1s, which starts White's clock
        private Game gameWithRunningClock() {
            Game game = game();
            assertThat(game.makeMove("e2", "e4", null, T0)).isTrue();
            assertThat(game.makeMove("e7", "e5", null, T0.plusSeconds(1))).isTrue();
            return game;
        }

        @Test
        void chessClockDoesNotRunBeforeBlacksFirstMove() {
            Game game = game();
            game.makeMove("e2", "e4", null, T0);

            assertThat(game.isClockRunning()).isFalse();
            assertThat(game.getRemaining(Color.BLACK, T0.plusSeconds(20))).isEqualTo(tenMinutes);
            // Only the first-move window applies until then
            assertThat(game.timeoutDeadline()).isEqualTo(T0.plus(Game.FIRST_MOVE_TIMEOUT));
        }

        @Test
        void deadlineIsWhenTheSideToMoveRunsOut() {
            Game game = gameWithRunningClock();

            assertThat(game.timeoutDeadline()).isEqualTo(T0.plusSeconds(1).plus(tenMinutes));
            assertThat(game.isClockRunning()).isTrue();
            assertThat(game.getRemaining(Color.WHITE, T0.plusSeconds(61))).isEqualTo(Duration.ofMinutes(9));
            assertThat(game.getRemaining(Color.BLACK, T0.plusSeconds(61))).isEqualTo(tenMinutes);
        }

        @Test
        void checkTimeoutEndsTheGameOnlyOnceTheDeadlinePasses() {
            Game game = gameWithRunningClock();
            Instant deadline = game.timeoutDeadline();

            assertThat(game.hasTimedOut(deadline.minusMillis(1))).isFalse();
            assertThat(game.checkTimeout(deadline.minusMillis(1))).isFalse();
            assertThat(game.getStatus()).isEqualTo(GameStatus.ONGOING);

            assertThat(game.hasTimedOut(deadline)).isTrue();
            assertThat(game.checkTimeout(deadline)).isTrue();
            assertThat(game.getStatus()).isEqualTo(GameStatus.FLAGGED_BLACK_WINS);
            assertThat(game.getEndTime()).isEqualTo(deadline);
            assertThat(game.getRemaining(Color.WHITE, deadline.plusSeconds(5))).isZero();
            assertThat(game.timeoutDeadline()).isNull();
        }

        @Test
        void checkTimeoutIsIdempotent() {
            Game game = gameWithRunningClock();
            Instant late = game.timeoutDeadline().plusSeconds(5);

            assertThat(game.checkTimeout(late)).isTrue();
            assertThat(game.checkTimeout(late.plusSeconds(5))).isFalse();
            assertThat(game.getEndTime()).isEqualTo(late);
        }

        @Test
        void checkTimeoutDoesNotOverrideAnEarlierResult() {
            Game game = gameWithRunningClock();
            Instant late = game.timeoutDeadline().plusSeconds(1);
            game.resign(Color.BLACK);

            assertThat(game.checkTimeout(late)).isFalse();
            assertThat(game.getStatus()).isEqualTo(GameStatus.RESIGNED_WHITE_WINS);
        }

        @Test
        void aMoveAfterTheFlagEndsTheGameOnTimeInstead() {
            Game game = gameWithRunningClock();
            Instant late = game.timeoutDeadline().plusMillis(1);

            assertThat(game.makeMove("g1", "f3", null, late)).isTrue();

            assertThat(game.getStatus()).isEqualTo(GameStatus.FLAGGED_BLACK_WINS);
            assertThat(game.getMoves()).hasSize(2);
            assertThat(game.getLastMoveTo()).isEqualTo("e5");
        }

        @Test
        void incrementPushesTheDeadlineBack() {
            Game game = new Game(player("white"), player("black"), ClockSetting.ofMinutes(1, 2));
            game.makeMove("e2", "e4", null, T0);
            game.makeMove("e7", "e5", null, T0.plusSeconds(1));
            game.makeMove("g1", "f3", null, T0.plusSeconds(11));

            // Black's clock started at T0 + 11s with 60s + 2s from its first move
            assertThat(game.timeoutDeadline()).isEqualTo(T0.plusSeconds(11 + 62));
        }

        @Test
        void flagIsADrawWhenTheOpponentCannotCheckmate() {
            // White has a queen; Black's lone knight cannot mate without a blocker other than a queen
            Game game = Game.fromPosition(
                    Fen.parse("1n2k3/8/8/8/8/8/8/3QK3 w - - 0 1"),
                    player("white"),
                    player("black"),
                    TestGames.TEN_MINUTES);
            game.makeMove("e1", "f1", null, T0);
            game.makeMove("b8", "c6", null, T0.plusSeconds(1));

            assertThat(game.checkTimeout(game.timeoutDeadline())).isTrue();

            assertThat(game.getStatus()).isEqualTo(GameStatus.DRAW_BY_TIMEOUT_VS_INSUFFICIENT_MATERIAL);
            assertThat(game.getStatus().getSymbol()).isEqualTo("1/2-1/2");
        }

        @Test
        void flagIsALossWhenTheOpponentCouldStillCheckmate() {
            // A rook can block its own king in, so a knight could still mate
            Game game = Game.fromPosition(
                    Fen.parse("1n2k3/8/8/8/8/8/8/3RK3 w - - 0 1"),
                    player("white"),
                    player("black"),
                    TestGames.TEN_MINUTES);
            game.makeMove("e1", "f1", null, T0);
            game.makeMove("b8", "c6", null, T0.plusSeconds(1));

            assertThat(game.checkTimeout(game.timeoutDeadline())).isTrue();

            assertThat(game.getStatus()).isEqualTo(GameStatus.FLAGGED_BLACK_WINS);
        }
    }

    @Nested
    class FirstMoveAbort {
        private final RegisteredPlayer white = player("white");
        private final RegisteredPlayer black = player("black");
        private final Instant firstMoveDeadline = T0.plus(Game.FIRST_MOVE_TIMEOUT);

        private Game seatedAtT0() {
            return new Game(white, black, TestGames.TEN_MINUTES, T0);
        }

        @Test
        void noDeadlineWhileASeatIsOpen() {
            Game game = new Game(white, null, TestGames.TEN_MINUTES, T0);

            assertThat(game.getFirstMoveDeadline()).isNull();
            assertThat(game.timeoutDeadline()).isNull();
            assertThat(game.checkTimeout(T0.plus(Duration.ofHours(1)))).isFalse();
        }

        @Test
        void takingTheLastSeatStartsWhitesWindow() {
            Game game = new Game(white, null, TestGames.TEN_MINUTES, T0);

            game.seat(Color.BLACK, black, T0.plusSeconds(90));

            assertThat(game.getBlackPlayer()).isEqualTo(black);
            assertThat(game.timeoutDeadline()).isEqualTo(T0.plusSeconds(90).plus(Game.FIRST_MOVE_TIMEOUT));
        }

        @Test
        void gameCreatedWithBothPlayersStartsWhitesWindowAtOnce() {
            assertThat(seatedAtT0().timeoutDeadline()).isEqualTo(firstMoveDeadline);
        }

        @Test
        void whiteMissingTheWindowAbortsTheGame() {
            Game game = seatedAtT0();

            assertThat(game.checkTimeout(firstMoveDeadline.minusMillis(1))).isFalse();
            assertThat(game.checkTimeout(firstMoveDeadline)).isTrue();

            assertThat(game.getStatus()).isEqualTo(GameStatus.ABORTED);
            assertThat(game.getStatus().isWhiteWin()
                            || game.getStatus().isBlackWin()
                            || game.getStatus().isDraw())
                    .isFalse();
            assertThat(game.getEndTime()).isEqualTo(firstMoveDeadline);
            assertThat(game.timeoutDeadline()).isNull();
            assertThat(game.getRemaining(Color.WHITE, firstMoveDeadline)).isEqualTo(Duration.ofMinutes(10));
        }

        @Test
        void whitesFirstMoveStartsBlacksWindow() {
            Game game = seatedAtT0();
            game.makeMove("e2", "e4", null, T0.plusSeconds(20));

            Instant blackDeadline = T0.plusSeconds(20).plus(Game.FIRST_MOVE_TIMEOUT);
            assertThat(game.timeoutDeadline()).isEqualTo(blackDeadline);
            assertThat(game.checkTimeout(firstMoveDeadline)).isFalse();
            assertThat(game.checkTimeout(blackDeadline)).isTrue();
            assertThat(game.getStatus()).isEqualTo(GameStatus.ABORTED);
        }

        @Test
        void blacksFirstMoveEndsTheWindowAndStartsTheClock() {
            Game game = seatedAtT0();
            game.makeMove("e2", "e4", null, T0.plusSeconds(20));
            game.makeMove("e7", "e5", null, T0.plusSeconds(40));

            assertThat(game.getFirstMoveDeadline()).isNull();
            assertThat(game.isClockRunning()).isTrue();
            assertThat(game.timeoutDeadline()).isEqualTo(T0.plusSeconds(40).plus(Duration.ofMinutes(10)));
            assertThat(game.checkTimeout(T0.plus(Duration.ofMinutes(5)))).isFalse();
        }

        @Test
        void aFirstMoveAfterTheWindowAbortsInsteadOfPlaying() {
            Game game = seatedAtT0();

            assertThat(game.makeMove("e2", "e4", null, firstMoveDeadline.plusSeconds(1)))
                    .isTrue();

            assertThat(game.getStatus()).isEqualTo(GameStatus.ABORTED);
            assertThat(game.getMoves()).isEmpty();
        }

        @Test
        void copiesKeepTheWindow() {
            Game game = seatedAtT0();
            game.makeMove("e2", "e4", null, T0.plusSeconds(20));

            assertThat(TestGames.copy(game).getFirstMoveDeadline()).isEqualTo(game.getFirstMoveDeadline());
        }
    }
}
