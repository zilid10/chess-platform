package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.model.dto.FriendRequestResponse;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.model.entity.FriendRequest;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.FriendService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController()
@RequestMapping("/api")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping("/friends")
    public Page<UserResponse> getFriends(@AuthenticationPrincipal UserPrincipal userPrincipal, Pageable pageable) {
        return friendService.getFriends(userPrincipal.getId(), pageable);
    }

    @DeleteMapping("/friends/{userId}")
    public void removeFriend(@AuthenticationPrincipal UserPrincipal userPrincipal,
                             @PathVariable("userId") UUID friendId) {
        UUID userId = userPrincipal.getId();
        friendService.deleteFriend(userId, friendId);
    }

    @GetMapping("/friends/received")
    public Page<FriendRequestResponse> getPendingFriendRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal, Pageable pageable) {
        return friendService.getReceivedRequest(userPrincipal.getId(), pageable);
    }

    @GetMapping("/friends/sent")
    public Page<FriendRequestResponse> getSentFriendRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal, Pageable pageable) {
        return friendService.getSentRequest(userPrincipal.getId(), pageable);
    }

    @PostMapping("/friends/send/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestResponse sendFriendRequest(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                  @PathVariable("userId") UUID recipientId) {
        UUID senderId = userPrincipal.getId();
        return friendService.createFriendRequest(senderId, recipientId);
    }

    @PostMapping("/friends/accept/{friendRequestId}")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestResponse acceptFriendRequest(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                    @PathVariable("friendRequestId") UUID friendRequestId) {
        UUID recipientId = userPrincipal.getId();
        return friendService.acceptFriendRequest(friendRequestId, recipientId);
    }

    @PutMapping("/friends/reject/{friendRequestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectFriendRequest(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                    @PathVariable("friendRequestId") UUID friendRequestId) {
        UUID recipientId = userPrincipal.getId();
        friendService.declineFriendRequest(friendRequestId, recipientId);
    }

}
