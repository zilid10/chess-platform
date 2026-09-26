package me.zilid.chessplatform.service;


import me.zilid.chessplatform.exception.FriendAlreadyExistsException;
import me.zilid.chessplatform.exception.UserNotFoundException;
import me.zilid.chessplatform.model.converter.FriendRequestConverter;
import me.zilid.chessplatform.model.converter.UserConverter;
import me.zilid.chessplatform.model.dto.FriendRequestResponse;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.model.entity.FriendRequest;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.FriendRequestRepo;
import me.zilid.chessplatform.repository.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class FriendService {
    private static final Logger logger = LoggerFactory.getLogger(FriendService.class);

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
                .orElseThrow(() -> new UserNotFoundException("Sender not found"));
        User recipient = userRepo.findById(recipientId)
                .orElseThrow(() -> new UserNotFoundException("Recipient not found"));
        if (friendRequestRepo.existsFriendships(senderId, recipientId)) {
            throw new FriendAlreadyExistsException("Users are already friends");
        }

        // check if there already exists pending friend request
        Optional<FriendRequest> existingRequest = friendRequestRepo
                .findBySender_IdAndRecipient_IdAndStatus(senderId, recipientId, FriendRequest.RequestStatus.PENDING);
        if (existingRequest.isPresent()) {
            throw new IllegalArgumentException("Friend request already sent");
        }
        Optional<FriendRequest> existingReverseRequest = friendRequestRepo
                .findBySender_IdAndRecipient_IdAndStatus(recipientId, senderId, FriendRequest.RequestStatus.PENDING);
        if (existingReverseRequest.isPresent()) {
            throw new IllegalArgumentException("Reverse friend request already sent");
        }

        FriendRequest friendRequest = friendRequestRepo.save(new FriendRequest(sender, recipient, FriendRequest.RequestStatus.PENDING));
        logger.info("Friend request created from {} to {}", sender.getUsername(), recipient.getUsername());
        return friendRequestConverter.toResponse(friendRequest);
    }

    @Transactional
    public FriendRequestResponse acceptFriendRequest(UUID friendRequestId, UUID recipientId) {
        FriendRequest friendRequest = friendRequestRepo
                .findByIdAndRecipient_IdAndStatus(friendRequestId, recipientId, FriendRequest.RequestStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("no pending friend requests found"));

        friendRequest.setStatus(FriendRequest.RequestStatus.ACCEPTED);
        UUID senderId = friendRequest.getSender().getId();
        int res = friendRequestRepo.addFriend(senderId, recipientId);
        logger.info("Friend request accepted: {} and {} are now friends, affected lines: {}",
                friendRequest.getSender().getUsername(), friendRequest.getRecipient().getUsername(), res);
        return friendRequestConverter.toResponse(friendRequest);
    }

    @Transactional
    public void declineFriendRequest(UUID friendRequestId, UUID recipientId) {
        FriendRequest friendRequest = friendRequestRepo
                .findByIdAndRecipient_IdAndStatus(friendRequestId, recipientId, FriendRequest.RequestStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("no pending friend requests found"));

        friendRequest.setStatus(FriendRequest.RequestStatus.REJECTED);
        logger.info("Friend request {} rejected by user {}", friendRequestId, recipientId);
    }

    @Transactional(readOnly = true)
    public Page<FriendRequestResponse> getSentRequest(UUID senderId, Pageable pageable) {
        logger.debug("Fetching sent friend requests for user: {}", senderId);
        Page<FriendRequest> sentRequests = friendRequestRepo.findBySender_IdAndStatus(senderId, FriendRequest.RequestStatus.PENDING, pageable);
        logger.debug("Found {} sent friend requests", sentRequests.getTotalElements());
        return sentRequests.map(friendRequestConverter::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<FriendRequestResponse> getReceivedRequest(UUID recipientId, Pageable pageable) {
        logger.debug("Fetching received friend requests for user: {}", recipientId);
        Page<FriendRequest> receivedRequests = friendRequestRepo.findByRecipient_IdAndStatus(recipientId, FriendRequest.RequestStatus.PENDING, pageable);
        logger.debug("Found {} received friend requests", receivedRequests.getTotalElements());
        return receivedRequests.map(friendRequestConverter::toResponse);
    }

    @Transactional
    public void deleteFriendRequest(UUID friendRequestId) {
        friendRequestRepo.deleteById(friendRequestId);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getFriends(UUID userId, Pageable pageable) {
        logger.debug("Fetching friends for user: {}", userId);
        Page<User> friends = friendRequestRepo.findFriendsByUserId(userId, pageable);
        logger.debug("User {} has {} friends", userId, friends.getTotalElements());
        return friends.map(userConverter::toResponse);
    }

    @Transactional
    public void deleteFriend(UUID userId, UUID friendId) {
        logger.info("Removing friendship between user {} and {}", userId, friendId);
        User user = userRepo.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        User friend = userRepo.findById(friendId).orElseThrow(() -> new UserNotFoundException("Friend not found"));
        if (!friend.getFriends().contains(user)) {
            throw new IllegalArgumentException("friendship does not exist");
        }
        user.removeFriend(friend);
        friend.removeFriend(user);
        logger.info("Friendship removed successfully between {} and {}", user.getUsername(), friend.getUsername());
    }
}
