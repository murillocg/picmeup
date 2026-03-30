export interface EventResponse {
  id: string;
  name: string;
  date: string;
  location: string;
  slug: string;
  createdAt: string;
  expiresAt: string;
  coverImageUrl: string | null;
}

export interface CreateEventRequest {
  name: string;
  date: string;
  location: string;
}

export interface PhotoResponse {
  id: string;
  status: string;
  thumbnailUrl: string | null;
  uploadedAt: string;
}

export interface PhotoUploadResponse {
  id: string;
  status: string;
}

export interface ErrorResponse {
  status: number;
  message: string;
  fieldErrors?: Record<string, string>;
  timestamp: string;
}
