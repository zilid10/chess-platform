import { useEffect, useState } from 'react';
import { Trophy } from 'lucide-react';
import { apiErrorMessage } from '../services/errors';
import { userService } from '../services/userService';
import { PlayerRating, TIME_CONTROL_LABELS } from '../types';

interface RatingsCardProps {
  userId: string;
}

const RatingsCard = ({ userId }: RatingsCardProps) => {
  const [ratings, setRatings] = useState<PlayerRating[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    userService
      .getRatings(userId)
      .then((result) => {
        if (!cancelled) setRatings(result);
      })
      .catch((err) => {
        if (!cancelled) setError(apiErrorMessage(err, 'Failed to load ratings'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [userId]);

  return (
    <div className="card">
      <div className="flex items-center space-x-3 mb-4">
        <Trophy className="text-primary-600" size={24} />
        <h2 className="text-xl font-semibold">Ratings</h2>
      </div>

      {error && <p className="text-sm text-red-800">{error}</p>}
      {loading && !error && <p className="text-sm text-gray-600">Loading ratings...</p>}

      {!loading && !error && (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
          {ratings.map((rating) => (
            <div key={rating.timeControl} className="rounded-md border border-gray-200 p-3">
              <p className="text-sm font-medium text-gray-600">{TIME_CONTROL_LABELS[rating.timeControl]}</p>
              <p className="text-2xl font-bold text-gray-900">{rating.rating}</p>
              <p className="text-xs text-gray-500">
                {rating.gamesPlayed === 0
                  ? 'No rated games'
                  : `${rating.gamesPlayed} ${rating.gamesPlayed === 1 ? 'game' : 'games'} · peak ${rating.peakRating}`}
              </p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default RatingsCard;
