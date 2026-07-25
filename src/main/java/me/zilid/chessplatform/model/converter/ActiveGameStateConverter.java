package me.zilid.chessplatform.model.converter;

import me.zilid.chessplatform.chess.ChessEngine;
import me.zilid.chessplatform.chess.Game;
import me.zilid.chessplatform.chess.GameSnapShot;
import me.zilid.chessplatform.chess.Position;
import me.zilid.chessplatform.chess.formatter.Fen;
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
        Position position = Fen.parse(state.fen());
        ChessEngine engine = new ChessEngine(position);
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
