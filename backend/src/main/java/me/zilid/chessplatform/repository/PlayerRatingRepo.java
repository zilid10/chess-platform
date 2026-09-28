package me.zilid.chessplatform.repository;

import jakarta.persistence.LockModeType;
import me.zilid.chessplatform.chess.game.clock.TimeControl;
import me.zilid.chessplatform.model.entity.PlayerRating;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlayerRatingRepo extends CrudRepository<PlayerRating, UUID> {

    List<PlayerRating> findByUser_Id(UUID userId);

    /**
     * Load a rating row and hold a row lock until the transaction ends, so concurrent games
     * for the same player apply their rating changes one after another.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PlayerRating r where r.user.id = :userId and r.timeControl = :timeControl")
    Optional<PlayerRating> findForUpdate(@Param("userId") UUID userId, @Param("timeControl") TimeControl timeControl);
}
