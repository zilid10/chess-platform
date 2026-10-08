package me.zilid.chessplatform.repository;

import java.util.Optional;
import java.util.UUID;
import me.zilid.chessplatform.model.entity.FriendRequest;
import me.zilid.chessplatform.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendRequestRepo extends CrudRepository<FriendRequest, UUID> {

    Page<FriendRequest> findBySender_IdAndStatus(UUID senderId, FriendRequest.RequestStatus status, Pageable pageable);

    Page<FriendRequest> findByRecipient_IdAndStatus(
            UUID recipientId, FriendRequest.RequestStatus status, Pageable pageable);

    Optional<FriendRequest> findBySender_IdAndRecipient_IdAndStatus(
            UUID senderId, UUID recipientId, FriendRequest.RequestStatus status);

    @Query("SELECT f FROM User u JOIN u.friends f WHERE u.id = :userId")
    Page<User> findFriendsByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO user_friendship(user_id, friend_id)
            VALUES (:senderId, :recipientId), (:recipientId, :senderId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int addFriend(@Param("senderId") UUID senderId, @Param("recipientId") UUID recipientId);

    @Query(value = """
            SELECT EXISTS (
                        SELECT 1
                        FROM user_friendship
                        WHERE user_id = :userId AND friend_id = :friendId
                        )
            """, nativeQuery = true)
    boolean existsFriendships(@Param("userId") UUID userId, @Param("friendId") UUID friendId);

    Optional<FriendRequest> findByIdAndRecipient_IdAndStatus(
            UUID id, UUID recipientId, FriendRequest.RequestStatus status);
}
