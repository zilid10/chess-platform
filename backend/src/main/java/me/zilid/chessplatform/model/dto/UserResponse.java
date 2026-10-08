package me.zilid.chessplatform.model.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record UserResponse(
        UUID id, String username, String email, @Nullable String about, Instant createdAt, Instant updatedAt) {}
