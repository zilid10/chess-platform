package me.zilid.chessplatform.model.dto;

import me.zilid.chessplatform.model.entity.FriendRequest;

import java.time.Instant;
import java.util.UUID;

public record FriendRequestResponse(
        UUID friendRequestId,
        UserResponse sender,
        UserResponse recipient,
        FriendRequest.RequestStatus status,
        Instant requestedAt,
        Instant updatedAt
) {
}
