package me.zilid.chessplatform.exception;

public class GameIsOverException extends RuntimeException {
    public GameIsOverException(String gameIsAlreadyOver) {
        super(gameIsAlreadyOver);
    }
}
