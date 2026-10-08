package me.zilid.chessplatform.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UserUpdateRequest(
        @Size(min = 3, max = 20, message = "username length must between 3 and 20") @Nullable
        String username,

        @Size(max = 100, message = "email length can't exceed 100") @Email @Nullable
        String email,

        @Size(min = 3, max = 32, message = "password length must between 10 and 32") @Nullable
        String rawPassword,

        @Size(max = 1000, message = "about length can't exceed 1000") @Nullable
        String about) {}
