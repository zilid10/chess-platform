package me.zilid.chessplatform.model.entity;

import jakarta.persistence.*;
import me.zilid.chessplatform.chess.game.TimeControl;

@Entity
@Table(
        name = "player_ratings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_player_rating_user_time_control",
                        columnNames = {"user_id", "time_control"}
                )
        }
)
public class PlayerRating extends AuditedBaseEntity {
    private static final int DEFAULT_RATING = 1200;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false, nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_control", updatable = false, nullable = false)
    private TimeControl timeControl;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "games_played", nullable = false)
    private int gamesPlayed;

    @Column(name = "peak_rating", nullable = false)
    private int peakRating;

    protected PlayerRating() {
    }

    public PlayerRating(User user, TimeControl timeControl) {
        this.user = user;
        this.timeControl = timeControl;
        this.rating = DEFAULT_RATING;
        this.gamesPlayed = 0;
        this.peakRating = DEFAULT_RATING;
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

    public void applyChanges(int newRating) {
        peakRating = Math.max(peakRating, newRating);
        rating = newRating;
        gamesPlayed++;
    }
}
