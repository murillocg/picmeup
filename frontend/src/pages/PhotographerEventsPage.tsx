import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listMyEvents } from '../services/api';
import type { EventResponse } from '../types/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';
import usePageTitle from '../hooks/usePageTitle';

/**
 * A photographer's whole application: the events they may upload to. Assignments are
 * made by an admin, so an empty list is a normal state rather than an error.
 */
export default function PhotographerEventsPage() {
  usePageTitle('My events');
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    listMyEvents()
      .then(setEvents)
      .catch(() => setError('Could not load your events. Please try again.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner />;

  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="text-3xl font-bold text-gray-900 mb-6">My events</h1>

      {error && <ErrorMessage message={error} />}

      {!error && events.length === 0 && (
        <div className="bg-white border border-gray-200 rounded-lg p-8 text-center">
          <p className="text-gray-600">You have not been assigned to any events yet.</p>
          <p className="text-sm text-gray-500 mt-2">
            Ask the team to assign you to an event and it will appear here.
          </p>
        </div>
      )}

      <div className="space-y-3">
        {events.map((event) => (
          <div
            key={event.slug}
            className="bg-white border border-gray-200 rounded-lg p-4 flex items-center justify-between gap-4"
          >
            <div className="min-w-0">
              <Link
                to={`/events/${event.slug}`}
                className="font-semibold text-gray-900 hover:text-brand-orange truncate block"
              >
                {event.name}
              </Link>
              <p className="text-sm text-gray-500">
                {event.location} &middot;{' '}
                {new Date(event.date).toLocaleDateString('en-AU', {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                })}
              </p>
            </div>
            <Link
              to={`/events/${event.slug}/upload`}
              className="shrink-0 bg-brand-orange text-white px-4 py-2 rounded-lg hover:bg-brand-orange-dark text-sm font-medium"
            >
              Upload photos
            </Link>
          </div>
        ))}
      </div>
    </div>
  );
}
