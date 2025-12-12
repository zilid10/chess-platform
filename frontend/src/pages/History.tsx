import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { gameService } from '../services/gameService';
import { MatchRecord } from '../types';
import { Trophy, Clock, Calendar } from 'lucide-react';

const History = () => {
  const { user } = useAuth();
  const [games, setGames] = useState<MatchRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const pageSize = 10;

  useEffect(() => {
    if (user?.id) {
      loadGames();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id, page]);

  const loadGames = async () => {
    if (!user?.id) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError('');
      const response = await gameService.getGames(user.id, page, pageSize);
      setGames(response.content || []);
      setTotalPages(response.totalPages || 0);
    } catch (err: any) {
      console.error('Error loading games:', err);
      setError(err.response?.data?.message || 'Failed to load game history');
      setGames([]);
    } finally {
      setLoading(false);
    }
  };

  const getGameResult = (game: MatchRecord) => {
    if (!user || !game.result) return { text: game.result || 'Unknown', color: 'text-gray-600' };

    const isWhite = game.whitePlayer?.id === user.id;
    const isBlack = game.blackPlayer?.id === user.id;

    if (game.result.includes('1-0')) {
      if (isWhite) return { text: 'Won', color: 'text-green-600 font-semibold' };
      if (isBlack) return { text: 'Lost', color: 'text-red-600 font-semibold' };
    } else if (game.result.includes('0-1')) {
      if (isBlack) return { text: 'Won', color: 'text-green-600 font-semibold' };
      if (isWhite) return { text: 'Lost', color: 'text-red-600 font-semibold' };
    } else if (game.result.includes('1/2-1/2') || game.result.includes('STALEMATE')) {
      return { text: 'Draw', color: 'text-blue-600 font-semibold' };
    }

    return { text: game.result, color: 'text-gray-600' };
  };

  const getOpponentName = (game: MatchRecord) => {
    if (!user) return 'Unknown';
    
    if (game.whitePlayer?.id === user.id) {
      return game.blackPlayer?.username || 'Unknown';
    }
    return game.whitePlayer?.username || 'Unknown';
  };

  const getPlayerColor = (game: MatchRecord) => {
    if (!user) return '';
    return game.whitePlayer?.id === user.id ? 'White' : 'Black';
  };

  const formatDuration = (startTime: string, endTime?: string) => {
    if (!endTime) return 'In progress';
    
    const start = new Date(startTime);
    const end = new Date(endTime);
    const durationMs = end.getTime() - start.getTime();
    const minutes = Math.floor(durationMs / 60000);
    const seconds = Math.floor((durationMs % 60000) / 1000);
    
    if (minutes > 0) {
      return `${minutes}m ${seconds}s`;
    }
    return `${seconds}s`;
  };

  if (loading && page === 0) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex items-center justify-center h-64">
          <div className="text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto mb-4"></div>
            <p className="text-gray-600">Loading game history...</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">Game History</h1>
        <p className="mt-2 text-gray-600">View your past chess games</p>
      </div>

      {error && (
        <div className="mb-6 rounded-md bg-red-50 p-4">
          <p className="text-sm text-red-800">{error}</p>
        </div>
      )}

      {games.length === 0 && !loading ? (
        <div className="card text-center py-12">
          <Trophy size={48} className="mx-auto text-gray-400 mb-4" />
          <h3 className="text-lg font-semibold text-gray-900 mb-2">No games yet</h3>
          <p className="text-gray-600">Start playing to build your game history!</p>
        </div>
      ) : (
        <>
          <div className="space-y-4">
            {games.map((game) => {
              const result = getGameResult(game);
              
              return (
                <div key={game.id} className="card hover:shadow-lg transition-shadow">
                  <div className="flex items-center justify-between">
                    <div className="flex-1">
                      <div className="flex items-center space-x-4 mb-2">
                        <h3 className="text-lg font-semibold text-gray-900">
                          vs {getOpponentName(game)}
                        </h3>
                        <span className={`text-sm ${result.color}`}>
                          {result.text}
                        </span>
                      </div>
                      
                      <div className="flex flex-wrap items-center gap-4 text-sm text-gray-600">
                        <div className="flex items-center space-x-1">
                          <span className="font-medium">Playing as:</span>
                          <span>{getPlayerColor(game)}</span>
                        </div>
                        
                        <div className="flex items-center space-x-1">
                          <Calendar size={14} />
                          <span>{new Date(game.startTime).toLocaleDateString()}</span>
                        </div>
                        
                        <div className="flex items-center space-x-1">
                          <Clock size={14} />
                          <span>{formatDuration(game.startTime, game.endTime)}</span>
                        </div>
                      </div>

                      <div className="mt-2 text-xs text-gray-500">
                        {game.result}
                      </div>
                    </div>

                    <div className="flex items-center space-x-3">
                      {game.pgn && (
                        <a
                          href={`/api/games/${game.id}/pgn`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="btn btn-secondary text-sm"
                        >
                          View PGN
                        </a>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="mt-8 flex items-center justify-center space-x-4">
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0 || loading}
                className="btn btn-secondary disabled:opacity-50"
              >
                Previous
              </button>
              
              <span className="text-sm text-gray-600">
                Page {page + 1} of {totalPages}
              </span>
              
              <button
                onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                disabled={page >= totalPages - 1 || loading}
                className="btn btn-secondary disabled:opacity-50"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default History;
