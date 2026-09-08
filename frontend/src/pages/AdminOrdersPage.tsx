import { useEffect, useState } from 'react';
import { listOrders } from '../services/api';
import type { OrderSummaryResponse } from '../types/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';
import usePageTitle from '../hooks/usePageTitle';

const PAGE_SIZE = 25;

// Whatever zone the reader is actually in, named once above the table rather than
// repeated on every row. Intl reports the resolved zone, so this stays right whether
// the page is opened from Australia or anywhere else.
const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

const formatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

export default function AdminOrdersPage() {
  usePageTitle('Orders');
  const [orders, setOrders] = useState<OrderSummaryResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    listOrders(page, PAGE_SIZE)
      .then((result) => {
        setOrders(result.content);
        setTotalPages(result.page.totalPages);
        setTotalElements(result.page.totalElements);
      })
      .catch(() => setError('Failed to load orders'))
      .finally(() => setLoading(false));
  }, [page]);

  if (loading && orders.length === 0) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error} />;

  const firstOnPage = page * PAGE_SIZE + 1;
  const lastOnPage = page * PAGE_SIZE + orders.length;

  return (
    <div>
      <div className="flex items-baseline justify-between mb-6 gap-4 flex-wrap">
        <h1 className="text-2xl font-bold text-gray-900">Orders</h1>
        {totalElements > 0 && (
          <p className="text-sm text-gray-500">
            {firstOnPage}&ndash;{lastOnPage} of {totalElements} &middot; times shown in{' '}
            <span className="font-medium text-gray-700">{timeZone}</span>
          </p>
        )}
      </div>

      {orders.length === 0 ? (
        <p className="text-gray-500 text-center py-12">No orders yet</p>
      ) : (
        <>
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Email</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">Amount</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">Link</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {orders.map((order) => (
                  <tr key={order.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-gray-600 whitespace-nowrap">
                      {formatter.format(new Date(order.createdAt))}
                    </td>
                    <td className="px-4 py-3 text-gray-900">{order.buyerEmail}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${
                          order.status === 'PAID'
                            ? 'bg-green-100 text-green-700'
                            : order.status === 'PENDING'
                              ? 'bg-yellow-100 text-yellow-700'
                              : 'bg-red-100 text-red-700'
                        }`}
                      >
                        {order.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right whitespace-nowrap">
                      {order.free ? (
                        // A $0.00 total otherwise reads like a failed charge. Say why.
                        <span className="inline-block px-2 py-0.5 rounded text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200">
                          Free event
                        </span>
                      ) : (
                        <span className="text-gray-900">
                          ${order.totalAmount.toFixed(2)} {order.currency}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <a
                        href={`/orders/${order.id}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-brand-orange hover:text-brand-orange-dark font-medium"
                      >
                        View order
                      </a>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="flex items-center justify-between mt-4 gap-4">
              <button
                onClick={() => setPage((p) => Math.max(p - 1, 0))}
                disabled={page === 0 || loading}
                className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed text-sm"
              >
                Previous
              </button>
              <span className="text-sm text-gray-500 tabular-nums">
                Page {page + 1} of {totalPages}
              </span>
              <button
                onClick={() => setPage((p) => Math.min(p + 1, totalPages - 1))}
                disabled={page >= totalPages - 1 || loading}
                className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed text-sm"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
