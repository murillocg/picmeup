import { useEffect, useState } from 'react';
import { getPlatformUsage } from '../services/api';
import type { PlatformUsageResponse } from '../types/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';
import usePageTitle from '../hooks/usePageTitle';

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
}

export default function AdminUsagePage() {
  usePageTitle('Platform Usage');
  const [usage, setUsage] = useState<PlatformUsageResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getPlatformUsage()
      .then(setUsage)
      .catch(() => setError('Failed to load usage data'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error} />;
  if (!usage) return null;

  const storageGB = usage.storage.totalBytes / (1024 * 1024 * 1024);
  const freeStorageGB = 5;
  const paidStorageGB = Math.max(0, storageGB - freeStorageGB);
  const storageCost = paidStorageGB * 0.025;

  const freeRecognitionOps = 5000;
  const totalRecognitionOpsThisMonth = usage.facialRecognition.facesIndexedThisMonth + usage.facialRecognition.searchesThisMonth;
  const paidRecognitionOps = Math.max(0, totalRecognitionOpsThisMonth - freeRecognitionOps);
  const recognitionCost = paidRecognitionOps * 0.001;
  const estimatedMonthlyCost = storageCost + recognitionCost;

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Platform Usage</h1>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Events</p>
          <p className="text-3xl font-bold text-gray-900">{usage.totalEvents}</p>
        </div>
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Photos</p>
          <p className="text-3xl font-bold text-gray-900">{usage.totalPhotos.toLocaleString()}</p>
        </div>
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Total Storage</p>
          <p className="text-3xl font-bold text-gray-900">{formatBytes(usage.storage.totalBytes)}</p>
        </div>
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Estimated Monthly Cost</p>
          <p className="text-3xl font-bold text-green-600">${estimatedMonthlyCost.toFixed(2)}</p>
          <p className="text-xs text-gray-400 mt-1">USD (variable costs only)</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Storage</h2>
          <table className="w-full text-sm">
            <tbody className="divide-y divide-gray-100">
              <tr>
                <td className="py-2 text-gray-600">Original photos</td>
                <td className="py-2 text-right font-medium text-gray-900">{formatBytes(usage.storage.originalsBytes)}</td>
              </tr>
              <tr>
                <td className="py-2 text-gray-600">Thumbnails</td>
                <td className="py-2 text-right font-medium text-gray-900">{formatBytes(usage.storage.thumbnailsBytes)}</td>
              </tr>
              <tr className="border-t-2 border-gray-200">
                <td className="py-2 text-gray-900 font-medium">Total</td>
                <td className="py-2 text-right font-bold text-gray-900">{formatBytes(usage.storage.totalBytes)}</td>
              </tr>
              <tr>
                <td className="py-2 text-gray-600">Free tier ({freeStorageGB} GB/month)</td>
                <td className="py-2 text-right font-medium">
                  <span className={storageGB > freeStorageGB ? 'text-red-500' : 'text-green-600'}>
                    {storageGB.toFixed(2)} / {freeStorageGB.toFixed(0)} GB
                  </span>
                </td>
              </tr>
              <tr>
                <td className="py-2 text-gray-600">Estimated cost</td>
                <td className="py-2 text-right font-medium text-green-600">${storageCost.toFixed(2)}/month</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Facial Recognition</h2>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="py-2 text-left text-gray-500 font-medium"></th>
                <th className="py-2 text-right text-gray-500 font-medium">This Month</th>
                <th className="py-2 text-right text-gray-500 font-medium">All Time</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              <tr>
                <td className="py-2 text-gray-600">Faces indexed</td>
                <td className="py-2 text-right font-medium text-gray-900">{usage.facialRecognition.facesIndexedThisMonth.toLocaleString()}</td>
                <td className="py-2 text-right font-medium text-gray-900">{usage.facialRecognition.facesIndexedAllTime.toLocaleString()}</td>
              </tr>
              <tr>
                <td className="py-2 text-gray-600">Searches</td>
                <td className="py-2 text-right font-medium text-gray-900">{usage.facialRecognition.searchesThisMonth.toLocaleString()}</td>
                <td className="py-2 text-right font-medium text-gray-900">{usage.facialRecognition.searchesAllTime.toLocaleString()}</td>
              </tr>
              <tr className="border-t-2 border-gray-200">
                <td className="py-2 text-gray-600">Total operations this month</td>
                <td className="py-2 text-right font-bold text-gray-900">{totalRecognitionOpsThisMonth.toLocaleString()}</td>
                <td></td>
              </tr>
              <tr>
                <td className="py-2 text-gray-600">Free tier (5,000/month)</td>
                <td className="py-2 text-right font-medium text-gray-900">
                  <span className={totalRecognitionOpsThisMonth > freeRecognitionOps ? 'text-red-500' : 'text-green-600'}>
                    {Math.min(totalRecognitionOpsThisMonth, freeRecognitionOps).toLocaleString()} / {freeRecognitionOps.toLocaleString()}
                  </span>
                </td>
                <td></td>
              </tr>
              {paidRecognitionOps > 0 && (
                <tr>
                  <td className="py-2 text-gray-600">Paid operations</td>
                  <td className="py-2 text-right font-medium text-red-500">{paidRecognitionOps.toLocaleString()}</td>
                  <td></td>
                </tr>
              )}
              <tr>
                <td className="py-2 text-gray-600">Estimated cost</td>
                <td className="py-2 text-right font-medium text-green-600">${recognitionCost.toFixed(2)}/month</td>
                <td></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
