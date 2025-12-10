package me.zilid.chessplatform.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "match_records")
public class MatchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "white_user_id", nullable = false)
    private User whitePlayer;

    @ManyToOne
    @JoinColumn(name = "black_user_id", nullable = false)
    private User blackPlayer;

    private String matchResult;

    private String reason;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String pgn;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public MatchRecord(String id, User white, User black, String result, String reason, String pgn, LocalDateTime start) {
        this.id = id;
        this.whitePlayer = white;
        this.blackPlayer = black;
        this.matchResult = result;
        this.reason = reason;
        this.pgn = pgn;
        this.startTime = start;
        this.endTime = LocalDateTime.now();
    }

    public MatchRecord() {
    }
}