package me.zilid.chessplatform.service;

import jakarta.transaction.Transactional;
import me.zilid.chessplatform.model.dto.UserCreateRequest;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.UserRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepo userRepo;

    public UserService(PasswordEncoder passwordEncoder, UserRepo userRepo) {
        this.passwordEncoder = passwordEncoder;
        this.userRepo = userRepo;
    }

    @Transactional
    public User createUser(UserCreateRequest request) {
        String passwordHashed = passwordEncoder.encode(request.rawPassword());
        User user = new User(request.email(), request.username(), passwordHashed, request.about());

        return userRepo.save(user);
    }

    @Transactional
    public User updateUser(UUID userId, UserCreateRequest request) {
        User user = userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found!"));
        String passwordHashed = passwordEncoder.encode(request.rawPassword());
        user.setUsername(request.username());
        user.setPasswordHash(passwordHashed);
        user.setEmail(request.email());
        user.setAbout(request.about());

        return user;
    }

    @Transactional
    public void deleteUser(UUID userId) {
        userRepo.deleteById(userId);
    }


}
