import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WebSocketService } from './websocketService';
import type { ChatMessage, GameState } from '../types';

interface TestClient {
  active: boolean;
  connected: boolean;
  onConnect: () => void;
  subscriptions: Map<string, (message: { body: string }) => void>;
  activate: ReturnType<typeof vi.fn>;
  deactivate: ReturnType<typeof vi.fn>;
  publish: ReturnType<typeof vi.fn>;
}

const stomp = vi.hoisted(() => ({ clients: [] as unknown[] }));

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    active = false;
    connected = false;
    onConnect = () => {};
    onStompError = () => {};
    subscriptions = new Map<string, (message: { body: string }) => void>();
    activate = vi.fn();
    deactivate = vi.fn();
    publish = vi.fn();
    subscribe = vi.fn((destination: string, callback: (message: { body: string }) => void) => {
      this.subscriptions.set(destination, callback);
    });

    constructor() {
      stomp.clients.push(this);
    }
  },
}));

function latestClient(): TestClient {
  return stomp.clients[stomp.clients.length - 1] as TestClient;
}

describe('WebSocketService', () => {
  beforeEach(() => {
    stomp.clients.length = 0;
    vi.spyOn(console, 'log').mockImplementation(() => {});
    vi.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('subscribes to game, chat, and error messages before joining', () => {
    const service = new WebSocketService();
    const onGameUpdate = vi.fn();
    const onChatMessage = vi.fn();

    service.connect('game-1', 'alice', onGameUpdate, onChatMessage);
    const client = latestClient();
    expect(client.activate).toHaveBeenCalledOnce();

    client.onConnect();
    expect([...client.subscriptions.keys()]).toEqual([
      '/topic/game/game-1',
      '/topic/game/game-1/chat',
      '/user/queue/errors',
    ]);
    expect(client.publish).toHaveBeenCalledWith({ destination: '/app/game/game-1/join', body: '{}' });

    const state: GameState = { gameStatus: 'ONGOING', fen: 'example', turnColor: 'WHITE' };
    const chat: ChatMessage = { sender: 'bob', message: 'Hi', timestamp: 'now', type: 'CHAT' };
    client.subscriptions.get('/topic/game/game-1')?.({ body: JSON.stringify(state) });
    client.subscriptions.get('/topic/game/game-1/chat')?.({ body: JSON.stringify(chat) });

    expect(onGameUpdate).toHaveBeenCalledExactlyOnceWith(state);
    expect(onChatMessage).toHaveBeenCalledExactlyOnceWith(chat);
  });

  it('shows backend socket errors as system chat messages', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-23T12:00:00.000Z'));
    const service = new WebSocketService();
    const onChatMessage = vi.fn();
    service.connect('game-1', 'alice', vi.fn(), onChatMessage);
    const client = latestClient();
    client.onConnect();

    client.subscriptions.get('/user/queue/errors')?.({
      body: JSON.stringify({ error: 'Invalid input: illegal move' }),
    });

    expect(onChatMessage).toHaveBeenCalledExactlyOnceWith({
      sender: 'System',
      message: 'Invalid input: illegal move',
      timestamp: '2026-09-23T12:00:00.000Z',
      type: 'SYSTEM',
    });
  });

  it('does not start another connection while one is active', () => {
    const service = new WebSocketService();
    service.connect('game-1', 'alice', vi.fn(), vi.fn());
    latestClient().active = true;

    service.connect('game-1', 'alice', vi.fn(), vi.fn());

    expect(stomp.clients).toHaveLength(1);
  });

  it('publishes move, chat, resign, and draw commands to their destinations', () => {
    const service = new WebSocketService();
    service.connect('game-1', 'alice', vi.fn(), vi.fn());
    const client = latestClient();
    client.connected = true;

    service.sendMove('game-1', 'e7', 'e8', 'q');
    service.sendChatMessage('game-1', 'alice', 'Good game');
    service.resign('game-1', 'WHITE');
    service.offerDraw('game-1');
    service.acceptDraw('game-1');

    expect(client.publish.mock.calls.map(([message]) => message)).toEqual([
      {
        destination: '/app/game/game-1/move',
        body: JSON.stringify({ gameId: 'game-1', moveFrom: 'e7', moveTo: 'e8', promotion: 'q' }),
      },
      {
        destination: '/app/game/game-1/chat',
        body: JSON.stringify({ sender: 'alice', message: 'Good game', type: 'CHAT' }),
      },
      { destination: '/app/game/game-1/resign', body: '{}' },
      { destination: '/app/game/game-1/draw/offer', body: '' },
      { destination: '/app/game/game-1/draw/accept', body: '' },
    ]);
  });

  it('rejects commands without a connection and deactivates on disconnect', () => {
    const service = new WebSocketService();
    expect(() => service.sendMove('game-1', 'e2', 'e4')).toThrow('WebSocket not connected');
    expect(() => service.sendChatMessage('game-1', 'alice', 'Hi')).toThrow('WebSocket not connected');

    service.connect('game-1', 'alice', vi.fn(), vi.fn());
    const client = latestClient();
    service.disconnect();

    expect(client.deactivate).toHaveBeenCalledOnce();
    expect(() => service.offerDraw('game-1')).toThrow('WebSocket not connected');
  });
});
