package me.zilid.chessplatform.chess.format.pgn;

import java.util.List;
import me.zilid.chessplatform.chess.Move;
import me.zilid.chessplatform.chess.Position;

public class PgnFormatter {
    public static String format(Position replayPosition, List<Move> moveHistory) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < moveHistory.size(); i++) {
            if (i % 2 == 0) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append((i / 2) + 1).append(". ");
            } else {
                sb.append(' ');
            }
            Move move = moveHistory.get(i);
            String notation = SanFormatter.format(replayPosition, move);
            sb.append(notation);
            replayPosition.applyMove(move);
        }
        return sb.toString();
    }
}
