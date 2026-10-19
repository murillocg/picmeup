import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getEventRevenue } from '../services/api';
import type { EventRevenueResponse } from '../types/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';

const money = (amount: number) => `$${amount.toFixed(2)}`;

export default function AdminRevenuePage() {
  const [rows, setRows] = useState<EventRevenueResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getEventRevenue()
      .then(setRows)
      .catch(() => setError('Failed to load revenue'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error} />;

  const totalRevenue = rows.reduce((sum, r) => sum + r.totalRevenue, 0);
  const totalOrders = rows.reduce((sum, r) => sum + r.orderCount, 0);
  const totalPhotos = rows.reduce((sum, r) => sum + r.photosSold, 0);

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Revenue by Event</h1>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Total Revenue</p>
          <p className="text-3xl font-bold text-green-600">{money(totalRevenue)}</p>
        </div>
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Paid Orders</p>
          <p className="text-3xl font-bold text-gray-900">{totalOrders}</p>
        </div>
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Photos Sold</p>
          <p className="text-3xl font-bold text-gray-900">{totalPhotos}</p>
        </div>
      </div>

      {rows.length === 0 ? (
        <p className="text-gray-500 text-center py-12">No revenue yet</p>
      ) : (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Event</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">Orders</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">Photos</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">Passes</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">Revenue</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">Searches</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">Per Searcher</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {rows.map((row) => {
                // Searches, not people -- someone who fails and retries counts twice, so
                // this reads low until face_searches carries a visitor id.
                const perSearcher = row.totalSearches > 0
                  ? row.totalRevenue / row.totalSearches
                  : 0;
                return (
                  <tr key={row.eventId} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-gray-900">
                      {row.eventSlug ? (
                        <Link to={`/events/${row.eventSlug}`} className="text-brand-orange hover:text-brand-orange-dark font-medium">
                          {row.eventName}
                        </Link>
                      ) : (
                        row.eventName
                      )}
                    </td>
                    <td className="px-4 py-3 text-right text-gray-900">{row.orderCount}</td>
                    <td className="px-4 py-3 text-right text-gray-600">{row.photosSold}</td>
                    <td className="px-4 py-3 text-right text-gray-600">
                      {row.passCount > 0 ? `${row.passCount} (${money(row.passRevenue)})` : '—'}
                    </td>
                    <td className="px-4 py-3 text-right font-medium text-green-600">{money(row.totalRevenue)}</td>
                    <td className="px-4 py-3 text-right text-gray-600">{row.totalSearches}</td>
                    <td className="px-4 py-3 text-right text-gray-600">
                      {row.totalSearches > 0 ? money(perSearcher) : '—'}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <p className="text-xs text-gray-400 mt-4">
        Paid orders and passes only, in AUD. Refunds issued in PayPal are not reflected.
      </p>
    </div>
  );
}
