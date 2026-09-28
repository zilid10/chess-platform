package me.zilid.chessplatform.repository;

import jakarta.persistence.EntityManager;
import me.zilid.chessplatform.model.entity.MatchRecord;
import me.zilid.chessplatform.model.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:postgresql:.*")
class MatchRecordRepoIntegrationTest {

    @Autowired
    private MatchRecordRepo matchRecordRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private EntityManager entityManager;

    @Test
    void historyIncludesEitherColorAndPaginatesByLatestEndTime() {
        User alice = saveUser("alice");
        User bob = saveUser("bob");
        User carol = saveUser("carol");
        User dave = saveUser("dave");
        MatchRecord oldest = saveMatch(alice, bob, "2026-01-01T12:00:00Z");
        MatchRecord newest = saveMatch(bob, alice, "2026-01-03T12:00:00Z");
        MatchRecord middle = saveMatch(alice, carol, "2026-01-02T12:00:00Z");
        saveMatch(carol, dave, "2026-01-04T12:00:00Z");
        entityManager.flush();
        entityManager.clear();

        PageRequest firstTwo = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "endTime"));
        Page<MatchRecord> firstPage = matchRecordRepo.findByWhitePlayer_IdOrBlackPlayer_Id(
                alice.getId(), alice.getId(), firstTwo);
        Page<MatchRecord> secondPage = matchRecordRepo.findByWhitePlayer_IdOrBlackPlayer_Id(
                alice.getId(), alice.getId(), firstTwo.next());

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.map(MatchRecord::getId).getContent())
                .containsExactly(newest.getId(), middle.getId());
        assertThat(secondPage.map(MatchRecord::getId).getContent())
                .containsExactly(oldest.getId());
        assertThat(secondPage.getContent().getFirst().getPgn()).isEqualTo("1. e4 e5");
    }

    private User saveUser(String name) {
        String uniqueName = name + "-" + UUID.randomUUID();
        return userRepo.save(new User(uniqueName + "@example.com", uniqueName, "hash", null));
    }

    private MatchRecord saveMatch(User white, User black, String endTime) {
        MatchRecord match = new MatchRecord(
                white, black, "1-0", "CHECKMATE", "1. e4 e5",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse(endTime));
        return matchRecordRepo.save(match);
    }
}
