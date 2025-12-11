package me.zilid.chessplatform.service;

import jakarta.transaction.Transactional;
import me.zilid.chessplatform.model.entity.FriendRequest;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.FriendRequestRepo;
import me.zilid.chessplatform.repository.UserRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FriendService {

    private final FriendRequestRepo friendRequestRepo;
    private final UserRepo userRepo;

    public FriendService(FriendRequestRepo friendRequestRepo, UserRepo userRepo) {
        this.friendRequestRepo = friendRequestRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public FriendRequest createFriendRequest(UUID senderId, UUID recipientId) {
        User sender = userRepo.findById(senderId).orElseThrow(() -> new IllegalArgumentException("Sender not found"));
        User recipient = userRepo.findById(recipientId).orElseThrow(() -> new IllegalArgumentException("Recipient not found"));
        if (sender.getFriends().contains(recipient)) {
            throw new IllegalArgumentException("Sender and recipient already exist");
        }

        FriendRequest friendRequest = new FriendRequest(sender, recipient, FriendRequest.RequestStatus.PENDING);
        return friendRequestRepo.save(friendRequest);
    }

    @Transactional
    public void acceptFriendRequest(UUID senderId, UUID recipientId) {
        List<FriendRequest> requests =
                friendRequestRepo.findBySender_IdAndRecipient_IdAndStatus(senderId, recipientId, FriendRequest.RequestStatus.PENDING);

        if (requests.isEmpty()) {
            throw new IllegalArgumentException("no pending friend requests found");
        }
        FriendRequest friendRequest = requests.getFirst();
        friendRequest.setStatus(FriendRequest.RequestStatus.ACCEPTED);
        friendRequestRepo.addFriend(senderId, recipientId);
    }

    @Transactional
    public void declineFriendRequest(UUID senderId, UUID recipientId) {
        List<FriendRequest> requests =
                friendRequestRepo.findBySender_IdAndRecipient_IdAndStatus(senderId, recipientId, FriendRequest.RequestStatus.PENDING);
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("no pending friend requests found");
        }

        FriendRequest friendRequest = requests.getFirst();
        friendRequest.setStatus(FriendRequest.RequestStatus.REJECTED);
    }

    @Transactional
    public void sendFriendRequest(UUID senderId, UUID recipientId) {
        List<FriendRequest> requests =
                friendRequestRepo.findBySender_IdAndRecipient_IdAndStatus(senderId, recipientId, FriendRequest.RequestStatus.PENDING);
        if (!requests.isEmpty()) {
            throw new IllegalArgumentException("pending friend requests found already exists");
        }
        friendRequestRepo.addFriendRequest(senderId, recipientId, FriendRequest.RequestStatus.PENDING);
    }
}
