package me.zilid.chessplatform.service;

import jakarta.transaction.Transactional;
import me.zilid.chessplatform.model.converter.UserConverter;
import me.zilid.chessplatform.model.dto.UserCreateRequest;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.model.dto.UserUpdateRequest;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.UserRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepo userRepo;
    private final UserConverter userConverter;

    public UserService(PasswordEncoder passwordEncoder, UserRepo userRepo, UserConverter userConverter) {
        this.passwordEncoder = passwordEncoder;
        this.userRepo = userRepo;
        this.userConverter = userConverter;
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        User user = userConverter.toEntity(request);
        user = userRepo.save(user);
        return userConverter.toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UserUpdateRequest request) {
        User user = userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found!"));
        userConverter.updateEntityFromDto(request, user);
        if (request.rawPassword() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.rawPassword()));
        }

        return userConverter.toResponse(user);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        userRepo.deleteById(userId);
    }

    @Transactional
    public Page<UserResponse> getUser(String search, Pageable pageable) {
        Page<User> users = userRepo.findByUsernameContainingIgnoreCase(search, pageable);
        return users.map(userConverter::toResponse);
    }

    @Transactional
    public UserResponse getUserById(UUID userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found!"));
        return userConverter.toResponse(user);
    }

}
