import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getOrder, getOrderZip } from '../services/api';
import type { OrderResponse } from '../types/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';
import usePageTitle from '../hooks/usePageTitle';
import { getMobilePlatform } from '../utils/device';

export default function OrderPage() {
  usePageTitle('Order');
  const { id } = useParams<{ id: string }>();
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [zipState, setZipState] = useState<'idle' | 'preparing' | 'error'>('idle');

  /**
   * The archive is built into S3 on first request, so the first person to ask may wait a
   * minute. Polling until it exists, then handing the browser a direct S3 link: that is
   * what gives the download a content length and the ability to resume, instead of the
   * silently-truncating stream this replaced.
   */
  async function handleDownloadAll() {
    if (!order) return;
    setZipState('preparing');

    const deadline = Date.now() + 5 * 60 * 1000;
    try {
      while (Date.now() < deadline) {
        const result = await getOrderZip(order.id);

        if (result.status === 'READY' && result.url) {
          window.location.assign(result.url);
          setZipState('idle');
          return;
        }
        if (result.status === 'FORBIDDEN') {
          setZipState('error');
          return;
        }
        await new Promise((resolve) => setTimeout(resolve, 3000));
      }
      setZipState('error');
    } catch {
      setZipState('error');
    }
  }

  useEffect(() => {
    if (!id) return;

    getOrder(id)
      .then(setOrder)
      .catch(() => setError('Failed to load order'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error} />;
  if (!order) return <ErrorMessage message="Order not found" />;

  const isPaid = order.status === 'PAID';
  const mobilePlatform = getMobilePlatform();

  return (
    <div className="max-w-2xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          {isPaid ? 'Your photos are ready!' : 'Order pending'}
        </h1>
        <p className="text-gray-600 mt-1">
          Order for {order.buyerEmail}
        </p>
      </div>

      {isPaid && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-4 mb-6">
          <p className="text-green-800 text-sm">
            Your download links are available below. They expire in 24 hours — revisit this page to regenerate them.
          </p>
        </div>
      )}

      {isPaid && mobilePlatform && (
        <div className="bg-orange-50 border border-brand-orange/30 rounded-lg p-4 mb-6">
          <h2 className="text-sm font-semibold text-gray-900 mb-1">Where your photos are saved</h2>
          {mobilePlatform === 'ios' ? (
            <p className="text-sm text-gray-700">
              Your photos are saved in <span className="font-semibold">Files → Downloads</span>, not in your
              Photos app.
            </p>
          ) : (
            <p className="text-sm text-gray-700">
              Your photos are saved in your <span className="font-semibold">Downloads</span> folder.
            </p>
          )}
          <Link
            to="/faq#where-are-my-images-downloaded"
            className="text-sm text-brand-orange hover:text-brand-orange-dark underline mt-2 inline-block"
          >
            How to find and share them
          </Link>
        </div>
      )}

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
        <div className="flex justify-between text-sm text-gray-500 mb-4">
          <span>{order.items.length} photo{order.items.length !== 1 ? 's' : ''}</span>
          <span>${order.totalAmount.toFixed(2)} {order.currency}</span>
        </div>

        <div className="space-y-3">
          {order.items.map((item) => (
            <div
              key={item.id}
              className="flex items-center justify-between gap-3 border border-gray-100 rounded-lg p-3"
            >
              <span className="text-sm text-gray-600 truncate" title={item.filename || 'Photo'}>
                {item.filename || 'Photo'}
              </span>
              {isPaid && item.downloadUrl ? (
                <a
                  href={item.downloadUrl}
                  download
                  className="bg-brand-orange text-white px-4 py-1.5 rounded-lg hover:bg-brand-orange-dark text-sm shrink-0"
                >
                  Download
                </a>
              ) : (
                <span className="text-sm text-gray-400 shrink-0">Unavailable</span>
              )}
            </div>
          ))}
        </div>
      </div>

      {isPaid && (
        <>
          <button
            onClick={handleDownloadAll}
            disabled={zipState === 'preparing'}
            className="block w-full text-center bg-green-600 text-white py-3 rounded-lg hover:bg-green-700 font-semibold disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {zipState === 'preparing' ? 'Preparing your download...' : 'Download all photos (ZIP)'}
          </button>
          {zipState === 'preparing' && (
            <p className="text-sm text-gray-500 text-center mt-2">
              We are packaging your photos. This takes a minute the first time; the download
              starts on its own when it is ready.
            </p>
          )}
          {zipState === 'error' && (
            <p className="text-sm text-red-600 text-center mt-2">
              We could not prepare the download. Please try again, or use the individual
              links above.
            </p>
          )}
        </>
      )}
    </div>
  );
}
