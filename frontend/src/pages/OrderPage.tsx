import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getOrder } from '../services/api';
import type { OrderResponse } from '../types/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';
import usePageTitle from '../hooks/usePageTitle';

export default function OrderPage() {
  usePageTitle('Order');
  const { id } = useParams<{ id: string }>();
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

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
          <p className="text-green-800 text-sm mt-2">
            On a phone, tap <span className="font-semibold">View</span> to open a photo, then press and hold it to save or share it.
          </p>
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
                <div className="flex items-center gap-2 shrink-0">
                  {item.viewUrl && (
                    <a
                      href={item.viewUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="border border-brand-orange text-brand-orange px-3 py-1.5 rounded-lg hover:bg-orange-50 text-sm"
                    >
                      View
                    </a>
                  )}
                  <a
                    href={item.downloadUrl}
                    download
                    className="bg-brand-orange text-white px-3 py-1.5 rounded-lg hover:bg-brand-orange-dark text-sm"
                  >
                    Download
                  </a>
                </div>
              ) : (
                <span className="text-sm text-gray-400 shrink-0">Unavailable</span>
              )}
            </div>
          ))}
        </div>
      </div>

      {isPaid && (
        <a
          href={`/api/orders/${order.id}/downloads/zip`}
          download
          className="block w-full text-center bg-green-600 text-white py-3 rounded-lg hover:bg-green-700 font-semibold"
        >
          Download all photos (ZIP)
        </a>
      )}
    </div>
  );
}
