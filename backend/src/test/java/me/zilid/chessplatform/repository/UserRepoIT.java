package me.zilid.chessplatform.repository;

import static me.zilid.chessplatform.util.EntityFixtures.friendRequest;
import static me.zilid.chessplatform.util.EntityFixtures.match;
import static me.zilid.chessplatform.util.EntityFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import me.zilid.chessplatform.model.entity.FriendRequest;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.util.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@RepositoryTest
class UserRepoIT {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private RatingRepo ratingRepo;

    @Autowired
    private FriendRequestRepo friendRequestRepo;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void lookupsByEmailAndUsernameMatchTheWholeValue() {
        User alice = user(entityManager, "alice");
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepo.findByEmail(alice.getEmail())).map(User::getId).contains(alice.getId());
        assertThat(userRepo.findByUsername(alice.getUsername()))
                .map(User::getId)
                .contains(alice.getId());
        assertThat(userRepo.findByUsername("alice")).isEmpty();
        assertThat(userRepo.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void usernameSearchIgnoresCaseAndPaginates() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User magnolia = entityManager.persist(newUser(tag + "-Magnolia"));
        User magnus = entityManager.persist(newUser(tag + "-Magnus"));
        entityManager.persist(newUser(tag + "-Hikaru"));
        entityManager.flush();

        String search = (tag + "-magn").toUpperCase();
        PageRequest firstUser = PageRequest.of(0, 1, Sort.by("username"));
        Page<User> firstPage = userRepo.findByUsernameContainingIgnoreCase(search, firstUser);
        Page<User> secondPage = userRepo.findByUsernameContainingIgnoreCase(search, firstUser.next());

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.map(User::getId).getContent()).containsExactly(magnolia.getId());
        assertThat(secondPage.map(User::getId).getContent()).containsExactly(magnus.getId());
    }

    @Test
    void emailMustBeUnique() {
        User alice = user(entityManager, "alice");
        entityManager.persist(new User(alice.getEmail(), "other-" + UUID.randomUUID(), "hash", null));

        assertThatThrownBy(entityManager::flush).hasMessageContaining("users_email_key");
    }

    @Test
    void usernameMustBeUnique() {
        User alice = user(entityManager, "alice");
        entityManager.persist(new User(UUID.randomUUID() + "@example.com", alice.getUsername(), "hash", null));

        assertThatThrownBy(entityManager::flush).hasMessageContaining("users_username_key");
    }

    @Test
    void auditingStampsCreationAndLaterModification() {
        User alice = user(entityManager, "alice");
        entityManager.flush();
        Instant createdAt = alice.getCreatedAt();
        Instant firstUpdatedAt = alice.getUpdatedAt();
        assertThat(createdAt).isNotNull();
        assertThat(firstUpdatedAt).isNotNull();

        alice.setAbout("Plays the Najdorf");
        entityManager.flush();

        assertThat(alice.getCreatedAt()).isEqualTo(createdAt);
        assertThat(alice.getUpdatedAt()).isAfter(firstUpdatedAt);
    }

    @Test
    void deletingUserRemovesTheirRatingsFriendshipsAndRequests() {
        User alice = user(entityManager, "alice");
        User bob = user(entityManager, "bob");
        alice.addFriend(bob);
        FriendRequest request = friendRequest(entityManager, bob, alice, FriendRequest.RequestStatus.PENDING);
        entityManager.flush();
        entityManager.clear();

        userRepo.deleteById(alice.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(ratingRepo.findByUser_Id(alice.getId())).isEmpty();
        assertThat(friendRequestRepo.existsFriendships(bob.getId(), alice.getId()))
                .isFalse();
        assertThat(friendRequestRepo.findById(request.getId())).isEmpty();
        assertThat(userRepo.existsById(bob.getId())).isTrue();
    }

    // Pins current behavior: match_records has no ON DELETE rule, so UserService.deleteUser fails for anyone who has
    // played a game. Update this test along with whichever fix is chosen.
    @Test
    void deletingUserWithMatchHistoryIsBlockedByTheMatchForeignKey() {
        User alice = user(entityManager, "alice");
        User bob = user(entityManager, "bob");
        match(entityManager, alice, bob, Instant.parse("2026-01-01T12:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        userRepo.deleteById(alice.getId());

        assertThatThrownBy(entityManager::flush).hasMessageContaining("match_records_white_user_id_fkey");
    }

    private static User newUser(String username) {
        return new User(username + "@example.com", username, "hash", null);
    }
}
