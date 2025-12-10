package me.zilid.chessplatform.model.dto;

public record MoveRequest(String gameId, String moveFrom, String moveTo, String promotion) {
}
