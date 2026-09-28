package me.zilid.chessplatform.repository.game;

import me.zilid.chessplatform.chess.game.Player;
import me.zilid.chessplatform.chess.game.RegisteredPlayer;

import java.util.UUID;

/**
 * Redis representation of a seated {@link Player}. {@link Player} is a sealed interface with no type information for
 * JSON, so it is stored through this record.
 */
record StoredPlayer(UUID id, String displayName) {

    static StoredPlayer of(Player player) {
        return switch (player) {
            case RegisteredPlayer registered -> new StoredPlayer(registered.id(), registered.displayName());
        };
    }

    Player toPlayer() {
        return new RegisteredPlayer(id, displayName);
    }
}
