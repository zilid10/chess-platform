import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Chessboard } from 'react-chessboard';
import { Chess } from 'chess.js';
import { websocketService } from '../services/websocketService';
import { gameService } from '../services/gameService';
import { useAuth } from '../context/AuthContext';
import { GameState, ChatMessage as ChatMessageType } from '../types';
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
  const iOfferedDrawRef = useRef(false);

  useEffect(() => {
    if (!gameId || !user) return;

    const initializeGame = async () => {
      try {
        setLoading(true);
        setError('');

        // Step 1: Join the game via REST API
        const joinResponse = await gameService.joinGame(gameId);
        
        // Check if user is a player or spectator
        const isActualPlayer = joinResponse.role === 'WHITE' || joinResponse.role === 'BLACK';
        setIsPlayer(isActualPlayer);
        
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
            setGameState(state);
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
      } catch (err: any) {
        console.error('Error initializing game:', err);
        setError(err.response?.data?.message || 'Failed to join game');
        setLoading(false);
      }
    };

    initializeGame();

    return () => {
      websocketService.disconnect();
    };
  }, [gameId, user]);

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
    } catch (error) {
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
    
    if (status.includes('DRAW')) {
      return 'Game drawn';
    }
    
    if (status.includes('STALEMATE')) {
      return 'Stalemate - Draw!';
    }
    
    return status;
  };

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
                <h2 className="text-2xl font-bold text-gray-900">Chess Game</h2>
                <p className="text-sm text-gray-600 mt-1">{getStatusMessage()}</p>
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
              <Chessboard
                position={game.fen()}
                onPieceDrop={onDrop}
                boardWidth={560}
                boardOrientation={playerColor}
              />
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
