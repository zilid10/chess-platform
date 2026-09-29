package me.zilid.chessplatform.model.converter;

import me.zilid.chessplatform.model.dto.UserCreateRequest;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.model.dto.UserUpdateRequest;
import me.zilid.chessplatform.model.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserConverter {

    private final PasswordEncoder passwordEncoder;

    public UserConverter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getAbout(), user.getCreatedAt(), user.getUpdatedAt());
    }

    public User fromRequest(UserCreateRequest request) {
        String passwordHash = passwordEncoder.encode(request.rawPassword());
        return new User(request.email(), request.username(), passwordHash, request.about());
    }

    public void updateEntityFromDto(User user, UserUpdateRequest request) {
        if (request.username() != null && !request.username().isEmpty()) {
            user.setUsername(request.username());
        }
        if (request.email() != null && !request.email().isEmpty()) {
            user.setEmail(request.email());
        }
        if (request.rawPassword() != null && !request.rawPassword().isEmpty()) {
            String passwordHash = passwordEncoder.encode(request.rawPassword());
            user.setPasswordHash(passwordHash);
        }
        if (request.about() != null) {
            user.setAbout(request.about());
        }
    }
}
