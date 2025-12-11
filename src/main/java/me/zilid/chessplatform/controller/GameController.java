package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.model.dto.MatchRecordResponse;
import me.zilid.chessplatform.service.MatchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController("/api")
public class GameController {

    private final MatchService matchService;

    public GameController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping("/games/users/{userId}")
    public Page<MatchRecordResponse> getGames(@PathVariable("userId") UUID userId, Pageable pageable) {
        return matchService.findMatches(userId, pageable);
    }

    @GetMapping("/games/{gameId}")
    public String getGamePGN(@PathVariable("gameId") UUID gameId) {
        return matchService.getMatchPGN(gameId);
    }
}
