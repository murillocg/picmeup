import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listEvents } from '../services/api';
import type { EventResponse } from '../types/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';

export default function EventListPage() {
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');

  useEffect(() => {
    listEvents()
      .then(setEvents)
      .catch(() => setError('Failed to load events'))
      .finally(() => setLoading(false));
  }, []);

  const filtered = events.filter((e) =>
    e.name.toLowerCase().includes(search.toLowerCase()),
  );

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error} />;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-3xl font-bold text-gray-900">Events</h1>
        <input
          type="text"
          placeholder="Search events..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
      </div>

      {filtered.length === 0 ? (
        <div className="text-center py-12 text-gray-500">
          {events.length === 0 ? 'No events yet' : 'No events match your search'}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {filtered.map((event) => (
            <Link
              key={event.id}
              to={`/events/${event.slug}`}
              className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden hover:shadow-md transition-shadow flex"
            >
              <div className="w-28 h-28 flex-shrink-0 bg-gray-100 flex items-center justify-center">
                {event.coverImageUrl ? (
                  <img
                    src={event.coverImageUrl}
                    alt={event.name}
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <span className="text-xs text-gray-400 text-center px-2">No Image Available</span>
                )}
              </div>
              <div className="p-4 flex flex-col justify-center min-w-0">
                <h2 className="text-lg font-semibold text-gray-900 mb-1 truncate">{event.name}</h2>
                <p className="text-gray-600 text-sm mb-1 truncate">{event.location}</p>
                <p className="text-xs text-gray-400">
                  {new Date(event.date).toLocaleDateString('en-AU', {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                  })}
                </p>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
