package me.zilid.chessplatform.model.converter;

import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.GameSnapshot;
import me.zilid.chessplatform.model.dto.ActiveGameState;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.UserPrincipalService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ActiveGameStateConverter {

    private final UserPrincipalService userPrincipalService;

    public ActiveGameStateConverter(UserPrincipalService userPrincipalService) {
        this.userPrincipalService = userPrincipalService;
    }

    public ActiveGameState toState(Game game) {
        GameSnapshot snapshot = game.getGameSnapshot();
        return new ActiveGameState(
                snapshot.history(),
                snapshot.startTime(),
                snapshot.endTime(),
                snapshot.timeControl(),
                snapshot.status(),
                snapshot.whitePlayerId(),
                snapshot.blackPlayerId(),
                snapshot.drawOfferedBy()
        );
    }

    public Game toGame(ActiveGameState state) {
        GameSnapshot snapshot = new GameSnapshot(
                state.history(),
                state.startTime(),
                state.endTime(),
                state.timeControl(),
                state.status(),
                state.whitePlayerId(),
                state.blackPlayerId(),
                state.drawOfferedBy()
        );
        return Game.fromSnapshot(snapshot, loadUser(state.whitePlayerId()), loadUser(state.blackPlayerId()));
    }

    private @Nullable UserPrincipal loadUser(@Nullable UUID userId) {
        return userId == null ? null : userPrincipalService.loadUserById(userId);
    }
}
