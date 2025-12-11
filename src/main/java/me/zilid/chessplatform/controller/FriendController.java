package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.model.dto.FriendRequestResponse;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.FriendService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController("/api")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping()
    public Page<UserResponse> getFriends(@AuthenticationPrincipal UserPrincipal userPrincipal, Pageable pageable) {
        return friendService.getFriends(userPrincipal.getId(), pageable);
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

    @PostMapping("/friends/accept/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    public void acceptFriendRequest(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                  @PathVariable("userId") UUID senderId) {
        UUID recipientId = userPrincipal.getId();
        friendService.acceptFriendRequest(senderId, recipientId);
    }

    @PutMapping("/friends/reject/{userId}")
    public void rejectFriendRequest(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                    @PathVariable("userId") UUID senderId) {
        UUID recipientId = userPrincipal.getId();
        friendService.declineFriendRequest(senderId, recipientId);
    }

    @DeleteMapping("/friends/{userId}")
    public void removeFriend(@AuthenticationPrincipal UserPrincipal userPrincipal,
                             @PathVariable("userId") UUID friendId) {
        UUID userId = userPrincipal.getId();
        friendService.deleteFriend(userId, friendId);
    }
}
