import { useEffect } from 'react';

interface PhotoLightboxProps {
  imageUrl: string;
  onClose: () => void;
}

export default function PhotoLightbox({ imageUrl, onClose }: PhotoLightboxProps) {
  useEffect(() => {
    function handleKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', handleKey);
    return () => document.removeEventListener('keydown', handleKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4"
      onClick={onClose}
    >
      <button
        className="absolute top-4 right-4 text-white text-3xl hover:text-gray-300"
        onClick={onClose}
      >
        &times;
      </button>
      <img
        src={imageUrl}
        alt="Photo preview"
        className="max-w-full max-h-[90vh] object-contain rounded-lg"
        onClick={(e) => e.stopPropagation()}
      />
    </div>
  );
}
