package me.zilid.chessplatform.model.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "match_records")
public class MatchRecord extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "white_user_id", updatable = false, nullable = false)
    private User whitePlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "black_user_id", updatable = false, nullable = false)
    private User blackPlayer;

    @Column(name = "result")
    private String matchResult;

    @Column(name = "reason")
    private String reason;

    @Column(name = "pgn")
    private String pgn;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    public MatchRecord(User white, User black) {
        this.whitePlayer = white;
        this.blackPlayer = black;
    }

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