import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { uploadPhotos } from '../services/api';
import type { PhotoUploadResponse } from '../types/api';
import FileUpload from '../components/FileUpload';
import ErrorMessage from '../components/ErrorMessage';

export default function UploadPage() {
  const { slug } = useParams<{ slug: string }>();
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [uploading, setUploading] = useState(false);
  const [results, setResults] = useState<PhotoUploadResponse[] | null>(null);
  const [error, setError] = useState('');

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!slug || files.length === 0) return;

    setUploading(true);
    setError('');
    try {
      const response = await uploadPhotos(slug, files, email, name);
      setResults(response);
    } catch {
      setError('Upload failed. Please try again.');
    } finally {
      setUploading(false);
    }
  }

  if (results) {
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
          <h2 className="text-2xl font-bold text-gray-900 mb-2">Upload successful!</h2>
          <p className="text-gray-600 mb-4">
            {results.length} photo{results.length !== 1 ? 's' : ''} uploaded. They are being
            processed and will be available shortly.
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

  return (
    <div className="max-w-lg mx-auto">
      <h1 className="text-3xl font-bold text-gray-900 mb-8">Upload photos</h1>

      {error && <div className="mb-4"><ErrorMessage message={error} /></div>}

      <form onSubmit={handleSubmit} className="space-y-6">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Your name</label>
          <input
            type="text"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Your email</label>
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Photos</label>
          <FileUpload onFilesSelected={setFiles} />
          {files.length > 0 && (
            <p className="mt-2 text-sm text-gray-500">
              {files.length} file{files.length !== 1 ? 's' : ''} selected
            </p>
          )}
        </div>

        <button
          type="submit"
          disabled={uploading || files.length === 0}
          className="w-full bg-indigo-600 text-white py-3 rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {uploading ? 'Uploading...' : `Upload ${files.length} photo${files.length !== 1 ? 's' : ''}`}
        </button>
      </form>
    </div>
  );
}
