package me.zilid.chessplatform.model.entity;

import jakarta.persistence.*;
import me.zilid.chessplatform.chess.game.TimeControl;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "player_rating",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_player_rating_user_time_control",
                        columnNames = {"user_id", "time_control"}
                )
        }
)
public class Rating implements Persistable<UUID> {
    private static final int DEFAULT_RATING = 1200;

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_control", nullable = false)
    private TimeControl timeControl;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "games_played", nullable = false)
    private int gamesPlayed;

    @Column(name = "peak_rating", nullable = false)
    private int peakRating;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Rating() {
    }

    public Rating(User user, TimeControl timeControl) {
        this.user = user;
        this.timeControl = timeControl;
        this.rating = DEFAULT_RATING;
        this.gamesPlayed = 0;
        this.peakRating = DEFAULT_RATING;
    }

    @Override
    public boolean isNew() {
        return version == null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Rating r)) {
            return false;
        }
        return Objects.equals(getId(), r.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public TimeControl getTimeControl() {
        return timeControl;
    }

    public int getRating() {
        return rating;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public int getPeakRating() {
        return peakRating;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void applyChanges(int newRating) {
        peakRating = Math.max(peakRating, newRating);
        rating = newRating;
        gamesPlayed++;
    }
}
