import { GameState } from '../types';

export interface ClockReading {
  white: number;
  black: number;
}

/**
 * Each side's time left at `now`. The server sends the times as of when it built the state; the side to move keeps
 * losing time from when the state arrived, while the clock is running.
 */
export function remainingAt(state: GameState, receivedAt: number, now: number): ClockReading {
  const elapsed = state.gameStatus === 'ONGOING' && state.clockRunning ? Math.max(0, now - receivedAt) : 0;
  return {
    white: Math.max(0, state.whiteRemainingMillis - (state.turnColor === 'WHITE' ? elapsed : 0)),
    black: Math.max(0, state.blackRemainingMillis - (state.turnColor === 'BLACK' ? elapsed : 0)),
  };
}

/** m:ss, h:mm:ss from an hour up, and tenths of a second under ten seconds. */
export function formatClock(millis: number): string {
  const ms = Math.max(0, millis);
  if (ms < 10_000) {
    return `0:0${(Math.floor(ms / 100) / 10).toFixed(1)}`;
  }
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${seconds}` : `${minutes}:${seconds}`;
}
