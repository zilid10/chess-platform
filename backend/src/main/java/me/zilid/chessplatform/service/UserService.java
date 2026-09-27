package me.zilid.chessplatform.service;

import me.zilid.chessplatform.exception.UserNotFoundException;
import me.zilid.chessplatform.model.converter.UserConverter;
import me.zilid.chessplatform.model.dto.UserCreateRequest;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.model.dto.UserUpdateRequest;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepo userRepo;
    private final UserConverter userConverter;

    public UserService(UserRepo userRepo, UserConverter userConverter) {
        this.userRepo = userRepo;
        this.userConverter = userConverter;
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        User user = userConverter.fromRequest(request);
        user = userRepo.save(user);
        logger.info("User created: {}", user.getUsername());
        return userConverter.toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UserUpdateRequest request) {
        logger.debug("Updating user with ID: {}", userId);
        User user = userRepo.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found!"));
        userConverter.updateEntityFromDto(user, request);
        logger.info("User updated successfully: {}", user.getUsername());
        return userConverter.toResponse(user);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        logger.info("Deleting user with ID: {}", userId);
        userRepo.deleteById(userId);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getUser(String search, Pageable pageable) {
        logger.debug("Searching for users with query: '{}', page: {}, size: {}", search, pageable.getPageNumber(), pageable.getPageSize());
        Page<User> users = userRepo.findByUsernameContainingIgnoreCase(search, pageable);
        logger.debug("Found {} users matching query '{}'", users.getTotalElements(), search);
        return users.map(userConverter::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        logger.debug("Fetching user by ID: {}", userId);
        User user = userRepo.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found!"));
        return userConverter.toResponse(user);
    }
}
