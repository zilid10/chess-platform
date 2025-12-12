export interface User {
  id: string;
  username: string;
  email: string;
  displayName?: string;
  createdAt?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface UserCreateRequest {
  username: string;
  email: string;
  rawPassword: string;
  about?: string;
}

export interface UserUpdateRequest {
  email?: string;
  displayName?: string;
  password?: string;
}

export interface FriendRequest {
  id: string;
  sender: User;
  recipient: User;
  createdAt: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
}

export interface MatchRecord {
  id: string;
  whitePlayer: User;
  blackPlayer: User;
  startTime: string;
  endTime?: string;
  result: string;
  pgn?: string;
}

export interface GameState {
  gameStatus: GameStatus;
  fen: string;
  lastMoveFrom?: string;
  lastMoveTo?: string;
  turnColor: 'WHITE' | 'BLACK';
}

export type GameStatus = 
  | 'ONGOING'
  | 'CHECKMATE_WHITE_WINS'
  | 'CHECKMATE_BLACK_WINS'
  | 'RESIGNED_WHITE_WINS'
  | 'RESIGNED_BLACK_WINS'
  | 'STALEMATE'
  | 'DRAW_BY_REPETITION'
  | 'DRAW_BY_FIFTY_MOVE_RULE'
  | 'DRAW_BY_INSUFFICIENT_MATERIAL'
  | 'DRAW_BY_AGREEMENT';

export interface MoveRequest {
  gameId: string;
  moveFrom: string;
  moveTo: string;
  promotion?: string;
}

export interface ChatMessage {
  sender: string;
  message: string;
  timestamp: string;
  type: 'CHAT' | 'JOIN' | 'LEAVE' | 'SYSTEM';
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
