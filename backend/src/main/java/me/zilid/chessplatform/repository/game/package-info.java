/**
 * Redis game persistence and locking. GameStateStore exposes engine games; the stored JSON record and
 * reconstruction converter are package-private implementation details. Seated players are stored with the game,
 * so loading one needs neither the database nor authentication principals. A sorted set indexes the games whose
 * clocks are running by timeout deadline.
 */
@NullMarked
package me.zilid.chessplatform.repository.game;

import org.jspecify.annotations.NullMarked;
