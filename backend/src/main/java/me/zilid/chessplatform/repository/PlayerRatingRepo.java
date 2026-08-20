package me.zilid.chessplatform.repository;

import me.zilid.chessplatform.model.entity.PlayerRating;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PlayerRatingRepo extends CrudRepository<PlayerRating, UUID> {

}
