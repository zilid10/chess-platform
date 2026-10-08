package me.zilid.chessplatform.controller;

import java.util.UUID;
import me.zilid.chessplatform.model.dto.FriendRequestResponse;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.security.UserPrincipal;
import me.zilid.chessplatform.service.FriendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class FriendController {
    private static final Logger logger = LoggerFactory.getLogger(FriendController.class);

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping("/friends")
    public Page<UserResponse> getFriends(@AuthenticationPrincipal UserPrincipal userPrincipal, Pageable pageable) {
        logger.debug("Fetching friends list for user: {}", userPrincipal.getUsername());
        return friendService.getFriends(userPrincipal.getId(), pageable);
    }

    @DeleteMapping("/friends/{userId}")
    public void removeFriend(
            @AuthenticationPrincipal UserPrincipal userPrincipal, @PathVariable("userId") UUID friendId) {
        UUID userId = userPrincipal.getId();
        logger.info("User {} removing friend with ID: {}", userPrincipal.getUsername(), friendId);
        friendService.deleteFriend(userId, friendId);
    }

    @GetMapping("/friends/received")
    public Page<FriendRequestResponse> getPendingFriendRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal, Pageable pageable) {
        logger.debug("Fetching received friend requests for user: {}", userPrincipal.getUsername());
        return friendService.getReceivedRequest(userPrincipal.getId(), pageable);
    }

    @GetMapping("/friends/sent")
    public Page<FriendRequestResponse> getSentFriendRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal, Pageable pageable) {
        logger.debug("Fetching sent friend requests for user: {}", userPrincipal.getUsername());
        return friendService.getSentRequest(userPrincipal.getId(), pageable);
    }

    @PostMapping("/friends/send/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestResponse sendFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal, @PathVariable("userId") UUID recipientId) {
        UUID senderId = userPrincipal.getId();
        logger.info("User {} sending friend request to user {}", userPrincipal.getUsername(), recipientId);
        return friendService.createFriendRequest(senderId, recipientId);
    }

    @PostMapping("/friends/accept/{friendRequestId}")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestResponse acceptFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("friendRequestId") UUID friendRequestId) {
        UUID recipientId = userPrincipal.getId();
        logger.info("User {} accepting friend request {}", userPrincipal.getUsername(), friendRequestId);
        return friendService.acceptFriendRequest(friendRequestId, recipientId);
    }

    @PutMapping("/friends/reject/{friendRequestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("friendRequestId") UUID friendRequestId) {
        UUID recipientId = userPrincipal.getId();
        logger.info("User {} rejecting friend request {}", userPrincipal.getUsername(), friendRequestId);
        friendService.declineFriendRequest(friendRequestId, recipientId);
    }
}
