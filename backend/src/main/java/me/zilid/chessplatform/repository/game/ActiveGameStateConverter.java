package me.zilid.chessplatform.repository.game;

import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.GameSnapshot;
import me.zilid.chessplatform.chess.game.Player;
import me.zilid.chessplatform.chess.game.RegisteredPlayer;
import me.zilid.chessplatform.exception.UserNotFoundException;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.UserRepo;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ActiveGameStateConverter {

    private final UserRepo userRepo;

    ActiveGameStateConverter(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    ActiveGameState toState(Game game) {
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

    Game toGame(ActiveGameState state) {
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
        return Game.fromSnapshot(snapshot, loadPlayer(state.whitePlayerId()), loadPlayer(state.blackPlayerId()));
    }

    private @Nullable Player loadPlayer(@Nullable UUID userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        return new RegisteredPlayer(user.getId(), user.getUsername());
    }
}
