package me.zilid.chessplatform.service;


import me.zilid.chessplatform.model.converter.FriendRequestConverter;
import me.zilid.chessplatform.model.converter.UserConverter;
import me.zilid.chessplatform.model.dto.FriendRequestResponse;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.model.entity.FriendRequest;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.FriendRequestRepo;
import me.zilid.chessplatform.repository.UserRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class FriendService {

    private final FriendRequestRepo friendRequestRepo;
    private final UserRepo userRepo;
    private final FriendRequestConverter friendRequestConverter;
    private final UserConverter userConverter;

    public FriendService(FriendRequestRepo friendRequestRepo, UserRepo userRepo, FriendRequestConverter friendRequestConverter, UserConverter userConverter) {
        this.friendRequestRepo = friendRequestRepo;
        this.userRepo = userRepo;
        this.friendRequestConverter = friendRequestConverter;
        this.userConverter = userConverter;
    }

    @Transactional
    public FriendRequestResponse createFriendRequest(UUID senderId, UUID recipientId) {
        if (senderId.equals(recipientId)) {
            throw new IllegalArgumentException("Cannot send friend request to yourself");
        }

        // check if they are already friends
        User sender = userRepo.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));
        User recipient = userRepo.findById(recipientId)
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found"));
        if (friendRequestRepo.existsFriendships(senderId, recipientId)) {
            throw new IllegalArgumentException("Users are already friends");
        }

        // check if there already exists pending friend request
        Optional<FriendRequest> existingRequest = friendRequestRepo
                .findBySender_IdAndRecipient_IdAndStatus(senderId, recipientId, FriendRequest.RequestStatus.PENDING);
        if (existingRequest.isPresent()) {
            throw new IllegalArgumentException("Friend request already sent");
        }

        FriendRequest friendRequest = friendRequestRepo.save(new FriendRequest(sender, recipient, FriendRequest.RequestStatus.PENDING));
        return friendRequestConverter.toResponse(friendRequest);
    }

    @Transactional
    public FriendRequestResponse acceptFriendRequest(UUID friendRequestId, UUID recipientId) {
        FriendRequest friendRequest = friendRequestRepo
                .findByIdAndRecipient_IdAndStatus(friendRequestId, recipientId,FriendRequest.RequestStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("no pending friend requests found"));

        friendRequest.setStatus(FriendRequest.RequestStatus.ACCEPTED);
        UUID senderId = friendRequest.getSender().getId();
        friendRequestRepo.addFriend(senderId, recipientId);
        return friendRequestConverter.toResponse(friendRequest);
    }

    @Transactional
    public void declineFriendRequest(UUID friendRequestId,  UUID recipientId) {
        FriendRequest friendRequest = friendRequestRepo
                .findByIdAndRecipient_IdAndStatus(friendRequestId, recipientId, FriendRequest.RequestStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("no pending friend requests found"));

        friendRequest.setStatus(FriendRequest.RequestStatus.REJECTED);
    }

    @Transactional(readOnly = true)
    public Page<FriendRequestResponse> getSentRequest(UUID senderId, Pageable pageable) {
        Page<FriendRequest> sentRequests = friendRequestRepo.findBySender_Id(senderId, pageable);
        return sentRequests.map(friendRequestConverter::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<FriendRequestResponse> getReceivedRequest(UUID recipientId, Pageable pageable) {
        Page<FriendRequest> receivedRequests = friendRequestRepo.findByRecipient_Id(recipientId, pageable);
        return receivedRequests.map(friendRequestConverter::toResponse);
    }

    @Transactional
    public void deleteFriendRequest(UUID friendRequestId) {
        friendRequestRepo.deleteById(friendRequestId);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getFriends(UUID userId, Pageable pageable) {
        Page<User> friends = friendRequestRepo.findFriendsByUserId(userId, pageable);
        return friends.map(userConverter::toResponse);
    }

    @Transactional
    public void deleteFriend(UUID userId, UUID friendId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        User friend = userRepo.findById(friendId).orElseThrow(() -> new IllegalArgumentException("Friend not found"));
        if (!friend.getFriends().contains(user)) {
            throw new IllegalArgumentException("friendship does not exist");
        }
        user.removeFriend(friend);
        friend.removeFriend(user);
    }
}
