import { useState } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import { createEvent } from '../services/api';
import { useAuth } from '../context/AuthContext';
import ErrorMessage from '../components/ErrorMessage';

export default function CreateEventPage() {
  const navigate = useNavigate();
  const { authenticated, loading } = useAuth();
  const [name, setName] = useState('');
  const [date, setDate] = useState('');
  const [location, setLocation] = useState('');
  const [photoPrice, setPhotoPrice] = useState('20.00');
  const [packPrice, setPackPrice] = useState('65.00');
  const [free, setFree] = useState(false);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (!loading && !authenticated) return <Navigate to="/" />;

  function handlePhotoPriceChange(value: string) {
    setPhotoPrice(value);
    const price = parseFloat(value);
    if (!isNaN(price) && price > 0) {
      setPackPrice((price * 3.25).toFixed(2));
    }
  }

  function formatPrice(value: string, setter: (v: string) => void) {
    const num = parseFloat(value);
    if (!isNaN(num) && num >= 0) {
      setter(num.toFixed(2));
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const event = await createEvent({
        name,
        date,
        location,
        photoPrice: free ? 0 : parseFloat(photoPrice),
        packPrice: free ? 0 : parseFloat(packPrice),
        free,
      });
      navigate(`/events/${event.slug}/upload`);
    } catch {
      setError('Failed to create event. It may already exist.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="max-w-lg mx-auto">
      <h1 className="text-3xl font-bold text-gray-900 mb-8">Create event</h1>

      {error && <div className="mb-4"><ErrorMessage message={error} /></div>}

      <form onSubmit={handleSubmit} className="space-y-6">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Event name</label>
          <input
            type="text"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="City Marathon 2026"
            className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-brand-orange"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Date</label>
          <input
            type="date"
            required
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-brand-orange"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Location</label>
          <input
            type="text"
            required
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            placeholder="Sydney, Australia"
            className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-brand-orange"
          />
        </div>

        <div className="border-t pt-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Pricing</h2>

          <label className="flex items-center gap-3 mb-4 cursor-pointer">
            <input
              type="checkbox"
              checked={free}
              onChange={(e) => setFree(e.target.checked)}
              className="h-4 w-4 rounded border-gray-300 text-brand-orange focus:ring-brand-orange"
            />
            <span className="text-sm font-medium text-gray-700">Free event — users can download photos for free</span>
          </label>

          {!free && (
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Price per photo (AUD)</label>
                <input
                  type="number"
                  required
                  min="0.01"
                  step="0.01"
                  value={photoPrice}
                  onChange={(e) => handlePhotoPriceChange(e.target.value)}
                  onBlur={() => formatPrice(photoPrice, setPhotoPrice)}
                  className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-brand-orange"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Pack price — all photos (AUD)
                </label>
                <input
                  type="number"
                  required
                  min="0.01"
                  step="0.01"
                  value={packPrice}
                  onChange={(e) => setPackPrice(e.target.value)}
                  onBlur={() => formatPrice(packPrice, setPackPrice)}
                  className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-brand-orange"
                />
                <p className="text-xs text-gray-500 mt-1">
                  Users pay at most this amount, regardless of how many photos they select.
                </p>
              </div>
            </div>
          )}
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-brand-orange text-white py-3 rounded-lg hover:bg-brand-orange-dark disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitting ? 'Creating...' : 'Create event'}
        </button>
      </form>
    </div>
  );
}
