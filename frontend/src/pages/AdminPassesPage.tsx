import { useEffect, useState } from 'react';
import { listPasses } from '../services/api';
import type { EventPassResponse } from '../types/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';

export default function AdminPassesPage() {
  const [passes, setPasses] = useState<EventPassResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    listPasses()
      .then(setPasses)
      .catch(() => setError('Failed to load passes'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error} />;

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Photo Passes</h1>

      {passes.length === 0 ? (
        <p className="text-gray-500 text-center py-12">No passes yet</p>
      ) : (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Email</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">Price</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Redeemed</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {passes.map((pass) => (
                <tr key={pass.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-gray-600">
                    {new Date(pass.createdAt).toLocaleDateString('en-AU', {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </td>
                  <td className="px-4 py-3 text-gray-900">{pass.buyerEmail}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${
                        pass.status === 'REDEEMED'
                          ? 'bg-blue-100 text-blue-700'
                          : pass.status === 'PAID'
                            ? 'bg-green-100 text-green-700'
                            : 'bg-yellow-100 text-yellow-700'
                      }`}
                    >
                      {pass.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right text-gray-900">
                    ${pass.price.toFixed(2)} {pass.currency}
                  </td>
                  <td className="px-4 py-3 text-gray-600">
                    {pass.redeemedAt
                      ? new Date(pass.redeemedAt).toLocaleDateString('en-AU', {
                          year: 'numeric',
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })
                      : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
