import { useState, useRef } from 'react';
import { Link, useParams, Navigate } from 'react-router-dom';
import { uploadPhoto } from '../services/api';
import { useAuth } from '../context/AuthContext';
import FileUpload from '../components/FileUpload';
import ErrorMessage from '../components/ErrorMessage';

export default function UploadPage() {
  const { slug } = useParams<{ slug: string }>();
  const { authenticated, loading: authLoading } = useAuth();
  const [files, setFiles] = useState<File[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploaded, setUploaded] = useState(0);
  const [failed, setFailed] = useState(0);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');
  const [timeRemaining, setTimeRemaining] = useState<string | null>(null);
  const abortRef = useRef(false);

  if (!authLoading && !authenticated) return <Navigate to="/" />;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!slug || files.length === 0) return;

    setUploading(true);
    setUploaded(0);
    setFailed(0);
    setError('');
    setTimeRemaining(null);
    abortRef.current = false;

    const total = files.length;
    const startTime = Date.now();
    let completedCount = 0;
    let failedCount = 0;

    for (let i = 0; i < total; i++) {
      if (abortRef.current) break;

      try {
        await uploadPhoto(slug, files[i]);
        completedCount++;
      } catch {
        failedCount++;
      }

      setUploaded(completedCount);
      setFailed(failedCount);

      const elapsed = Date.now() - startTime;
      const avgPerFile = elapsed / (i + 1);
      const remaining = avgPerFile * (total - i - 1);
      setTimeRemaining(formatTime(remaining));
    }

    setUploading(false);
    setDone(true);
    setTimeRemaining(null);
  }

  function formatTime(ms: number): string {
    const seconds = Math.ceil(ms / 1000);
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${minutes}m ${secs}s`;
  }

  if (done) {
    const total = uploaded + failed;
    return (
      <div className="max-w-lg mx-auto text-center">
        <div className="bg-green-50 border border-green-200 rounded-lg p-8">
          <svg
            className="mx-auto w-16 h-16 text-green-500 mb-4"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
          </svg>
          <h2 className="text-2xl font-bold text-gray-900 mb-2">Upload complete!</h2>
          <p className="text-gray-600 mb-4">
            {uploaded} of {total} photo{total !== 1 ? 's' : ''} uploaded successfully.
            {failed > 0 && ` ${failed} failed.`}
            {' '}They are being processed and will be available shortly.
          </p>
          <Link
            to={`/events/${slug}`}
            className="inline-block bg-indigo-600 text-white px-6 py-3 rounded-lg hover:bg-indigo-700"
          >
            View event
          </Link>
        </div>
      </div>
    );
  }

  const progress = files.length > 0 ? ((uploaded + failed) / files.length) * 100 : 0;

  return (
    <div className="max-w-lg mx-auto">
      <h1 className="text-3xl font-bold text-gray-900 mb-8">Upload photos</h1>

      {error && <div className="mb-4"><ErrorMessage message={error} /></div>}

      <form onSubmit={handleSubmit} className="space-y-6">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Photos</label>
          {!uploading && (
            <FileUpload onFilesSelected={(newFiles) => setFiles((prev) => [...prev, ...newFiles].slice(0, 50))} />
          )}

          {files.length > 0 && (
            <div className="mt-4">
              <div className="flex items-center justify-between mb-2">
                <p className="text-sm text-gray-500">
                  {files.length} file{files.length !== 1 ? 's' : ''} selected
                </p>
                {!uploading && (
                  <button
                    type="button"
                    onClick={() => setFiles([])}
                    className="text-sm text-red-600 hover:text-red-700"
                  >
                    Remove all
                  </button>
                )}
              </div>
              <div className="grid grid-cols-4 gap-2">
                {files.map((file, index) => (
                  <div key={`${file.name}-${index}`} className="relative group">
                    <img
                      src={URL.createObjectURL(file)}
                      alt={file.name}
                      className="w-full h-24 object-cover rounded-lg"
                      onLoad={(e) => URL.revokeObjectURL((e.target as HTMLImageElement).src)}
                    />
                    {!uploading && (
                      <button
                        type="button"
                        onClick={() => setFiles((prev) => prev.filter((_, i) => i !== index))}
                        className="absolute top-1 right-1 bg-black/60 text-white rounded-full w-5 h-5 flex items-center justify-center text-xs opacity-0 group-hover:opacity-100 transition-opacity"
                      >
                        &times;
                      </button>
                    )}
                    <p className="text-xs text-gray-400 mt-1 truncate">{file.name}</p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {uploading && (
          <div className="space-y-3">
            <div className="flex items-center justify-between text-sm">
              <span className="font-medium text-gray-700">
                {uploaded + failed}/{files.length} uploaded
              </span>
              {timeRemaining && (
                <span className="text-gray-500">~{timeRemaining} remaining</span>
              )}
            </div>
            <div className="w-full bg-gray-200 rounded-full h-3">
              <div
                className="bg-indigo-600 h-3 rounded-full transition-all duration-300"
                style={{ width: `${progress}%` }}
              />
            </div>
            {failed > 0 && (
              <p className="text-sm text-red-500">{failed} file{failed !== 1 ? 's' : ''} failed</p>
            )}
          </div>
        )}

        <button
          type="submit"
          disabled={uploading || files.length === 0}
          className="w-full bg-indigo-600 text-white py-3 rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {uploading
            ? `Uploading ${uploaded + failed + 1}/${files.length}...`
            : `Upload ${files.length} photo${files.length !== 1 ? 's' : ''}`}
        </button>
      </form>
    </div>
  );
}
