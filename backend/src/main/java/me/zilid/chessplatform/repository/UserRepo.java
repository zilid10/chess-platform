package me.zilid.chessplatform.repository;

import me.zilid.chessplatform.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepo extends CrudRepository<User, UUID> {
    User getReferenceById(UUID id);

    Optional<User> findByEmail(String email);
    
    Page<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    Optional<User> findByUsername(String username);
}
