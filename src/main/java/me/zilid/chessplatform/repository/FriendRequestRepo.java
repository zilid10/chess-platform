package me.zilid.chessplatform.repository;

import me.zilid.chessplatform.model.entity.FriendRequest;
import me.zilid.chessplatform.model.entity.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FriendRequestRepo extends CrudRepository<FriendRequest, UUID> {

    List<FriendRequest> findBySenderAndStatus(User user, FriendRequest.RequestStatus status);

    List<FriendRequest> findByRecipientAndStatus(User recipient, FriendRequest.RequestStatus status);

    List<FriendRequest> findBySender_IdAndStatus(UUID senderId, FriendRequest.RequestStatus status);

    List<FriendRequest> findByRecipient_IdAndStatus(UUID recipientId, FriendRequest.RequestStatus status);

    List<FriendRequest> findBySender_IdAndRecipient_IdAndStatus(UUID senderId, UUID recipientId, FriendRequest.RequestStatus status);

    @Modifying
    @Query(value = """
            INSERT INTO user_friendship(user_id, friend_id)
            VALUES (:senderId, :recipientId), (:recipientId, :senderId)
            """, nativeQuery = true)
    void addFriend(UUID senderId, UUID recipientId);

    @Modifying
    @Query(value = """
            INSERT INTO friend_request(sender_id, recipient_id, status)
            VALUES (:senderId, :recipientId, :status)
            """, nativeQuery = true)
    void addFriendRequest(UUID senderId, UUID recipientId, FriendRequest.RequestStatus status);

    @Query(value = """
            SELECT EXISTS (
                        SELECT 1
                        FROM user_friendship
                        WHERE user_id = :userId AND friend_id = :friendId
                        )
            """, nativeQuery = true)
    boolean existsFriendships(@Param("userId") UUID userId, @Param("friendId") UUID friendId);
}
