package me.zilid.chessplatform.model.converter;

import me.zilid.chessplatform.model.dto.FriendRequestResponse;
import me.zilid.chessplatform.model.entity.FriendRequest;
import org.springframework.stereotype.Component;

@Component
public class FriendRequestConverter {
    private final UserConverter userConverter;

    public FriendRequestConverter(UserConverter userConverter) {
        this.userConverter = userConverter;
    }

    public FriendRequestResponse toResponse(FriendRequest friendRequest) {
        return new FriendRequestResponse(
                friendRequest.getId(),
                userConverter.toResponse(friendRequest.getSender()),
                userConverter.toResponse(friendRequest.getRecipient()),
                friendRequest.getStatus(),
                friendRequest.getRequestedAt(),
                friendRequest.getUpdatedAt()
        );
    }

}
