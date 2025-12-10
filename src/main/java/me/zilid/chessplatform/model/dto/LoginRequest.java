package me.zilid.chessplatform.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record LoginRequest (
        @NotEmpty(message = "email must be provided")
        @Size(max = 100, message = "email length can't exceed 100")
        @Email
        String email,

        @NotEmpty(message = "password must be provided")
        @Size(min = 10, max = 32, message = "password length must between 10 and 32")
        String password
) {
}
