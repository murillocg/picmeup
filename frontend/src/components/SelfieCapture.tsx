import { useRef, useState, useCallback, useEffect } from 'react';

interface SelfieCaptureProps {
  onCapture: (file: File) => void;
  loading: boolean;
}

export default function SelfieCapture({ onCapture, loading }: SelfieCaptureProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [webcamActive, setWebcamActive] = useState(false);
  const [webcamError, setWebcamError] = useState('');

  const stopWebcam = useCallback(() => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }
    setWebcamActive(false);
  }, []);

  useEffect(() => {
    if (webcamActive && videoRef.current && streamRef.current) {
      videoRef.current.srcObject = streamRef.current;
    }
  }, [webcamActive]);

  useEffect(() => {
    return () => {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((t) => t.stop());
      }
    };
  }, []);

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setPreview(URL.createObjectURL(file));
    stopWebcam();
    onCapture(file);
  }

  async function startWebcam() {
    setWebcamError('');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } },
      });
      streamRef.current = stream;
      setWebcamActive(true);
    } catch {
      setWebcamError('Could not access camera. Please allow camera access or upload a photo instead.');
    }
  }

  function captureFromWebcam() {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas) return;

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.translate(canvas.width, 0);
    ctx.scale(-1, 1);
    ctx.drawImage(video, 0, 0);

    canvas.toBlob((blob) => {
      if (!blob) return;
      const file = new File([blob], 'selfie.jpg', { type: 'image/jpeg' });
      setPreview(canvas.toDataURL('image/jpeg'));
      stopWebcam();
      onCapture(file);
    }, 'image/jpeg', 0.9);
  }

  return (
    <div className="flex flex-col items-center gap-4">
      {webcamActive ? (
        <div className="relative">
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            className="w-64 h-48 rounded-lg object-cover border-2 border-indigo-200"
            style={{ transform: 'scaleX(-1)' }}
          />
          <div className="flex gap-2 mt-3 justify-center">
            <button
              onClick={captureFromWebcam}
              className="bg-indigo-600 text-white px-6 py-2 rounded-lg hover:bg-indigo-700 text-sm font-medium"
            >
              Capture
            </button>
            <button
              onClick={stopWebcam}
              className="bg-gray-200 text-gray-700 px-6 py-2 rounded-lg hover:bg-gray-300 text-sm font-medium"
            >
              Cancel
            </button>
          </div>
        </div>
      ) : preview ? (
        <img
          src={preview}
          alt="Selfie preview"
          className="w-32 h-32 rounded-full object-cover border-4 border-indigo-200"
        />
      ) : (
        <div className="w-32 h-32 rounded-full bg-gray-200 flex items-center justify-center">
          <svg className="w-12 h-12 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0" />
          </svg>
        </div>
      )}

      <canvas ref={canvasRef} className="hidden" />

      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        onChange={handleFileChange}
        className="hidden"
      />

      {webcamError && (
        <p className="text-red-500 text-sm text-center">{webcamError}</p>
      )}

      {!webcamActive && (
        <div className="flex gap-3">
          {'ontouchstart' in window && (
            <button
              onClick={() => fileInputRef.current?.click()}
              disabled={loading}
              className="bg-white text-indigo-600 border border-indigo-600 px-6 py-3 rounded-lg hover:bg-indigo-50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Searching...' : 'Upload a photo'}
            </button>
          )}
          <button
            onClick={startWebcam}
            disabled={loading}
            className="bg-indigo-600 text-white px-6 py-3 rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? 'Searching...' : preview ? 'Take another selfie' : 'Take a selfie'}
          </button>
        </div>
      )}
    </div>
  );
}
