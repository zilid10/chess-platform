import api from './api';
import { User, UserCreateRequest, UserUpdateRequest, PageResponse } from '../types';

export const userService = {
  createUser: async (request: UserCreateRequest): Promise<User> => {
    const response = await api.post('/users', request);
    return response.data;
  },

  updateUser: async (request: UserUpdateRequest): Promise<User> => {
    const response = await api.put('/users', request);
    return response.data;
  },

  deleteUser: async (): Promise<void> => {
    await api.delete('/users');
  },

  searchUsers: async (search: string, page: number = 0, size: number = 10): Promise<PageResponse<User>> => {
    const response = await api.get('/users', {
      params: { search, page, size }
    });
    return response.data;
  },

  login: async (username: string, password: string): Promise<{ token: string; user: User }> => {
    const response = await api.post('/auth/login', { username, password });
    return response.data;
  },

  getCurrentUser: async (): Promise<User> => {
    const response = await api.get('/auth/me');
    return response.data;
  },
};
