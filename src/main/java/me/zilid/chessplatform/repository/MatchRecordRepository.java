package me.zilid.chessplatform.repository;

import me.zilid.chessplatform.model.entity.MatchRecord;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MatchRecordRepository extends CrudRepository<MatchRecord, UUID> {
    
}
