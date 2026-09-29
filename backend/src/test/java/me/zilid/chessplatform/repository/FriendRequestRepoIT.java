package me.zilid.chessplatform.repository;

import jakarta.persistence.EntityManager;
import me.zilid.chessplatform.model.entity.FriendRequest;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.util.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class FriendRequestRepoIT {

    @Autowired
    private FriendRequestRepo friendRequestRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private EntityManager entityManager;

    @Test
    void addingFriendshipWritesBothDirectionsAndPaginatesFriends() {
        User alice = saveUser("alice");
        User bob = saveUser("bob");
        User carol = saveUser("carol");
        entityManager.flush();

        assertThat(friendRequestRepo.addFriend(alice.getId(), bob.getId())).isEqualTo(2);
        assertThat(friendRequestRepo.addFriend(alice.getId(), carol.getId())).isEqualTo(2);
        assertThat(friendRequestRepo.addFriend(alice.getId(), bob.getId())).isZero();
        entityManager.clear();

        assertThat(friendRequestRepo.existsFriendships(alice.getId(), bob.getId())).isTrue();
        assertThat(friendRequestRepo.existsFriendships(bob.getId(), alice.getId())).isTrue();
        assertThat(friendRequestRepo.existsFriendships(bob.getId(), carol.getId())).isFalse();

        PageRequest firstFriend = PageRequest.of(0, 1, Sort.by("username"));
        Page<User> firstPage = friendRequestRepo.findFriendsByUserId(alice.getId(), firstFriend);
        Page<User> secondPage = friendRequestRepo.findFriendsByUserId(alice.getId(), firstFriend.next());

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.map(User::getId).getContent()).containsExactly(bob.getId());
        assertThat(secondPage.map(User::getId).getContent()).containsExactly(carol.getId());
        assertThat(friendRequestRepo.findFriendsByUserId(bob.getId(), firstFriend)
                .map(User::getId).getContent()).containsExactly(alice.getId());
    }

    @Test
    void pendingRequestQueriesFilterByParticipantAndStatus() {
        User alice = saveUser("alice");
        User bob = saveUser("bob");
        User carol = saveUser("carol");
        FriendRequest aliceToBob = friendRequestRepo.save(
                new FriendRequest(alice, bob, FriendRequest.RequestStatus.PENDING));
        friendRequestRepo.save(new FriendRequest(alice, carol, FriendRequest.RequestStatus.REJECTED));
        FriendRequest carolToBob = friendRequestRepo.save(
                new FriendRequest(carol, bob, FriendRequest.RequestStatus.PENDING));
        entityManager.flush();
        entityManager.clear();

        PageRequest page = PageRequest.of(0, 10);
        assertThat(friendRequestRepo.findBySender_IdAndStatus(
                        alice.getId(), FriendRequest.RequestStatus.PENDING, page)
                .map(FriendRequest::getId).getContent()).containsExactly(aliceToBob.getId());
        assertThat(friendRequestRepo.findByRecipient_IdAndStatus(
                        bob.getId(), FriendRequest.RequestStatus.PENDING, page)
                .map(FriendRequest::getId).getContent())
                .containsExactlyInAnyOrder(aliceToBob.getId(), carolToBob.getId());
        assertThat(friendRequestRepo.findBySender_IdAndRecipient_IdAndStatus(
                        alice.getId(), bob.getId(), FriendRequest.RequestStatus.PENDING)
                .map(FriendRequest::getId)).contains(aliceToBob.getId());
        assertThat(friendRequestRepo.findByIdAndRecipient_IdAndStatus(
                aliceToBob.getId(), carol.getId(), FriendRequest.RequestStatus.PENDING)).isEmpty();
        assertThat(friendRequestRepo.findByIdAndRecipient_IdAndStatus(
                aliceToBob.getId(), bob.getId(), FriendRequest.RequestStatus.REJECTED)).isEmpty();
    }

    @Test
    void schemaAllowsANewPendingRequestAfterAcceptanceButRejectsDuplicates() {
        User alice = saveUser("alice");
        User bob = saveUser("bob");
        FriendRequest accepted = friendRequestRepo.save(
                new FriendRequest(alice, bob, FriendRequest.RequestStatus.PENDING));
        entityManager.flush();

        accepted.setStatus(FriendRequest.RequestStatus.ACCEPTED);
        entityManager.flush();
        friendRequestRepo.save(new FriendRequest(alice, bob, FriendRequest.RequestStatus.PENDING));
        entityManager.flush();

        friendRequestRepo.save(new FriendRequest(alice, bob, FriendRequest.RequestStatus.PENDING));
        assertThatThrownBy(() -> entityManager.flush())
                .hasRootCauseInstanceOf(SQLException.class)
                .hasMessageContaining("uq_friend_request_pending_sender_recipient");
    }

    private User saveUser(String name) {
        String uniqueName = name + "-" + UUID.randomUUID();
        return userRepo.save(new User(uniqueName + "@example.com", uniqueName, "hash", null));
    }
}
