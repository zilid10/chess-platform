import { Client, IMessage } from '@stomp/stompjs';
import { GameState, ChatMessage } from '../types';

export class WebSocketService {
  private client: Client | null = null;
  
  connect(gameId: string, _username: string, onGameUpdate: (state: GameState) => void, onChatMessage: (message: ChatMessage) => void) {

    if (this.client && this.client.active) {
      console.warn('WebSocket already active, skip connect');
      return;
    }

    this.client = new Client({
      brokerURL: 'ws://localhost:8080/ws',
      debug: (str) => {
        console.log('STOMP: ' + str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000
    });

    this.client.onConnect = () => {
      console.log('WebSocket connected');
      
      // Subscribe to game updates
      this.client?.subscribe(`/topic/game/${gameId}`, (message: IMessage) => {
        const gameState: GameState = JSON.parse(message.body);
        onGameUpdate(gameState);
      });

      // Subscribe to chat messages
      this.client?.subscribe(`/topic/game/${gameId}/chat`, (message: IMessage) => {
        const chatMessage: ChatMessage = JSON.parse(message.body);
        onChatMessage(chatMessage);
      });

      this.client?.subscribe(`/user/queue/errors`, (message: IMessage) => {
        const chatMessage: ChatMessage = JSON.parse(message.body);
        onChatMessage(chatMessage);
      })

      // Send join message
      this.client?.publish({
        destination: `/app/game/${gameId}/join`,
        body: '{}'
      });
    };

    this.client.onStompError = (frame) => {
      console.error('STOMP error:', frame);
    };

    this.client.activate();
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
  }

  sendMove(gameId: string, moveFrom: string, moveTo: string, promotion?: string) {
    if (!this.client || !this.client.connected) {
      throw new Error('WebSocket not connected');
    }

    this.client.publish({
      destination: `/app/game/${gameId}/move`,
      body: JSON.stringify({
        gameId,
        moveFrom,
        moveTo,
        promotion,
      }),
    });
  }

  sendChatMessage(gameId: string, sender: string, message: string) {
    if (!this.client || !this.client.connected) {
      throw new Error('WebSocket not connected');
    }

    this.client.publish({
      destination: `/app/game/${gameId}/chat`,
      body: JSON.stringify({
        sender,
        message,
        type: 'CHAT',
      }),
    });
  }

  resign(gameId: string, _playerColor: string) {
    if (!this.client || !this.client.connected) {
      throw new Error('WebSocket not connected');
    }

    this.client.publish({
      destination: `/app/game/${gameId}/resign`,
      body: '{}'
    });
  }

  offerDraw(gameId: string) {
    if (!this.client || !this.client.connected) {
      throw new Error('WebSocket not connected');
    }

    this.client.publish({
      destination: `/app/game/${gameId}/draw/offer`,
      body: '{}'
    });
  }
}

export const websocketService = new WebSocketService();
