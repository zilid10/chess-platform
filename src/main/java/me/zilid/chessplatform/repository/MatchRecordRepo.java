package me.zilid.chessplatform.repository;

import me.zilid.chessplatform.model.entity.MatchRecord;
import me.zilid.chessplatform.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRecordRepo extends CrudRepository<MatchRecord, UUID> {
    Page<MatchRecord> findByWhitePlayer_IdOrBlackPlayer_Id(UUID whitePlayerId, UUID blackPlayerId, Pageable pageable);
}
