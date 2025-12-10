package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.engine.Game;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MoveRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.HttpServerErrorException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Controller
public class GameSocketController {
    // Game is safe to use in multiple thread.
    private static final ConcurrentMap<String, Game> gameSessions = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(GameSocketController.class);

    @MessageMapping("/game/{gameId}/join")
    public void joinGame(@DestinationVariable String gameId,
                         @Payload String username,
                         SimpMessageHeaderAccessor headerAccessor) { // <--- 关键参数
        headerAccessor.getSessionAttributes().put("gameId", gameId);
        headerAccessor.getSessionAttributes().put("username", username);

        logger.info("User {} joined game {}", username, gameId);

    }

    @MessageMapping("/game/{gameId}/move")
    @SendTo("/update/{gameId}")
    public GameStateResponse movePiece(@DestinationVariable String gameId, @Payload MoveRequest moveRequest) {
        Game game = gameSessions.get(gameId);

        throw new IllegalArgumentException("TO DO");
    }

}
