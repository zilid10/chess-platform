package me.zilid.chessplatform.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotEmpty(message = "username must be provided")
        @Size(min = 3, max = 20, message = "username length can't exceed 100")
        String username,

        @NotEmpty(message = "password must be provided")
        @Size(min = 3, max = 32, message = "password length must between 10 and 32")
        String password) {}
