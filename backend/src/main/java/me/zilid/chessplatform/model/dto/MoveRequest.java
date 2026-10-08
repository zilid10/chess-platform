package me.zilid.chessplatform.model.dto;

import org.jspecify.annotations.Nullable;

public record MoveRequest(
        String gameId,
        String moveFrom,
        String moveTo,
        @Nullable String promotion) {}
