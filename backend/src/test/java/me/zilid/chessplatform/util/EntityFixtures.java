package me.zilid.chessplatform.util;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import me.zilid.chessplatform.model.entity.FriendRequest;
import me.zilid.chessplatform.model.entity.MatchRecord;
import me.zilid.chessplatform.model.entity.User;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/** Persists entities for repository tests through JPA, so test setup never depends on the repository under test. */
public final class EntityFixtures {

    private EntityFixtures() {}

    /** Persist a user named {@code name} plus a random suffix, so tests never collide on the unique columns. */
    public static User user(TestEntityManager entityManager, String name) {
        String uniqueName = name + "-" + UUID.randomUUID();
        return entityManager.persist(new User(uniqueName + "@example.com", uniqueName, "hash", null));
    }

    /** Persist a ten-minute white checkmate win that ended at {@code endTime}. */
    public static MatchRecord match(TestEntityManager entityManager, User white, User black, Instant endTime) {
        return entityManager.persist(new MatchRecord(
                white, black, "1-0", "CHECKMATE", "1. e4 e5", endTime.minus(Duration.ofMinutes(10)), endTime));
    }

    public static FriendRequest friendRequest(
            TestEntityManager entityManager, User sender, User recipient, FriendRequest.RequestStatus status) {
        return entityManager.persist(new FriendRequest(sender, recipient, status));
    }
}
