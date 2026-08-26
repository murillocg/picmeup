import { useState, useRef } from 'react';
import { Link, useParams, Navigate } from 'react-router-dom';
import { uploadPhoto, UploadError } from '../services/api';
import type { UploadErrorKind } from '../services/api';
import { useAuth } from '../context/AuthContext';
import FileUpload from '../components/FileUpload';
import ErrorMessage from '../components/ErrorMessage';

type Failure = {
  file: File;
  kind: UploadErrorKind;
  reason: string;
};

// S3 speaks HTTP/1.1 and browsers cap connections at 6 per host, so more workers
// than this just queue. Extra connections only help when the uplink isn't already
// saturated — when it is, nothing here beats the photographer's upstream bandwidth.
const CONCURRENCY = 6;

const MAX_FILES = 1000;

// Identity is name + size + lastModified: the same file picked twice. Two *different*
// photos that merely share a filename are left alone, so the server still reports them
// as duplicates rather than one being silently dropped here.
function fileKey(file: File): string {
  return `${file.name}:${file.size}:${file.lastModified}`;
}

// The picker appends, so selecting the same folder twice queues every file twice — and
// two workers presigning one filename at the same moment both pass the server's
// duplicate check, landing two copies in the event.
function mergeFiles(existing: File[], incoming: File[]): { files: File[]; duplicates: number } {
  const seen = new Set(existing.map(fileKey));
  const merged = [...existing];
  let duplicates = 0;

  for (const file of incoming) {
    const key = fileKey(file);
    if (seen.has(key)) {
      duplicates++;
      continue;
    }
    seen.add(key);
    merged.push(file);
  }

  return { files: merged.slice(0, MAX_FILES), duplicates };
}

