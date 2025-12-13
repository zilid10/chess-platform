package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.engine.pieces.Piece;
import me.zilid.chessplatform.model.dto.GameCreatedResponse;
import me.zilid.chessplatform.model.dto.GameJoinResponse;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MatchRecordResponse;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.MatchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class GameController {

    private final MatchService matchService;

    public GameController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping("/games/users/{userId}")
    public Page<MatchRecordResponse> getGames(@PathVariable("userId") UUID userId, Pageable pageable) {
        return matchService.findMatches(userId, pageable);
    }

    @GetMapping("/games/{gameId}/pgn")
    public String getGamePGN(@PathVariable("gameId") UUID gameId) {
        return matchService.getMatchPGN(gameId);
    }

    @PostMapping("/games")
    @ResponseStatus(HttpStatus.CREATED)
    public GameCreatedResponse createGame(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam("color") Piece.Color color) {
        return matchService.createGame(currentUser, color);
    }

    @PostMapping("/games/{gameId}/join")
    public GameJoinResponse joinGame(
            @PathVariable UUID gameId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return  matchService.joinGame(gameId, currentUser);
    }

    @GetMapping("/games/{gameId}/state")
    public GameStateResponse getGameState(@PathVariable UUID gameId) {
        return matchService.getGameState(gameId);
    }
}
