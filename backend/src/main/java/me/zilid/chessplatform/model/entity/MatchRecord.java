package me.zilid.chessplatform.model.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "match_records")
public class MatchRecord {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @ManyToOne
    @JoinColumn(name = "white_user_id", nullable = false)
    private User whitePlayer;

    @ManyToOne
    @JoinColumn(name = "black_user_id", nullable = false)
    private User blackPlayer;

    @Column(name = "result")
    private String matchResult;

    @Column(name = "reason")
    private String reason;

    @Column(name = "pgn", columnDefinition = "TEXT")
    private String pgn;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    public MatchRecord(User white, User black, String result, String reason, String pgn, Instant start) {
        this.whitePlayer = white;
        this.blackPlayer = black;
        this.matchResult = result;
        this.reason = reason;
        this.pgn = pgn;
        this.startTime = start;
        this.endTime = Instant.now();
    }

    public MatchRecord() {
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MatchRecord that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public UUID getId() {
        return id;
    }

    public User getWhitePlayer() {
        return whitePlayer;
    }

    public void setWhitePlayer(User whitePlayer) {
        this.whitePlayer = whitePlayer;
    }

    public User getBlackPlayer() {
        return blackPlayer;
    }

    public void setBlackPlayer(User blackPlayer) {
        this.blackPlayer = blackPlayer;
    }

    public String getMatchResult() {
        return matchResult;
    }

    public void setMatchResult(String matchResult) {
        this.matchResult = matchResult;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getPgn() {
        return pgn;
    }

    public void setPgn(String pgn) {
        this.pgn = pgn;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }
}