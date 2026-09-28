import { describe, expect, it } from 'vitest';
import { formatClock, remainingAt } from './gameClock';
import { GameState } from '../types';

const state = (overrides: Partial<GameState> = {}): GameState => ({
  gameStatus: 'ONGOING',
  fen: 'fen',
  turnColor: 'WHITE',
  whiteRemainingMillis: 60_000,
  blackRemainingMillis: 45_000,
  clockRunning: true,
  ...overrides,
});

describe('remainingAt', () => {
  it('counts down only the side to move', () => {
    expect(remainingAt(state(), 1_000, 11_000)).toEqual({ white: 50_000, black: 45_000 });
    expect(remainingAt(state({ turnColor: 'BLACK' }), 1_000, 11_000)).toEqual({ white: 60_000, black: 35_000 });
  });

  it('stops at zero', () => {
    expect(remainingAt(state(), 0, 120_000).white).toBe(0);
  });

  it('holds still before the clock starts and after the game ends', () => {
    expect(remainingAt(state({ clockRunning: false }), 0, 30_000)).toEqual({ white: 60_000, black: 45_000 });
    expect(remainingAt(state({ gameStatus: 'FLAGGED_BLACK_WINS' }), 0, 30_000))
      .toEqual({ white: 60_000, black: 45_000 });
  });

  it('never adds time when the local clock is behind', () => {
    expect(remainingAt(state(), 5_000, 4_000).white).toBe(60_000);
  });
});

describe('formatClock', () => {
  it.each([
    [300_000, '5:00'],
    [61_999, '1:01'],
    [10_000, '0:10'],
    [9_950, '0:09.9'],
    [400, '0:00.4'],
    [-5, '0:00.0'],
    [3_723_000, '1:02:03'],
  ])('formats %i ms as %s', (millis, expected) => {
    expect(formatClock(millis)).toBe(expected);
  });
});
