package me.zilid.chessplatform.model.dto;

import java.time.LocalDateTime;

public record ChatMessage(String sender, String message, LocalDateTime timestamp, MessageType type) {
    public enum MessageType {
        CHAT, // Regular chat message
        JOIN, // Player joined notification
        LEAVE, // Player left notification
        SYSTEM // System message (game events, etc.)
    }

    // chat type default to CHAT
    public ChatMessage(String sender, String message) {
        this(sender, message, LocalDateTime.now(), MessageType.CHAT);
    }

    public ChatMessage(String sender, String message, MessageType type) {
        this(sender, message, LocalDateTime.now(), type);
    }
}
