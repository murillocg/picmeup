import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getSearchStats } from '../services/api';
import type { SearchStatsResponse } from '../types/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';

export default function AdminSearchStatsPage() {
  const [stats, setStats] = useState<SearchStatsResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getSearchStats()
      .then(setStats)
      .catch(() => setError('Failed to load search stats'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error} />;

  const totalSearches = stats.reduce((sum, s) => sum + s.totalSearches, 0);
  const totalWithResults = stats.reduce((sum, s) => sum + s.searchesWithResults, 0);

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Face Search Stats</h1>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Total Searches</p>
          <p className="text-3xl font-bold text-gray-900">{totalSearches}</p>
        </div>
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Found Photos</p>
          <p className="text-3xl font-bold text-green-600">{totalWithResults}</p>
        </div>
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
          <p className="text-sm text-gray-500">No Results</p>
          <p className="text-3xl font-bold text-red-500">{totalSearches - totalWithResults}</p>
        </div>
      </div>

      {stats.length === 0 ? (
        <p className="text-gray-500 text-center py-12">No searches yet</p>
      ) : (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Event</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">Total Searches</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">Found Photos</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">No Results</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">Conversion</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {stats
                .sort((a, b) => b.totalSearches - a.totalSearches)
                .map((stat) => {
                  const noResults = stat.totalSearches - stat.searchesWithResults;
                  const conversionRate = stat.totalSearches > 0
                    ? ((stat.searchesWithResults / stat.totalSearches) * 100).toFixed(0)
                    : '0';
                  return (
                    <tr key={stat.eventId} className="hover:bg-gray-50">
                      <td className="px-4 py-3 text-gray-900">
                        {stat.eventSlug ? (
                          <Link to={`/events/${stat.eventSlug}`} className="text-indigo-600 hover:text-indigo-800 font-medium">
                            {stat.eventName}
                          </Link>
                        ) : (
                          stat.eventName
                        )}
                      </td>
                      <td className="px-4 py-3 text-right text-gray-900 font-medium">{stat.totalSearches}</td>
                      <td className="px-4 py-3 text-right text-green-600">{stat.searchesWithResults}</td>
                      <td className="px-4 py-3 text-right text-red-500">{noResults}</td>
                      <td className="px-4 py-3 text-right">
                        <span className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${
                          Number(conversionRate) >= 50
                            ? 'bg-green-100 text-green-700'
                            : Number(conversionRate) >= 25
                              ? 'bg-yellow-100 text-yellow-700'
                              : 'bg-red-100 text-red-700'
                        }`}>
                          {conversionRate}%
                        </span>
                      </td>
                    </tr>
                  );
                })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
