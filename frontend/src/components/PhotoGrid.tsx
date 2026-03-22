import type { PhotoResponse } from '../types/api';
import PhotoCard from './PhotoCard';

interface PhotoGridProps {
  photos: PhotoResponse[];
  selectedIds: Set<string>;
  onToggleSelect: (id: string) => void;
  selectable?: boolean;
}

export default function PhotoGrid({
  photos,
  selectedIds,
  onToggleSelect,
  selectable = true,
}: PhotoGridProps) {
  if (photos.length === 0) {
    return (
      <div className="text-center py-12 text-gray-500">
        No photos found
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
      {photos.map((photo) => (
        <PhotoCard
          key={photo.id}
          photo={photo}
          selected={selectedIds.has(photo.id)}
          onToggle={() => onToggleSelect(photo.id)}
          selectable={selectable}
        />
      ))}
    </div>
  );
}
