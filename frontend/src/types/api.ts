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
  originalUrl: string | null;
  uploadedAt: string;
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
}

export interface OrderResponse {
  id: string;
  buyerEmail: string;
  status: string;
  totalAmount: number;
  currency: string;
  createdAt: string;
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

export interface ErrorResponse {
  status: number;
  message: string;
  fieldErrors?: Record<string, string>;
  timestamp: string;
}
