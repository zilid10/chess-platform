import api from './api';
import { MatchRecord, PageResponse, GameCreatedResponse, GameJoinResponse, GameState, TimeControl } from '../types';

export const gameService = {
  createGame: async (color: 'WHITE' | 'BLACK', timeControl: TimeControl = 'RAPID'): Promise<GameCreatedResponse> => {
    const response = await api.post('/games', null, {
      params: { color, timeControl }
    });
    return response.data;
  },

  joinGame: async (gameId: string): Promise<GameJoinResponse> => {
    const response = await api.post(`/games/${gameId}/join`);
    return response.data;
  },

  getGameState: async (gameId: string): Promise<GameState> => {
    const response = await api.get(`/games/${gameId}/state`);
    return response.data;
  },

  getGames: async (userId: string, page: number = 0, size: number = 10): Promise<PageResponse<MatchRecord>> => {
    const response = await api.get(`/games/users/${userId}`, {
      params: { page, size }
    });
    return response.data;
  },

  getGamePGN: async (gameId: string): Promise<string> => {
    const response = await api.get(`/games/${gameId}/pgn`);
    return response.data;
  },
};
