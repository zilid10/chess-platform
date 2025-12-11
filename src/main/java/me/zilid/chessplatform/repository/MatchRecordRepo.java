package me.zilid.chessplatform.repository;

import me.zilid.chessplatform.model.entity.MatchRecord;
import me.zilid.chessplatform.model.entity.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MatchRecordRepo extends CrudRepository<MatchRecord, UUID> {
    List<MatchRecord> findByWhitePlayer(User whitePlayer);

    List<MatchRecord> findByBlackPlayer(User blackPlayer);
}
