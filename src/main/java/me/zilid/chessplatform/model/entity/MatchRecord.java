package me.zilid.chessplatform.model.entity;

import jakarta.persistence.*;

import java.time.Instant;
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

    @Lob
    @Column(name = "pgn", columnDefinition = "TEXT")
    private String pgn;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    public MatchRecord(UUID id, User white, User black, String result, String reason, String pgn, Instant start) {
        this.id = id;
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
}