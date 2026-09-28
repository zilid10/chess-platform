import { useState, useEffect, useRef } from 'react';
import { apiErrorMessage } from '../services/errors';
import { useParams, useNavigate } from 'react-router-dom';
import { Chessboard } from 'react-chessboard';
import { Chess } from 'chess.js';
import { websocketService } from '../services/websocketService';
import { gameService } from '../services/gameService';
import { useAuth } from '../context/AuthContext';
import { GameState, ChatMessage as ChatMessageType, TIME_CONTROL_LABELS, TimeControl } from '../types';
import { firstMoveLeftAt, formatClock, remainingAt } from '../components/gameClock';
import { Copy, Flag, Scale, Send } from 'lucide-react';

const Game = () => {
  const { gameId } = useParams<{ gameId: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [game, setGame] = useState(new Chess());
  const [gameState, setGameState] = useState<GameState | null>(null);
  const [chatMessages, setChatMessages] = useState<ChatMessageType[]>([]);
  const [chatInput, setChatInput] = useState('');
  const chatContainerRef = useRef<HTMLDivElement>(null);
  const [copied, setCopied] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [playerColor, setPlayerColor] = useState<'white' | 'black'>('white');
  const [drawOffered, setDrawOffered] = useState(false);
  const [isPlayer, setIsPlayer] = useState(true);
  const [timeControl, setTimeControl] = useState<TimeControl | null>(null);
  const [clockSetting, setClockSetting] = useState<string | null>(null);
  const [stateReceivedAt, setStateReceivedAt] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  const lastTimeoutClaimRef = useRef(0);
  const iOfferedDrawRef = useRef(false);

  useEffect(() => {
    if (!gameId || !user) return;
    let cancelled = false;

    const initializeGame = async () => {
      try {
        setLoading(true);
        setError('');

        // Step 1: Join the game via REST API
        const joinResponse = await gameService.joinGame(gameId);
        if (cancelled) return;
        
        // Check if user is a player or spectator
        const isActualPlayer = joinResponse.role === 'WHITE' || joinResponse.role === 'BLACK';
        setIsPlayer(isActualPlayer);
        setTimeControl(joinResponse.timeControl);
        setClockSetting(joinResponse.clockSetting);
        
        // Set player color based on role (role is like "WHITE" or "BLACK")
        if (isActualPlayer) {
          const color = joinResponse.role.toLowerCase() as 'white' | 'black';
          setPlayerColor(color);
        }
        
        // Set initial game state from join response
        if (joinResponse.fen) {
          const newGame = new Chess(joinResponse.fen);
          setGame(newGame);
        }

        // Step 2: Connect to WebSocket
        websocketService.connect(
          gameId,
          user.username,
          (state: GameState) => {
            const receivedAt = Date.now();
            setGameState(state);
            setStateReceivedAt(receivedAt);
            setNow(receivedAt);
            if (state.fen) {
              const newGame = new Chess(state.fen);
              setGame(newGame);
            }
          },
          (message: ChatMessageType) => {
            setChatMessages((prev) => [...prev, message]);
            
            // Check if this is a draw offer message
            if (message.type === 'SYSTEM' && message.message === 'Draw offered' && message.sender === 'System') {
              // Only set drawOffered if WE didn't offer the draw
              if (!iOfferedDrawRef.current) {
                setDrawOffered(true);
              }
            }
            
            // Clear draw offer if game ended or draw was agreed/declined
            if (message.type === 'SYSTEM' && (message.message === 'Draw agreed' || message.message.includes('wins'))) {
              setDrawOffered(false);
              iOfferedDrawRef.current = false;
            }
          }
        );

        setLoading(false);
      } catch (err) {
        if (cancelled) return;
        console.error('Error initializing game:', err);
        setError(apiErrorMessage(err, 'Failed to join game'));
        setLoading(false);
      }
    };

    initializeGame();

    return () => {
      cancelled = true;
      websocketService.disconnect();
    };
  }, [gameId, user]);

  const clockTicking = gameState?.gameStatus === 'ONGOING'
    && (gameState.clockRunning || gameState.firstMoveRemainingMillis != null);

  useEffect(() => {
    if (!clockTicking) return;
    const timer = setInterval(() => setNow(Date.now()), 100);
    return () => clearInterval(timer);
  }, [clockTicking]);

  const clocks = gameState ? remainingAt(gameState, stateReceivedAt, now) : null;
  const sideToMoveLeft = clocks && gameState?.clockRunning
    ? (gameState.turnColor === 'WHITE' ? clocks.white : clocks.black)
    : null;
  const firstMoveLeft = gameState ? firstMoveLeftAt(gameState, stateReceivedAt, now) : null;

  useEffect(() => {
    // The server ends games on time by itself; claiming the flag only makes the result show up sooner.
    // Keep claiming once a second in case this browser's clock runs ahead of the server's.
    if (!gameId || !isPlayer || sideToMoveLeft === null || sideToMoveLeft > 0) return;
    if (now - lastTimeoutClaimRef.current < 1000) return;
    lastTimeoutClaimRef.current = now;
    try {
      websocketService.claimTimeout(gameId);
    } catch (err) {
      console.warn('Could not claim timeout:', err);
    }
  }, [gameId, isPlayer, sideToMoveLeft, now]);

  useEffect(() => {
    // Scroll chat to bottom
    if (chatContainerRef.current) {
      chatContainerRef.current.scrollTop = chatContainerRef.current.scrollHeight;
    }
  }, [chatMessages]);

  const onDrop = (sourceSquare: string, targetSquare: string) => {
    if (!gameId || !gameState || gameState.gameStatus !== 'ONGOING') {
      return false;
    }

    try {
      const gameCopy = new Chess(game.fen());
      const move = gameCopy.move({
        from: sourceSquare,
        to: targetSquare,
        promotion: 'q', // Always promote to queen for simplicity
      });

      if (move === null) return false;

      console.log(`onDrop ${sourceSquare} -> ${targetSquare}`);
      websocketService.sendMove(gameId, sourceSquare, targetSquare, 'q');
      return true;
    } catch {
      return false;
    }
  };

  const handleSendChat = () => {
    if (!gameId || !user || !chatInput.trim()) return;
    
    websocketService.sendChatMessage(gameId, user.username, chatInput.trim());
    setChatInput('');
  };

  const handleResign = () => {
    if (!gameId || !confirm('Are you sure you want to resign?')) return;
    
    // Determine player color based on game state
    const playerColor = gameState?.turnColor || 'WHITE';
    websocketService.resign(gameId, playerColor);
  };

  const handleOfferDraw = () => {
    if (!gameId || !confirm('Offer a draw?')) return;
    
    iOfferedDrawRef.current = true;
    websocketService.offerDraw(gameId);
  };

  const handleAcceptDraw = () => {
    if (!gameId) return;
    
    websocketService.acceptDraw(gameId);
    setDrawOffered(false);
  };

  const handleDeclineDraw = () => {
    setDrawOffered(false);
    // Note: we don't reset iOfferedDraw here because the offer is still pending
  };

  const copyGameId = () => {
    if (gameId) {
      navigator.clipboard.writeText(gameId);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const getStatusMessage = () => {
    if (!gameState) return 'Waiting for players...';
    
    const status = gameState.gameStatus;
    if (status === 'ONGOING') {
      return `${gameState.turnColor}'s turn`;
    }
    
    if (status.includes('CHECKMATE')) {
      return status.includes('WHITE') ? 'White wins by checkmate!' : 'Black wins by checkmate!';
    }
    
    if (status.includes('RESIGNED')) {
      return status.includes('WHITE') ? 'White wins by resignation!' : 'Black wins by resignation!';
    }
    
    if (status === 'ABORTED') {
      return 'Game aborted - a first move did not come in time';
    }

    if (status.includes('FLAGGED')) {
      return status.includes('WHITE') ? 'White wins on time!' : 'Black wins on time!';
    }

    if (status === 'DRAW_BY_TIMEOUT_VS_INSUFFICIENT_MATERIAL') {
      return 'Draw - time ran out, but the opponent could not checkmate';
    }

    if (status.includes('DRAW')) {
      return 'Game drawn';
    }
    
    if (status.includes('STALEMATE')) {
      return 'Stalemate - Draw!';
    }
    
    return status;
  };

  const renderClock = (color: 'WHITE' | 'BLACK') => {
    if (!clocks || !gameState) return null;
    const millis = color === 'WHITE' ? clocks.white : clocks.black;
    const active = gameState.gameStatus === 'ONGOING' && gameState.clockRunning && gameState.turnColor === color;
    return (
      <div className="flex justify-end my-2">
        <span
          data-testid={`clock-${color.toLowerCase()}`}
          className={`font-mono text-xl px-3 py-1 rounded ${
            active ? (millis < 10_000 ? 'bg-red-600 text-white' : 'bg-gray-900 text-white') : 'bg-gray-100 text-gray-700'
          }`}
        >
          {formatClock(millis)}
        </span>
      </div>
    );
  };

  const topColor = playerColor === 'white' ? 'BLACK' : 'WHITE';
  const bottomColor = playerColor === 'white' ? 'WHITE' : 'BLACK';

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex items-center justify-center h-96">
          <div className="text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto mb-4"></div>
            <p className="text-gray-600">Joining game...</p>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="card bg-red-50">
          <h2 className="text-xl font-semibold text-red-800 mb-2">Error</h2>
          <p className="text-red-600">{error}</p>
          <button
            onClick={() => navigate('/dashboard')}
            className="mt-4 btn btn-primary"
          >
            Back to Dashboard
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Draw Offer Notification - Only show to players */}
      {isPlayer && drawOffered && gameState?.gameStatus === 'ONGOING' && (
        <div className="mb-6 card bg-blue-50 border-2 border-blue-500">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-lg font-semibold text-blue-900">Draw Offered</h3>
              <p className="text-sm text-blue-700">Your opponent has offered a draw</p>
            </div>
            <div className="flex space-x-3">
              <button
                onClick={handleAcceptDraw}
                className="btn btn-primary"
              >
                Accept Draw
              </button>
              <button
                onClick={handleDeclineDraw}
                className="btn btn-secondary"
              >
                Decline
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Chess Board */}
        <div className="lg:col-span-2">
          <div className="card">
            <div className="mb-4 flex justify-between items-center">
              <div>
                <h2 className="text-2xl font-bold text-gray-900">
                  Chess Game
                  {timeControl && (
                    <span className="ml-2 text-sm font-medium text-gray-500">
                      Rated {TIME_CONTROL_LABELS[timeControl]}{clockSetting && ` · ${clockSetting}`}
                    </span>
                  )}
                </h2>
                <p className="text-sm text-gray-600 mt-1">{getStatusMessage()}</p>
                {firstMoveLeft !== null && gameState && (
                  <p className="text-sm text-amber-700 mt-1">
                    {gameState.turnColor === 'WHITE' ? 'White' : 'Black'} must move within{' '}
                    {Math.ceil(firstMoveLeft / 1000)}s or the game is aborted
                  </p>
                )}
              </div>
              <div className="flex items-center space-x-2">
                <button
                  onClick={copyGameId}
                  className="btn btn-secondary flex items-center space-x-2"
                  title="Copy Game ID"
                >
                  <Copy size={16} />
                  <span>{copied ? 'Copied!' : 'Copy ID'}</span>
                </button>
              </div>
            </div>
            
            <div className="w-full max-w-2xl mx-auto">
              {renderClock(topColor)}
              <Chessboard
                position={game.fen()}
                onPieceDrop={onDrop}
                boardWidth={560}
                boardOrientation={playerColor}
              />
              {renderClock(bottomColor)}
            </div>

            {isPlayer && gameState?.gameStatus === 'ONGOING' && (
              <div className="mt-6 flex space-x-4">
                <button
                  onClick={handleResign}
                  className="btn btn-danger flex items-center space-x-2"
                >
                  <Flag size={16} />
                  <span>Resign</span>
                </button>
                <button
                  onClick={handleOfferDraw}
                  className="btn btn-secondary flex items-center space-x-2"
                >
                  <Scale size={16} />
                  <span>Offer Draw</span>
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Chat Panel */}
        <div className="lg:col-span-1">
          <div className="card h-[600px] flex flex-col">
            <h3 className="text-lg font-semibold mb-4">Chat</h3>
            
            <div
              ref={chatContainerRef}
              className="flex-1 overflow-y-auto space-y-2 mb-4 pr-2"
            >
              {chatMessages.map((msg, index) => (
                <div
                  key={index}
                  className={`p-2 rounded ${
                    msg.type === 'SYSTEM'
                      ? 'bg-blue-50 text-blue-800'
                      : msg.type === 'JOIN' || msg.type === 'LEAVE'
                      ? 'bg-gray-100 text-gray-600'
                      : 'bg-white border'
                  }`}
                >
                  <div className="flex justify-between items-start text-xs text-gray-500 mb-1">
                    <span className="font-semibold">{msg.sender}</span>
                    <span>{new Date(msg.timestamp).toLocaleTimeString()}</span>
                  </div>
                  <p className="text-sm">{msg.message}</p>
                </div>
              ))}
            </div>

            <div className="flex space-x-2">
              <input
                type="text"
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleSendChat()}
                placeholder="Type a message..."
                className="input flex-1"
              />
              <button
                onClick={handleSendChat}
                className="btn btn-primary"
              >
                <Send size={20} />
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Game ID Display */}
      <div className="mt-6 card bg-gray-50">
        <p className="text-sm text-gray-600">
          <span className="font-semibold">Game ID:</span>{' '}
          <span className="font-mono bg-white px-2 py-1 rounded">{gameId}</span>
        </p>
        <p className="text-xs text-gray-500 mt-2">
          Share this ID with your friend to let them join the game
        </p>
      </div>
    </div>
  );
};

export default Game;
