package me.zilid.chessplatform.repository;

import java.util.UUID;
import me.zilid.chessplatform.model.entity.MatchRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchRecordRepo extends CrudRepository<MatchRecord, UUID> {
    Page<MatchRecord> findByWhitePlayer_IdOrBlackPlayer_Id(UUID whitePlayerId, UUID blackPlayerId, Pageable pageable);
}
