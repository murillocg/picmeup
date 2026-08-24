export interface EventResponse {
  id: string;
  name: string;
  date: string;
  location: string;
  slug: string;
  createdAt: string;
  expiresAt: string;
  coverImageUrl: string | null;
  hidden: boolean;
  photoPrice: number;
  packPrice: number;
  free: boolean;
  comingSoon: boolean;
}

export interface CreateEventRequest {
  name: string;
  date: string;
  location: string;
  photoPrice: number;
  packPrice: number;
  free: boolean;
  comingSoon: boolean;
}

export interface PhotoResponse {
  id: string;
  status: string;
  thumbnailUrl: string | null;
  originalUrl: string | null;
  uploadedAt: string;
  filename: string | null;
}

export interface PhotoUploadResponse {
  id: string;
  status: string;
}

export interface OrderItemResponse {
  id: string;
  photoId: string;
  price: number;
  downloadUrl: string | null;
  filename: string | null;
}

export interface OrderResponse {
  id: string;
  buyerEmail: string;
  status: string;
  totalAmount: number;
  currency: string;
  createdAt: string;
  paypalOrderId: string | null;
  items: OrderItemResponse[];
}

export interface OrderSummaryResponse {
  id: string;
  buyerEmail: string;
  status: string;
  totalAmount: number;
  currency: string;
  createdAt: string;
}

export interface EventPassResponse {
  id: string;
  eventId: string;
  buyerEmail: string;
  status: string;
  price: number;
  currency: string;
  createdAt: string;
  redeemedAt: string | null;
  paypalOrderId: string | null;
}

export interface SearchStatsResponse {
  eventId: string;
  eventName: string;
  eventSlug: string | null;
  totalSearches: number;
  searchesWithResults: number;
}

export interface PlatformUsageResponse {
  totalEvents: number;
  totalPhotos: number;
  storage: {
    totalBytes: number;
    originalsBytes: number;
    thumbnailsBytes: number;
  };
  facialRecognition: {
    facesIndexedThisMonth: number;
    searchesThisMonth: number;
    facesIndexedAllTime: number;
    searchesAllTime: number;
  };
}

export interface ErrorResponse {
  status: number;
  message: string;
  fieldErrors?: Record<string, string>;
  timestamp: string;
}
