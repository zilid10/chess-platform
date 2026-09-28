/**
 * Redis game persistence and locking. GameStateStore exposes engine games; the stored JSON record and
 * reconstruction converter are package-private implementation details. Player identities come from UserRepo,
 * without loading authentication principals.
 */
@NullMarked
package me.zilid.chessplatform.repository.game;

import org.jspecify.annotations.NullMarked;
