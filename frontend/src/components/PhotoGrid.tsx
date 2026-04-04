import { useState } from 'react';
import type { PhotoResponse } from '../types/api';
import PhotoCard from './PhotoCard';
import PhotoLightbox from './PhotoLightbox';

interface PhotoGridProps {
  photos: PhotoResponse[];
  selectedIds: Set<string>;
  onToggleSelect: (id: string) => void;
  selectable?: boolean;
  adminMode?: boolean;
  onDelete?: (id: string) => void;
}

export default function PhotoGrid({
  photos,
  selectedIds,
  onToggleSelect,
  selectable = true,
  adminMode,
  onDelete,
}: PhotoGridProps) {
  const [lightboxUrl, setLightboxUrl] = useState<string | null>(null);

  if (photos.length === 0) {
    return (
      <div className="text-center py-12 text-gray-500">
        No photos found
      </div>
    );
  }

  return (
    <>
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
        {photos.map((photo) => (
          <PhotoCard
            key={photo.id}
            photo={photo}
            selected={selectedIds.has(photo.id)}
            onToggle={() => onToggleSelect(photo.id)}
            onView={() => setLightboxUrl(photo.thumbnailUrl ?? null)}
            selectable={selectable}
            adminMode={adminMode}
            onDelete={() => onDelete?.(photo.id)}
          />
        ))}
      </div>
      {lightboxUrl && (
        <PhotoLightbox imageUrl={lightboxUrl} onClose={() => setLightboxUrl(null)} />
      )}
    </>
  );
}
