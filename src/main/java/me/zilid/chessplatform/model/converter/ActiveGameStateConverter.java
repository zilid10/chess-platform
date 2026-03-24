package me.zilid.chessplatform.model.converter;

import me.zilid.chessplatform.engine.Board;
import me.zilid.chessplatform.engine.ChessEngine;
import me.zilid.chessplatform.engine.Game;
import me.zilid.chessplatform.engine.GameSnapShot;
import me.zilid.chessplatform.model.dto.ActiveGameState;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.repository.UserRepo;
import me.zilid.chessplatform.service.UserPrincipalService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Component
public class ActiveGameStateConverter {

    private final UserPrincipalService userPrincipalService;

    public ActiveGameStateConverter(UserPrincipalService userPrincipalService) {
        this.userPrincipalService = userPrincipalService;
    }

    public ActiveGameState getGameSnapshot(Game game) {
        GameSnapShot snapShot = game.getGameSnapshot();
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
        Board board = new Board(state.fen());
        ChessEngine engine = new ChessEngine(board);
        return new Game(
                engine,
                new ArrayList<>(state.history()),
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