export default function UploadPage() {
  const { slug } = useParams<{ slug: string }>();
  const { authenticated, loading: authLoading } = useAuth();
  const [files, setFiles] = useState<File[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploaded, setUploaded] = useState(0);
  const [failures, setFailures] = useState<Failure[]>([]);
  const [done, setDone] = useState(false);
  const [batchTotal, setBatchTotal] = useState(0);
  const [error, setError] = useState('');
  const [timeRemaining, setTimeRemaining] = useState<string | null>(null);
  const [throughput, setThroughput] = useState<number | null>(null);
  const [duplicatesIgnored, setDuplicatesIgnored] = useState(0);
  const abortRef = useRef<AbortController | null>(null);

  if (!authLoading && !authenticated) return <Navigate to="/" />;

  async function runUpload(batch: File[]) {
    if (!slug || batch.length === 0) return;

    const controller = new AbortController();
    abortRef.current = controller;

    setUploading(true);
    setDone(false);
    setUploaded(0);
    setFailures([]);
    setError('');
    setTimeRemaining(null);
    setThroughput(null);
    setBatchTotal(batch.length);

    const total = batch.length;
    const totalBytes = batch.reduce((sum, file) => sum + file.size, 0);
    const startTime = Date.now();
    let completedCount = 0;
    let uploadedBytes = 0;
    let processedBytes = 0;
    const failed: Failure[] = [];

    async function uploadNext(index: number) {
      while (index < total && !controller.signal.aborted) {
        const currentIndex = index;
        index += CONCURRENCY;
        const file = batch[currentIndex];

        try {
          await uploadPhoto(slug!, file, controller.signal);
          completedCount++;
          uploadedBytes += file.size;
        } catch (caught) {
          const failure =
            caught instanceof UploadError
              ? caught
              : new UploadError('unknown', 'Unexpected error', false);

          if (failure.kind === 'aborted') continue;

          failed.push({ file, kind: failure.kind, reason: failure.message });

          // Every remaining file would fail the same way, so stop instead of
          // grinding through hundreds of doomed uploads.
          if (failure.kind === 'auth') {
            setError('Your admin session expired. Log in again, then retry the remaining files.');
            controller.abort();
          }
        }

        setUploaded(completedCount);
        setFailures([...failed]);

        processedBytes += file.size;
        const processed = completedCount + failed.length;
        const elapsed = Date.now() - startTime;

        // The first few completions land together because the workers start together,
        // which makes an early estimate wildly pessimistic. Wait for a steady state,
        // then rate by bytes rather than by file — file sizes vary too much.
        if (processed >= CONCURRENCY * 2 && uploadedBytes > 0) {
          const bytesPerMs = uploadedBytes / elapsed;
          setThroughput((bytesPerMs * 1000) / (1024 * 1024));
          setTimeRemaining(formatTime((totalBytes - processedBytes) / bytesPerMs));
        }
      }
    }

    const workers = Array.from({ length: Math.min(CONCURRENCY, total) }, (_, i) => uploadNext(i));
    await Promise.all(workers);

    abortRef.current = null;
    setUploading(false);
    setDone(true);
    setTimeRemaining(null);
    setThroughput(null);
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    void runUpload(files);
  }

  function formatTime(ms: number): string {
    const seconds = Math.ceil(ms / 1000);
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${minutes}m ${secs}s`;
  }

  const duplicates = failures.filter((f) => f.kind === 'duplicate');
  const errors = failures.filter((f) => f.kind !== 'duplicate');

  if (done) {
    const stopped = uploaded + failures.length < batchTotal;

    return (
      <div className="max-w-lg mx-auto">
        <div
          className={`border rounded-lg p-8 ${
            errors.length > 0 || stopped ? 'bg-amber-50 border-amber-200' : 'bg-green-50 border-green-200'
          }`}
        >
          <div className="text-center">
            {errors.length > 0 || stopped ? (
              <svg
                className="mx-auto w-16 h-16 text-amber-500 mb-4"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"
                />
              </svg>
            ) : (
              <svg
                className="mx-auto w-16 h-16 text-green-500 mb-4"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            )}
            <h2 className="text-2xl font-bold text-gray-900 mb-2">
              {stopped
                ? 'Upload stopped'
                : errors.length > 0
                  ? 'Upload finished with problems'
                  : 'Upload complete!'}
            </h2>
            <p className="text-gray-600">
              {uploaded} of {batchTotal} photo{batchTotal !== 1 ? 's' : ''} uploaded successfully.
              {stopped && ` ${batchTotal - uploaded - failures.length} were not attempted.`}
              {uploaded > 0 && ' They are being processed and will be available shortly.'}
            </p>
          </div>

          {duplicates.length > 0 && (
            <FailureGroup
              tone="neutral"
              title={`${duplicates.length} photo${duplicates.length !== 1 ? 's were' : ' was'} already in this event and ${
                duplicates.length !== 1 ? 'were' : 'was'
              } skipped`}
              files={duplicates.map((f) => f.file.name)}
            />
          )}

          {errors.length > 0 && <ErrorBreakdown failures={errors} />}

          <div className="mt-6 flex flex-col sm:flex-row gap-3">
            {errors.length > 0 && (
              <button
                type="button"
                onClick={() => void runUpload(errors.map((f) => f.file))}
                className="flex-1 bg-brand-orange text-white px-6 py-3 rounded-lg hover:bg-brand-orange-dark"
              >
                Retry {errors.length} failed photo{errors.length !== 1 ? 's' : ''}
              </button>
            )}
            <Link
              to={`/events/${slug}`}
              className={`flex-1 text-center px-6 py-3 rounded-lg ${
                errors.length > 0
                  ? 'border border-gray-300 text-gray-700 hover:bg-gray-50'
                  : 'bg-brand-orange text-white hover:bg-brand-orange-dark'
              }`}
            >
              View event
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const processed = uploaded + failures.length;
  const progress = files.length > 0 ? (processed / files.length) * 100 : 0;

  return (
    <div className="max-w-lg mx-auto">
      <h1 className="text-3xl font-bold text-gray-900 mb-8">Upload photos</h1>

      {error && <div className="mb-4"><ErrorMessage message={error} /></div>}

      <form onSubmit={handleSubmit} className="space-y-6">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Photos</label>
          {!uploading && (
            <FileUpload
              onFilesSelected={(newFiles) => {
                const merged = mergeFiles(files, newFiles);
                setFiles(merged.files);
                setDuplicatesIgnored(merged.duplicates);
              }}
            />
          )}

          {files.length > 0 && (
            <div className="mt-4">
              <div className="flex items-center justify-between mb-2">
                <p className="text-sm text-gray-500">
                  {files.length} file{files.length !== 1 ? 's' : ''} selected
                  {duplicatesIgnored > 0 && (
                    <span className="text-gray-400">
                      {' '}
                      ({duplicatesIgnored} already in the list, skipped)
                    </span>
                  )}
                </p>
                {!uploading && (
                  <button
                    type="button"
                    onClick={() => {
                      setFiles([]);
                      setDuplicatesIgnored(0);
                    }}
                    className="text-sm text-red-600 hover:text-red-700"
                  >
                    Remove all
                  </button>
                )}
              </div>
              {files.length <= 50 ? (
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
              ) : (
                <div className="bg-gray-50 rounded-lg p-4 text-sm text-gray-600">
                  {files.length} photos ready to upload ({(files.reduce((sum, f) => sum + f.size, 0) / (1024 * 1024)).toFixed(0)} MB total)
                </div>
              )}
            </div>
          )}
        </div>

        {uploading && (
          <div className="space-y-3">
            <div className="flex items-center justify-between text-sm">
              <span className="font-medium text-gray-700">
                {uploaded} of {files.length} uploaded
              </span>
              <span className="text-gray-500">
                {throughput !== null && `${throughput.toFixed(1)} MB/s`}
                {throughput !== null && timeRemaining && ' · '}
                {timeRemaining ? `~${timeRemaining} remaining` : !throughput && 'estimating…'}
              </span>
            </div>
            <div className="w-full bg-gray-200 rounded-full h-3">
              <div
                className="bg-brand-orange h-3 rounded-full transition-all duration-300"
                style={{ width: `${progress}%` }}
              />
            </div>
            <div className="flex flex-wrap gap-x-4 text-sm">
              {duplicates.length > 0 && (
                <span className="text-gray-500">{duplicates.length} already uploaded, skipped</span>
              )}
              {errors.length > 0 && (
                <span className="text-red-500">
                  {errors.length} failed &mdash; {errors[errors.length - 1].reason}
                </span>
              )}
            </div>
            <button
              type="button"
              onClick={() => abortRef.current?.abort()}
              className="text-sm text-gray-500 hover:text-gray-700 underline"
            >
              Stop uploading
            </button>
          </div>
        )}

        <button
          type="submit"
          disabled={uploading || files.length === 0}
          className="w-full bg-brand-orange text-white py-3 rounded-lg hover:bg-brand-orange-dark disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {uploading
            ? `Uploading ${Math.min(processed + 1, files.length)}/${files.length}...`
            : `Upload ${files.length} photo${files.length !== 1 ? 's' : ''}`}
        </button>
      </form>
    </div>
  );
}

function ErrorBreakdown({ failures }: { failures: Failure[] }) {
  const byReason = new Map<string, string[]>();
  for (const failure of failures) {
    const names = byReason.get(failure.reason) ?? [];
    names.push(failure.file.name);
    byReason.set(failure.reason, names);
  }

  return (
    <div className="mt-6 space-y-3">
      {[...byReason.entries()].map(([reason, names]) => (
        <FailureGroup
          key={reason}
          tone="error"
          title={`${names.length} photo${names.length !== 1 ? 's' : ''} — ${reason}`}
          files={names}
        />
      ))}
    </div>
  );
}

function FailureGroup({
  tone,
  title,
  files,
}: {
  tone: 'error' | 'neutral';
  title: string;
  files: string[];
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div
      className={`mt-4 rounded-lg border p-3 text-sm ${
        tone === 'error' ? 'bg-red-50 border-red-200 text-red-700' : 'bg-white border-gray-200 text-gray-600'
      }`}
    >
      <div className="flex items-start justify-between gap-3">
        <p className="font-medium">{title}</p>
        <button
          type="button"
          onClick={() => setExpanded((prev) => !prev)}
          className="shrink-0 underline hover:no-underline"
        >
          {expanded ? 'Hide' : 'Show files'}
        </button>
      </div>
      {expanded && (
        <>
          <ul className="mt-2 max-h-40 overflow-y-auto space-y-0.5 font-mono text-xs">
            {files.map((name) => (
              <li key={name} className="truncate" title={name}>
                {name}
              </li>
            ))}
          </ul>
          <button
            type="button"
            onClick={() => void navigator.clipboard?.writeText(files.join('\n'))}
            className="mt-2 underline hover:no-underline"
          >
            Copy filenames
          </button>
        </>
      )}
    </div>
  );
}
