package me.zilid.chessplatform.model.converter;

import me.zilid.chessplatform.model.dto.MatchRecordResponse;
import me.zilid.chessplatform.model.entity.MatchRecord;
import org.springframework.stereotype.Component;

@Component
public class MatchRecordConverter {
    private final UserConverter userConverter;

    public MatchRecordConverter(UserConverter userConverter) {
        this.userConverter = userConverter;
    }

    public MatchRecordResponse toResponse(MatchRecord matchRecord) {
        return new MatchRecordResponse(
                matchRecord.getId(),
                userConverter.toResponse(matchRecord.getWhitePlayer()),
                userConverter.toResponse(matchRecord.getBlackPlayer()),
                matchRecord.getMatchResult(),
                matchRecord.getReason(),
                matchRecord.getStartTime(),
                matchRecord.getEndTime()
        );
    }
}
