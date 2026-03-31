import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getOrder } from '../services/api';
import type { OrderResponse } from '../types/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';

export default function OrderPage() {
  const { id } = useParams<{ id: string }>();
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!id) return;

    // Check that this order belongs to the user (stored in localStorage)
    const orders: string[] = JSON.parse(localStorage.getItem('orders') || '[]');
    if (!orders.includes(id)) {
      setError('Order not found');
      setLoading(false);
      return;
    }

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
              className="flex items-center justify-between border border-gray-100 rounded-lg p-3"
            >
              <span className="text-sm text-gray-600">Photo — ${item.price.toFixed(2)}</span>
              {isPaid && item.downloadUrl ? (
                <a
                  href={item.downloadUrl}
                  download
                  className="bg-indigo-600 text-white px-4 py-1.5 rounded-lg hover:bg-indigo-700 text-sm"
                >
                  Download
                </a>
              ) : (
                <span className="text-sm text-gray-400">Unavailable</span>
              )}
            </div>
          ))}
        </div>
      </div>

      {isPaid && (
        <button
          onClick={() => {
            order.items.forEach((item) => {
              if (item.downloadUrl) {
                const a = document.createElement('a');
                a.href = item.downloadUrl;
                a.download = '';
                a.click();
              }
            });
          }}
          className="w-full bg-green-600 text-white py-3 rounded-lg hover:bg-green-700 font-semibold"
        >
          Download all photos
        </button>
      )}
    </div>
  );
}
