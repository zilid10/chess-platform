package me.zilid.chessplatform.model.converter;

import me.zilid.chessplatform.chess.Position;
import me.zilid.chessplatform.chess.format.Fen;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.GameSnapshot;
import me.zilid.chessplatform.model.dto.ActiveGameState;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.UserPrincipalService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;

@Component
public class ActiveGameStateConverter {

    private final UserPrincipalService userPrincipalService;

    public ActiveGameStateConverter(UserPrincipalService userPrincipalService) {
        this.userPrincipalService = userPrincipalService;
    }

    public ActiveGameState getGameSnapshot(Game game) {
        GameSnapshot snapShot = game.getGameSnapshot();
        return new ActiveGameState(
                snapShot.fen(),
                snapShot.history(),
                snapShot.positionHistory(),
                snapShot.startTime(),
                snapShot.endTime(),
                snapShot.status(),
                snapShot.whitePlayerId(),
                snapShot.blackPlayerId(),
                snapShot.drawOfferedBy()
        );
    }

    public Game toGame(ActiveGameState state) {
        UserPrincipal whiteUser = userPrincipalService.loadUserById(state.whitePlayerId());
        UserPrincipal blackUser = userPrincipalService.loadUserById(state.blackPlayerId());
        Position position = Fen.parse(state.fen());
        // TODO: fix this (undo history)
        return new Game(
                position,
                new ArrayList<>(state.history()),
                new ArrayList<>(),
                new HashMap<>(state.positionHistory()),
                state.startTime(),
                state.endTime(),
                state.status(),
                whiteUser,
                blackUser,
                state.drawOfferedBy()
        );
    }
}
