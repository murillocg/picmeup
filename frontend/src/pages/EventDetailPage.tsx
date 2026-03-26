import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { getEvent, listPhotos, searchByFace, deleteEvent, updateEvent } from '../services/api';
import type { EventResponse, PhotoResponse } from '../types/api';
import { useAuth } from '../context/AuthContext';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';
import SelfieCapture from '../components/SelfieCapture';
import PhotoGrid from '../components/PhotoGrid';

export default function EventDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const { authenticated } = useAuth();
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [editing, setEditing] = useState(false);
  const [editName, setEditName] = useState('');
  const [editDate, setEditDate] = useState('');
  const [editLocation, setEditLocation] = useState('');
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [photos, setPhotos] = useState<PhotoResponse[]>([]);
  const [matchedPhotos, setMatchedPhotos] = useState<PhotoResponse[] | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(() => {
    const saved = localStorage.getItem(`cart-${slug}`);
    return saved ? new Set(JSON.parse(saved)) : new Set();
  });
  const [loading, setLoading] = useState(true);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState('');
  const [showAll, setShowAll] = useState(false);
  const [consentGiven, setConsentGiven] = useState(false);

  useEffect(() => {
    if (!slug) return;
    Promise.all([getEvent(slug), listPhotos(slug)])
      .then(([eventData, photosData]) => {
        setEvent(eventData);
        setPhotos(photosData);
      })
      .catch(() => setError('Failed to load event'))
      .finally(() => setLoading(false));
  }, [slug]);

  useEffect(() => {
    if (slug) {
      localStorage.setItem(`cart-${slug}`, JSON.stringify([...selectedIds]));
    }
  }, [selectedIds, slug]);

  async function handleSelfieCapture(file: File) {
    if (!slug) return;
    setSearching(true);
    setError('');
    try {
      const results = await searchByFace(slug, file);
      setMatchedPhotos(results);
      setShowAll(false);
    } catch {
      setError('Face search failed. Please try a clearer photo.');
    } finally {
      setSearching(false);
    }
  }

  function toggleSelect(id: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  if (loading) return <LoadingSpinner />;
  if (error && !event) return <ErrorMessage message={error} />;
  if (!event) return <ErrorMessage message="Event not found" />;

  const displayPhotos = showAll ? photos : (matchedPhotos ?? []);
  const totalPrice = selectedIds.size * 10;

  function startEditing() {
    setEditName(event!.name);
    setEditDate(event!.date);
    setEditLocation(event!.location);
    setEditing(true);
  }

  async function handleSave() {
    if (!slug) return;
    setSaving(true);
    setError('');
    try {
      const updated = await updateEvent(slug, { name: editName, date: editDate, location: editLocation });
      setEvent(updated);
      setEditing(false);
    } catch {
      setError('Failed to update event');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <div className="mb-8 flex items-start justify-between">
        <div>
          {editing ? (
            <div className="space-y-3">
              <input
                type="text"
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
                className="block text-2xl font-bold border border-gray-300 rounded-lg px-3 py-1 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
              <input
                type="text"
                value={editLocation}
                onChange={(e) => setEditLocation(e.target.value)}
                className="block border border-gray-300 rounded-lg px-3 py-1 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
              <input
                type="date"
                value={editDate}
                onChange={(e) => setEditDate(e.target.value)}
                className="block border border-gray-300 rounded-lg px-3 py-1 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
              <div className="flex gap-2">
                <button
                  onClick={handleSave}
                  disabled={saving}
                  className="bg-indigo-600 text-white px-4 py-1 rounded-lg hover:bg-indigo-700 disabled:opacity-50 text-sm"
                >
                  {saving ? 'Saving...' : 'Save'}
                </button>
                <button
                  onClick={() => setEditing(false)}
                  className="bg-gray-200 text-gray-700 px-4 py-1 rounded-lg hover:bg-gray-300 text-sm"
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            <>
              <h1 className="text-3xl font-bold text-gray-900">{event.name}</h1>
              <p className="text-gray-600 mt-1">{event.location}</p>
              <p className="text-sm text-gray-400">
                {new Date(event.date).toLocaleDateString('en-AU', {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                })}
              </p>
            </>
          )}
        </div>
        {authenticated && !editing && (
          <div className="flex items-center gap-2">
            <button
              onClick={startEditing}
              className="bg-gray-200 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-300 text-sm"
            >
              Edit
            </button>
            <Link
              to={`/events/${slug}/upload`}
              className="bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 text-sm"
            >
              Upload photos
            </Link>
            <button
              onClick={async () => {
                if (!slug || !window.confirm('Delete this event and all its photos? This cannot be undone.')) return;
                setDeleting(true);
                try {
                  await deleteEvent(slug);
                  navigate('/');
                } catch {
                  setError('Failed to delete event');
                  setDeleting(false);
                }
              }}
              disabled={deleting}
              className="bg-red-600 text-white px-4 py-2 rounded-lg hover:bg-red-700 disabled:opacity-50 text-sm"
            >
              {deleting ? 'Deleting...' : 'Delete event'}
            </button>
          </div>
        )}
      </div>

      {error && <ErrorMessage message={error} />}

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-8">
        <h2 className="text-xl font-semibold text-gray-900 mb-4">Find your photos</h2>

        {!consentGiven ? (
          <div className="text-center">
            <p className="text-gray-600 mb-4">
              We use facial recognition to find photos you appear in.
              Your selfie is only used for searching and is not stored.
            </p>
            <button
              onClick={() => setConsentGiven(true)}
              className="bg-indigo-600 text-white px-6 py-3 rounded-lg hover:bg-indigo-700"
            >
              I agree, let me search
            </button>
          </div>
        ) : (
          <SelfieCapture onCapture={handleSelfieCapture} loading={searching} />
        )}
      </div>

      <div className="flex items-center justify-between mb-4">
        <div className="flex gap-2">
          <button
            onClick={() => setShowAll(false)}
            className={`px-4 py-2 rounded-lg text-sm ${
              !showAll ? 'bg-indigo-600 text-white' : 'bg-gray-200 text-gray-700'
            }`}
          >
            My photos {matchedPhotos ? `(${matchedPhotos.length})` : ''}
          </button>
          <button
            onClick={() => setShowAll(true)}
            className={`px-4 py-2 rounded-lg text-sm ${
              showAll ? 'bg-indigo-600 text-white' : 'bg-gray-200 text-gray-700'
            }`}
          >
            All photos ({photos.length})
          </button>
        </div>

        {selectedIds.size > 0 && (
          <div className="flex items-center gap-4">
            <span className="text-gray-600">
              {selectedIds.size} selected — ${totalPrice} AUD
            </span>
            <button className="bg-green-600 text-white px-6 py-2 rounded-lg hover:bg-green-700">
              Buy selected photos
            </button>
          </div>
        )}
      </div>

      {!showAll && !matchedPhotos ? (
        <div className="text-center py-12 text-gray-500">
          Upload a selfie to find photos you appear in
        </div>
      ) : (
        <PhotoGrid
          photos={displayPhotos}
          selectedIds={selectedIds}
          onToggleSelect={toggleSelect}
        />
      )}
    </div>
  );
}
