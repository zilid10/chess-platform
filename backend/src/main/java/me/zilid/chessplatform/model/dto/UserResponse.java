package me.zilid.chessplatform.model.dto;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String username, String email, @Nullable String about,
                           Instant createdAt, Instant updatedAt) {
}
