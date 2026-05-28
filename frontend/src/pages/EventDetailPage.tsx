import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { getEvent, listPhotos, searchByFace, deleteEvent, deletePhoto, uploadCoverImage, getFreeDownloads } from '../services/api';
import type { EventResponse, PhotoResponse } from '../types/api';
import { useAuth } from '../context/AuthContext';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';
import SelfieCapture from '../components/SelfieCapture';
import PhotoGrid from '../components/PhotoGrid';
import { getErrorMessage } from '../utils/errors';
import CoverImageCropper from '../components/CoverImageCropper';

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
  const [downloadUrls, setDownloadUrls] = useState<string[] | null>(null);
  const [downloadLoading, setDownloadLoading] = useState(false);

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

  const displayPhotos = authenticated ? photos : (matchedPhotos ?? []);
  const isFree = event?.free ?? false;
  const photoPrice = event?.photoPrice ?? 20;
  const packPrice = event?.packPrice ?? 65;
  const perPhotoTotal = selectedIds.size * photoPrice;
  const totalPrice = Math.min(perPhotoTotal, packPrice);
  const hasBulkDiscount = perPhotoTotal > packPrice;
  const allSelected = matchedPhotos !== null && matchedPhotos.length > 0 && matchedPhotos.every((p) => selectedIds.has(p.id));
  const savings = perPhotoTotal - packPrice;

  async function handleFreeDownload() {
    if (!slug || !matchedPhotos) return;
    setDownloadLoading(true);
    setError('');
    try {
      const ids = matchedPhotos.map((p) => p.id);
      const urls = await getFreeDownloads(slug, ids);
      setDownloadUrls(urls);
    } catch {
      setError('Failed to get download links.');
    } finally {
      setDownloadLoading(false);
    }
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
        <div className="mb-4">
          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-600">
              {matchedPhotos.length} photo{matchedPhotos.length !== 1 ? 's' : ''} found
            </span>
            {!downloadUrls && (
              <button
                onClick={handleFreeDownload}
                disabled={downloadLoading}
                className="bg-green-600 text-white px-6 py-2 rounded-lg hover:bg-green-700 disabled:opacity-50"
              >
                {downloadLoading ? 'Preparing...' : 'Download all photos for free'}
              </button>
            )}
          </div>
          {downloadUrls && (
            <div className="mt-4 bg-green-50 border border-green-200 rounded-lg p-4">
              <p className="text-green-800 font-medium mb-3">Your download links are ready!</p>
              <div className="space-y-2">
                {downloadUrls.map((url, i) => (
                  <a
                    key={i}
                    href={url}
                    download
                    className="block text-brand-orange hover:text-brand-orange-dark text-sm truncate"
                  >
                    Download photo {i + 1}
                  </a>
                ))}
              </div>
            </div>
          )}
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
        />
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
