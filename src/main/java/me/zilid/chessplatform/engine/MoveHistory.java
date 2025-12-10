package me.zilid.chessplatform.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages the history of moves in a chess game
 */
public class MoveHistory {

    private final List<Move> moves = new ArrayList<>();

    public void addMove(Move move) {
        moves.add(move);
    }

    public Move getMove(int index) {
        return moves.get(index);
    }

    public Move getLastMove() {
        if (moves.isEmpty()) {
            return null;
        }
        return moves.getLast();
    }

    public List<Move> getAllMoves() {
        return Collections.unmodifiableList(moves);
    }

    public int size() {
        return moves.size();
    }

    public int getRounds() {
        return (moves.size() / 2) + 1;
    }

    public boolean isEmpty() {
        return moves.isEmpty();
    }

    public void clear() {
        moves.clear();
    }

    /**
     * Get move history in PGN notation
     */
    public String getNotation() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < moves.size(); i++) {
            if (i % 2 == 0) {
                sb.append((i / 2) + 1).append(". ");
            }
            sb.append(moves.get(i).getNotation());
            if (i != moves.size()) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }
}
