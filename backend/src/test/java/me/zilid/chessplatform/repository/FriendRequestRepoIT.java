package me.zilid.chessplatform.repository;

import static me.zilid.chessplatform.util.EntityFixtures.friendRequest;
import static me.zilid.chessplatform.util.EntityFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.OptimisticLockException;
import java.sql.SQLException;
import java.util.Objects;
import me.zilid.chessplatform.model.entity.FriendRequest;
import me.zilid.chessplatform.model.entity.FriendRequest.RequestStatus;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.util.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@RepositoryTest
class FriendRequestRepoIT {

    @Autowired
    private FriendRequestRepo friendRequestRepo;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void addFriendWritesBothDirectionsOnce() {
        User alice = user(entityManager, "alice");
        User bob = user(entityManager, "bob");
        User carol = user(entityManager, "carol");
        entityManager.flush();

        assertThat(friendRequestRepo.addFriend(alice.getId(), bob.getId())).isEqualTo(2);
        assertThat(friendRequestRepo.addFriend(alice.getId(), bob.getId())).isZero();
        assertThat(friendRequestRepo.addFriend(bob.getId(), alice.getId())).isZero();

        assertThat(friendRequestRepo.existsFriendships(alice.getId(), bob.getId()))
                .isTrue();
        assertThat(friendRequestRepo.existsFriendships(bob.getId(), alice.getId()))
                .isTrue();
        assertThat(friendRequestRepo.existsFriendships(bob.getId(), carol.getId()))
                .isFalse();
    }

    @Test
    void addFriendRejectsBefriendingYourself() {
        User alice = user(entityManager, "alice");
        entityManager.flush();

        assertThatThrownBy(() -> friendRequestRepo.addFriend(alice.getId(), alice.getId()))
                .hasMessageContaining("user_friendship_check");
    }

    @Test
    void friendsArePaginatedFromEitherSide() {
        User alice = user(entityManager, "alice");
        User bob = user(entityManager, "bob");
        User carol = user(entityManager, "carol");
        entityManager.flush();
        friendRequestRepo.addFriend(alice.getId(), bob.getId());
        friendRequestRepo.addFriend(carol.getId(), alice.getId());

        PageRequest firstFriend = PageRequest.of(0, 1, Sort.by("username"));
        Page<User> firstPage = friendRequestRepo.findFriendsByUserId(alice.getId(), firstFriend);
        Page<User> secondPage = friendRequestRepo.findFriendsByUserId(alice.getId(), firstFriend.next());

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.map(User::getId).getContent()).containsExactly(bob.getId());
        assertThat(secondPage.map(User::getId).getContent()).containsExactly(carol.getId());
        assertThat(friendRequestRepo
                        .findFriendsByUserId(bob.getId(), firstFriend)
                        .map(User::getId)
                        .getContent())
                .containsExactly(alice.getId());
    }

    // FriendService.deleteFriend removes friendships through the entity mapping, not a query
    @Test
    void removingAFriendThroughTheMappingDeletesBothDirections() {
        User alice = user(entityManager, "alice");
        User bob = user(entityManager, "bob");
        entityManager.flush();
        friendRequestRepo.addFriend(alice.getId(), bob.getId());
        entityManager.clear();

        User loadedAlice = Objects.requireNonNull(entityManager.find(User.class, alice.getId()));
        User loadedBob = Objects.requireNonNull(entityManager.find(User.class, bob.getId()));
        loadedAlice.removeFriend(loadedBob);
        entityManager.flush();

        assertThat(friendRequestRepo.existsFriendships(alice.getId(), bob.getId()))
                .isFalse();
        assertThat(friendRequestRepo.existsFriendships(bob.getId(), alice.getId()))
                .isFalse();
    }

    @Test
    void pendingRequestQueriesFilterByParticipantAndStatus() {
        User alice = user(entityManager, "alice");
        User bob = user(entityManager, "bob");
        User carol = user(entityManager, "carol");
        FriendRequest aliceToBob = friendRequest(entityManager, alice, bob, RequestStatus.PENDING);
        friendRequest(entityManager, alice, carol, RequestStatus.REJECTED);
        FriendRequest carolToBob = friendRequest(entityManager, carol, bob, RequestStatus.PENDING);
        entityManager.flush();
        entityManager.clear();

        PageRequest page = PageRequest.of(0, 10);
        assertThat(friendRequestRepo
                        .findBySender_IdAndStatus(alice.getId(), RequestStatus.PENDING, page)
                        .map(FriendRequest::getId)
                        .getContent())
                .containsExactly(aliceToBob.getId());
        assertThat(friendRequestRepo
                        .findByRecipient_IdAndStatus(bob.getId(), RequestStatus.PENDING, page)
                        .map(FriendRequest::getId)
                        .getContent())
                .containsExactlyInAnyOrder(aliceToBob.getId(), carolToBob.getId());
        assertThat(friendRequestRepo
                        .findBySender_IdAndRecipient_IdAndStatus(alice.getId(), bob.getId(), RequestStatus.PENDING)
                        .map(FriendRequest::getId))
                .contains(aliceToBob.getId());
        assertThat(friendRequestRepo.findBySender_IdAndRecipient_IdAndStatus(
                        bob.getId(), alice.getId(), RequestStatus.PENDING))
                .isEmpty();
        assertThat(friendRequestRepo.findByIdAndRecipient_IdAndStatus(
                        aliceToBob.getId(), carol.getId(), RequestStatus.PENDING))
                .isEmpty();
        assertThat(friendRequestRepo.findByIdAndRecipient_IdAndStatus(
                        aliceToBob.getId(), bob.getId(), RequestStatus.REJECTED))
                .isEmpty();
    }

    @Test
    void schemaAllowsANewPendingRequestAfterAcceptanceButRejectsDuplicates() {
        User alice = user(entityManager, "alice");
        User bob = user(entityManager, "bob");
        FriendRequest accepted = friendRequest(entityManager, alice, bob, RequestStatus.PENDING);
        entityManager.flush();

        accepted.setStatus(RequestStatus.ACCEPTED);
        // Hibernate flushes inserts before updates, so the acceptance must reach the database first
        entityManager.flush();
        friendRequest(entityManager, alice, bob, RequestStatus.PENDING);
        entityManager.flush();

        friendRequest(entityManager, alice, bob, RequestStatus.PENDING);
        assertThatThrownBy(entityManager::flush)
                .hasRootCauseInstanceOf(SQLException.class)
                .hasMessageContaining("uq_friend_request_pending_sender_recipient");
    }

    @Test
    void updatingARequestThatAnotherTransactionChangedFails() {
        User alice = user(entityManager, "alice");
        User bob = user(entityManager, "bob");
        FriendRequest request = entityManager.persistFlushFind(new FriendRequest(alice, bob, RequestStatus.PENDING));

        // Another transaction cancels the request after this one loaded it
        entityManager
                .getEntityManager()
                .createNativeQuery(
                        "UPDATE friend_request SET status = 'CANCELLED', version = version + 1 WHERE id = :id")
                .setParameter("id", request.getId())
                .executeUpdate();
        request.setStatus(RequestStatus.ACCEPTED);

        assertThatThrownBy(entityManager::flush).isInstanceOf(OptimisticLockException.class);
    }
}
