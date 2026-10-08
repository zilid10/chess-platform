package me.zilid.chessplatform.chess.game;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A player with an account; {@link #id()} is the user's id.
 *
 * <p>Equality uses the id only: usernames can change, so the same user may show up with an old display name from their
 * session and a new one from the database.
 */
public record RegisteredPlayer(UUID id, String displayName) implements Player {

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof RegisteredPlayer that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
