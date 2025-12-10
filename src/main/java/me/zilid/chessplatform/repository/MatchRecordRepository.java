package me.zilid.chessplatform.repository;

import me.zilid.chessplatform.model.entity.MatchRecord;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchRecordRepository extends CrudRepository<MatchRecord, String> {
    
}
