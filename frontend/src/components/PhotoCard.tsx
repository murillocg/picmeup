import type { PhotoResponse } from '../types/api';

interface PhotoCardProps {
  photo: PhotoResponse;
  selected: boolean;
  onToggle: () => void;
  onView: () => void;
  selectable: boolean;
}

export default function PhotoCard({ photo, selected, onToggle, onView, selectable }: PhotoCardProps) {
  if (!photo.thumbnailUrl) {
    return (
      <div className="aspect-square bg-gray-200 rounded-lg flex items-center justify-center text-gray-400">
        Processing...
      </div>
    );
  }

  return (
    <div
      className={`relative aspect-square rounded-lg overflow-hidden cursor-pointer border-2 transition-all ${
        selected ? 'border-indigo-600 ring-2 ring-indigo-300' : 'border-transparent'
      }`}
      onClick={onView}
    >
      <img
        src={photo.thumbnailUrl}
        alt="Event photo"
        className="w-full h-full object-cover"
        loading="lazy"
      />
      {selectable && (
        <div
          className={`absolute top-2 right-2 w-6 h-6 rounded-full border-2 flex items-center justify-center ${
            selected
              ? 'bg-indigo-600 border-indigo-600 text-white'
              : 'bg-white/80 border-gray-300'
          }`}
          onClick={(e) => {
            e.stopPropagation();
            onToggle();
          }}
        >
          {selected && (
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
            </svg>
          )}
        </div>
      )}
    </div>
  );
}
