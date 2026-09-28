package me.zilid.chessplatform.chess.game;

import java.util.UUID;

/**
 * Someone seated at a {@link Game}. Kept free of accounts and security so the chess code does not depend on them.
 */
public sealed interface Player permits RegisteredPlayer {
    UUID id();

    String displayName();
}
