package me.zilid.chessplatform.model.entity;

import jakarta.persistence.*;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import me.zilid.chessplatform.chess.game.TimeControl;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "users")
public class User extends AuditedBaseEntity {
    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "about")
    private @Nullable String about;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_friendship",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "friend_id"))
    private Set<User> friends = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @MapKey(name = "timeControl")
    private Map<TimeControl, Rating> playerRatings = new EnumMap<>(TimeControl.class);

    protected User() {}

    public User(String email, String username, String passwordHash, @Nullable String about) {
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.about = about;
        for (TimeControl timeControl : TimeControl.values()) {
            Rating rating = new Rating(this, timeControl);
            playerRatings.put(timeControl, rating);
        }
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public @Nullable String getAbout() {
        return about;
    }

    public void setAbout(@Nullable String about) {
        this.about = about;
    }

    public Set<User> getFriends() {
        return friends;
    }

    public void addFriend(User other) {
        this.friends.add(other);
        other.friends.add(this);
    }

    public void removeFriend(User other) {
        this.friends.remove(other);
        other.friends.remove(this);
    }

    public Rating getPlayerRating(TimeControl timeControl) {
        if (!playerRatings.containsKey(timeControl)) {
            throw new IllegalStateException("error registering user");
        }
        return playerRatings.get(timeControl);
    }
}
