import { useEffect, useState, lazy, Suspense } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { getEvent, listPhotos, searchByFace, deleteEvent, deletePhoto, uploadCoverImage } from '../services/api';
import type { EventResponse, PhotoResponse } from '../types/api';
import { useAuth } from '../context/AuthContext';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';
import SelfieCapture from '../components/SelfieCapture';
import PhotoGrid from '../components/PhotoGrid';
import { getErrorMessage } from '../utils/errors';
import CoverImageCropper from '../components/CoverImageCropper';
import usePageTitle from '../hooks/usePageTitle';
import { SITE_URL, eventUrl } from '../config';

// Admin-only, and it pulls in the QR library — keep it out of the bundle every
// visitor downloads.
const QrPosterModal = lazy(() => import('../components/QrPosterModal'));

export default function EventDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const { authenticated } = useAuth();
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [coverUploading, setCoverUploading] = useState(false);
  const [cropImageUrl, setCropImageUrl] = useState<string | null>(null);
  const [photos, setPhotos] = useState<PhotoResponse[]>([]);
  const [matchedPhotos, setMatchedPhotos] = useState<PhotoResponse[] | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState('');
  const [consentGiven, setConsentGiven] = useState(false);
  const [qrPosterOpen, setQrPosterOpen] = useState(false);

  usePageTitle(event?.name);

  useEffect(() => {
    if (!event) return;
    const jsonLd = {
      '@context': 'https://schema.org',
      '@type': 'Event',
      name: event.name,
      startDate: event.date,
      location: {
        '@type': 'Place',
        name: event.location,
      },
      url: eventUrl(event.slug),
      organizer: {
        '@type': 'Organization',
        name: 'Elite Sport Photos',
        url: SITE_URL,
      },
      ...(event.coverImageUrl ? { image: event.coverImageUrl } : {}),
    };
    const script = document.createElement('script');
    script.type = 'application/ld+json';
    script.textContent = JSON.stringify(jsonLd);
    document.head.appendChild(script);
    return () => { document.head.removeChild(script); };
  }, [event]);

  useEffect(() => {
    if (!slug) return;
    Promise.all([getEvent(slug), listPhotos(slug, authenticated)])
      .then(([eventData, photosData]) => {
        setEvent(eventData);
        setPhotos(photosData);
      })
      .catch(() => setError('Failed to load event'))
      .finally(() => setLoading(false));
  }, [slug, authenticated]);

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
    } catch {
      setError('Face search failed. Please try a clearer photo.');
    } finally {
      setSearching(false);
    }
  }


  async function handleDeletePhoto(photoId: string) {
    if (!slug || !window.confirm('Delete this photo? This cannot be undone.')) return;
    try {
      await deletePhoto(slug, photoId);
      setPhotos((prev) => prev.filter((p) => p.id !== photoId));
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to delete photo'));
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

  if (event.comingSoon && !authenticated) {
    return (
      <div className="max-w-lg mx-auto text-center py-16">
        <div className="bg-orange-50 border border-brand-orange/30 rounded-lg p-8">
          <svg className="mx-auto w-16 h-16 text-brand-orange mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
          </svg>
          <h2 className="text-2xl font-bold text-gray-900 mb-2">{event.name}</h2>
          <p className="text-gray-600 mb-1">{event.location}</p>
          <p className="text-sm text-gray-400 mb-4">
            {new Date(event.date).toLocaleDateString('en-AU', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
            })}
          </p>
          <span className="inline-block bg-brand-orange text-white text-sm font-semibold px-4 py-2 rounded-full">
            Coming Soon
          </span>
          <p className="text-gray-500 text-sm mt-4">Photos for this event are not available yet. Check back soon!</p>
        </div>
      </div>
    );
  }

  const displayPhotos = authenticated ? photos : (matchedPhotos ?? []);
  const isFree = event?.free ?? false;
  const photoPrice = event?.photoPrice ?? 20;
  const packPrice = event?.packPrice ?? 65;
  const perPhotoTotal = selectedIds.size * photoPrice;
  const totalPrice = Math.min(perPhotoTotal, packPrice);
  const hasBulkDiscount = perPhotoTotal > packPrice;
  const allSelected = matchedPhotos !== null && matchedPhotos.length > 0 && matchedPhotos.every((p) => selectedIds.has(p.id));
  const savings = perPhotoTotal - packPrice;

  function handleFreeDownloadAll() {
    if (!slug || !matchedPhotos) return;
    const ids = matchedPhotos.map((p) => p.id);
    localStorage.setItem(`cart-${slug}`, JSON.stringify(ids));
    navigate(`/events/${slug}/checkout`);
  }

  return (
    <div>
      <div className="mb-8 flex items-start justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">{event.name}</h1>
          <p className="text-gray-600 mt-1">{event.location}</p>
          <p className="text-sm text-gray-400">
            {new Date(event.date).toLocaleDateString('en-AU', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
            })}
          </p>
        </div>
        {authenticated && (
          <div className="flex items-center gap-2">
            <Link
              to={`/events/${slug}/upload`}
              className="bg-brand-orange text-white px-4 py-2 rounded-lg hover:bg-brand-orange-dark text-sm"
            >
              Upload photos
            </Link>
            <button
              onClick={() => setQrPosterOpen(true)}
              className="bg-gray-600 text-white px-4 py-2 rounded-lg hover:bg-gray-700 text-sm"
            >
              QR poster
            </button>
            <label className="bg-gray-600 text-white px-4 py-2 rounded-lg hover:bg-gray-700 text-sm cursor-pointer">
              {coverUploading ? 'Uploading...' : 'Set cover photo'}
              <input
                type="file"
                accept="image/*"
                className="hidden"
                disabled={coverUploading}
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (!file) return;
                  const url = URL.createObjectURL(file);
                  setCropImageUrl(url);
                  e.target.value = '';
                }}
              />
            </label>
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

      {!authenticated && (
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
                className="bg-brand-orange text-white px-6 py-3 rounded-lg hover:bg-brand-orange-dark"
              >
                I agree, let me search
              </button>
            </div>
          ) : (
            <SelfieCapture onCapture={handleSelfieCapture} loading={searching} />
          )}
        </div>
      )}

      {!authenticated && matchedPhotos && !isFree && (
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-4">
            <span className="text-sm text-gray-600">
              {matchedPhotos.length} photo{matchedPhotos.length !== 1 ? 's' : ''} found
            </span>
            {matchedPhotos.length > 0 && (
              <button
                onClick={() => {
                  const allSelected = matchedPhotos.every((p) => selectedIds.has(p.id));
                  setSelectedIds((prev) => {
                    const next = new Set(prev);
                    if (allSelected) {
                      matchedPhotos.forEach((p) => next.delete(p.id));
                    } else {
                      matchedPhotos.forEach((p) => next.add(p.id));
                    }
                    return next;
                  });
                }}
                className="text-sm text-brand-orange hover:text-brand-orange-dark font-medium"
              >
                {allSelected ? 'Deselect all' : 'Select all'}
              </button>
            )}
          </div>

          {selectedIds.size > 0 && (
            <div className="flex items-center gap-4">
              <span className="text-gray-600">
                {selectedIds.size} selected —{' '}
                {hasBulkDiscount ? (
                  <>
                    <span className="line-through text-gray-400">${perPhotoTotal.toFixed(2)}</span>{' '}
                    <span className="text-green-600 font-semibold">${totalPrice.toFixed(2)} AUD</span>
                    <span className="text-green-600 text-sm ml-1">(save ${savings.toFixed(2)}!)</span>
                  </>
                ) : (
                  <>${totalPrice.toFixed(2)} AUD</>
                )}
              </span>
              <button
                onClick={() => navigate(`/events/${slug}/checkout`)}
                className="bg-green-600 text-white px-6 py-2 rounded-lg hover:bg-green-700"
              >
                Buy selected photos
              </button>
            </div>
          )}
        </div>
      )}

      {!authenticated && matchedPhotos && !isFree && matchedPhotos.length >= 5 && !allSelected && selectedIds.size === 0 && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-4 mb-4 text-center">
          <p className="text-green-800 text-sm">
            Select all {matchedPhotos.length} photos for just <span className="font-semibold">${packPrice.toFixed(2)} AUD</span> instead of ${(matchedPhotos.length * photoPrice).toFixed(2)} AUD!
          </p>
        </div>
      )}

      {!authenticated && matchedPhotos && isFree && matchedPhotos.length > 0 && (
        <div className="flex items-center justify-between mb-4">
          <span className="text-sm text-gray-600">
            {matchedPhotos.length} photo{matchedPhotos.length !== 1 ? 's' : ''} found
          </span>
          <button
            onClick={handleFreeDownloadAll}
            className="bg-green-600 text-white px-6 py-2 rounded-lg hover:bg-green-700"
          >
            Download all photos for free
          </button>
        </div>
      )}

      {!authenticated && matchedPhotos && isFree && matchedPhotos.length === 0 && (
        <p className="text-sm text-gray-600 mb-4">No photos found</p>
      )}

      {authenticated ? (
        <div>
          <h2 className="text-lg font-semibold text-gray-900 mb-4">All photos ({photos.length})</h2>
          <PhotoGrid
            photos={photos}
            selectedIds={new Set()}
            onToggleSelect={() => {}}
            selectable={false}
            adminMode
            onDelete={handleDeletePhoto}
          />
        </div>
      ) : !matchedPhotos ? (
        <div className="text-center py-12 text-gray-500">
          Upload a selfie to find photos you appear in
        </div>
      ) : (
        <PhotoGrid
          photos={displayPhotos}
          selectedIds={selectedIds}
          onToggleSelect={toggleSelect}
          selectable={!isFree}
        />
      )}
      {qrPosterOpen && (
        <Suspense fallback={null}>
          <QrPosterModal event={event} onClose={() => setQrPosterOpen(false)} />
        </Suspense>
      )}
      {cropImageUrl && (
        <CoverImageCropper
          imageUrl={cropImageUrl}
          onCancel={() => {
            URL.revokeObjectURL(cropImageUrl);
            setCropImageUrl(null);
          }}
          onConfirm={async (blob) => {
            URL.revokeObjectURL(cropImageUrl);
            setCropImageUrl(null);
            if (!slug) return;
            setCoverUploading(true);
            try {
              const file = new File([blob], 'cover.jpg', { type: 'image/jpeg' });
              const updated = await uploadCoverImage(slug, file);
              setEvent(updated);
            } catch {
              setError('Failed to upload cover image');
            } finally {
              setCoverUploading(false);
            }
          }}
        />
      )}
    </div>
  );
}
