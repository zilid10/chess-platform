import api from './api';
import { User, FriendRequest, PageResponse } from '../types';

export const friendService = {
  getFriends: async (page: number = 0, size: number = 10): Promise<PageResponse<User>> => {
    const response = await api.get('/friends', {
      params: { page, size }
    });
    return response.data;
  },

  getReceivedRequests: async (page: number = 0, size: number = 10): Promise<PageResponse<FriendRequest>> => {
    const response = await api.get('/friends/received', {
      params: { page, size }
    });
    return response.data;
  },

  getSentRequests: async (page: number = 0, size: number = 10): Promise<PageResponse<FriendRequest>> => {
    const response = await api.get('/friends/sent', {
      params: { page, size }
    });
    return response.data;
  },

  sendFriendRequest: async (userId: string): Promise<FriendRequest> => {
    const response = await api.post(`/friends/send/${userId}`);
    return response.data;
  },

  acceptFriendRequest: async (requestId: string): Promise<FriendRequest> => {
    const response = await api.post(`/friends/accept/${requestId}`);
    return response.data;
  },

  rejectFriendRequest: async (requestId: string): Promise<void> => {
    await api.put(`/friends/reject/${requestId}`);
  },

  removeFriend: async (userId: string): Promise<void> => {
    await api.delete(`/friends/${userId}`);
  },
};
