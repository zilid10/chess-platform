package me.zilid.chessplatform.model.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String username, String email, String about, Instant created,
                           Instant lastModified) {
}
