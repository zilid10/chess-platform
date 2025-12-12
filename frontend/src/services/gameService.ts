import api from './api';
import { MatchRecord, PageResponse } from '../types';

export const gameService = {
  getGames: async (userId: string, page: number = 0, size: number = 10): Promise<PageResponse<MatchRecord>> => {
    const response = await api.get(`/games/users/${userId}`, {
      params: { page, size }
    });
    return response.data;
  },

  getGamePGN: async (gameId: string): Promise<string> => {
    const response = await api.get(`/games/${gameId}`);
    return response.data;
  },
};
