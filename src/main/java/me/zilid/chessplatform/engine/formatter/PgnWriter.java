package me.zilid.chessplatform.engine.formatter;

import me.zilid.chessplatform.engine.Board;
import me.zilid.chessplatform.engine.Move;

import java.util.List;

public class PgnWriter {
    private static final SanFormatter sanFormatter = new SanFormatter();

    public String format(Board replayBoard, List<Move> moveHistory) {
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
            String notation = sanFormatter.format(replayBoard, move);
            sb.append(notation);
            replayBoard.applyMove(move);
        }
        return sb.toString();
    }
}
