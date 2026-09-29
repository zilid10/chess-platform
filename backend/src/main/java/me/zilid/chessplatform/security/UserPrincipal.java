package me.zilid.chessplatform.security;

import me.zilid.chessplatform.chess.game.RegisteredPlayer;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Authenticated identity stored in Java-serialized Redis sessions. Its qualified class name, serialized fields,
 * and serialVersionUID are part of the session format; changing any of them invalidates existing sessions.
 */
public class UserPrincipal implements UserDetails, CredentialsContainer {
    // Stored in Redis-backed sessions; keep stable so sessions survive redeploys
    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String username;
    private final String email;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;
    private @Nullable String passwordHash;

    public UserPrincipal(UUID id, String username, String email, String passwordHash, boolean enabled, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.authorities = authorities;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof UserPrincipal that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public UUID getId() {
        return id;
    }

    /**
     * This user as a {@link me.zilid.chessplatform.chess.game.Game} sees them.
     */
    public RegisteredPlayer toPlayer() {
        return new RegisteredPlayer(id, username);
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public @Nullable String getPassword() {
        return passwordHash;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
