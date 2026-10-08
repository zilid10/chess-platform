package me.zilid.chessplatform.model.dto;

import java.time.Instant;
import java.util.UUID;
import me.zilid.chessplatform.model.entity.FriendRequest;

public record FriendRequestResponse(
        UUID friendRequestId,
        UserResponse sender,
        UserResponse recipient,
        FriendRequest.RequestStatus status,
        Instant requestedAt,
        Instant updatedAt) {}
