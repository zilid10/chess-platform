package me.zilid.chessplatform.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    private String password;

    private int wins = 0;
    private int losses = 0;
    private int draws = 0;
    private LocalDateTime createdAt = LocalDateTime.now();

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public User() {
    }
}