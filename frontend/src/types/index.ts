export interface User {
  id: string;
  username: string;
  email: string;
  about?: string;
  createdAt?: string;
  updatedAt?: string;
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
  about?: string;
  rawPassword?: string;
}

export interface FriendRequest {
  friendRequestId: string;
  sender: User;
  recipient: User;
  requestedAt: string;
  updatedAt: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';
}

export type TimeControl = 'BULLET' | 'BLITZ' | 'RAPID' | 'CLASSICAL' | 'CORRESPONDENCE';

export const TIME_CONTROLS: TimeControl[] = ['BULLET', 'BLITZ', 'RAPID', 'CLASSICAL', 'CORRESPONDENCE'];

export const TIME_CONTROL_LABELS: Record<TimeControl, string> = {
  BULLET: 'Bullet',
  BLITZ: 'Blitz',
  RAPID: 'Rapid',
  CLASSICAL: 'Classical',
  CORRESPONDENCE: 'Correspondence',
};

export interface PlayerRating {
  timeControl: TimeControl;
  rating: number;
  gamesPlayed: number;
  peakRating: number;
}

export interface MatchRecord {
  id: string;
  whitePlayer: User;
  blackPlayer: User;
  startTime: string;
  endTime?: string;
  result: string;
  reason?: string;
  pgn?: string;
  // Rating fields are null for games played before ratings were tracked.
  timeControl?: TimeControl | null;
  whiteRating?: number | null;
  blackRating?: number | null;
  whiteRatingChange?: number | null;
  blackRatingChange?: number | null;
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

export interface GameCreatedResponse {
  gameId: string;
  color: 'WHITE' | 'BLACK';
  timeControl: TimeControl;
  fen: string;
  socketUrl: string;
}

export interface GameJoinResponse {
  gameId: string;
  role: string;
  timeControl: TimeControl;
  fen: string;
  status: GameStatus;
  currentTurn: string;
}
