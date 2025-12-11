package me.zilid.chessplatform.service;

import me.zilid.chessplatform.engine.Game;
import me.zilid.chessplatform.model.converter.MatchRecordConverter;
import me.zilid.chessplatform.model.dto.MatchRecordResponse;
import me.zilid.chessplatform.model.entity.MatchRecord;
import me.zilid.chessplatform.repository.MatchRecordRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MatchService {

    private final MatchRecordRepo matchRecordRepo;
    private final MatchRecordConverter matchRecordConverter;

    public MatchService(MatchRecordRepo matchRecordRepo, MatchRecordConverter matchRecordConverter) {
        this.matchRecordRepo = matchRecordRepo;
        this.matchRecordConverter = matchRecordConverter;
    }

    @Transactional(readOnly = true)
    public Page<MatchRecordResponse> findMatches(UUID userId, Pageable pageable) {
        Page<MatchRecord> games = matchRecordRepo.findByWhitePlayer_IdOrBlackPlayer_Id(userId, userId, pageable);
        return games.map(matchRecordConverter::toResponse);
    }

    @Transactional(readOnly = true)
    public String getMatchPGN(UUID matchId) {
        MatchRecord matchRecord = matchRecordRepo.findById(matchId).orElseThrow(() -> new IllegalArgumentException("Game with id " + matchId + " does not exist"));
        return matchRecord.getPgn();
    }

    @Transactional
    public void archiveMatch(UUID matchId, Game game) {

    }
}
