import axios from 'axios';
import type {
  EventResponse,
  CreateEventRequest,
  PhotoResponse,
  PhotoUploadResponse,
  OrderResponse,
  OrderItemResponse,
  OrderSummaryResponse,
  EventPassResponse,
  SearchStatsResponse,
  PlatformUsageResponse,
} from '../types/api';

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
});

export async function listEvents(includeHidden = false): Promise<EventResponse[]> {
  const response = await api.get<EventResponse[]>('/events', {
    params: includeHidden ? { includeHidden: true } : undefined,
  });
  return response.data;
}

export async function toggleEventHidden(slug: string): Promise<EventResponse> {
  const response = await api.post<EventResponse>(`/events/${slug}/toggle-hidden`);
  return response.data;
}

export async function toggleEventComingSoon(slug: string): Promise<EventResponse> {
  const response = await api.post<EventResponse>(`/events/${slug}/toggle-coming-soon`);
  return response.data;
}

export async function getEvent(slug: string): Promise<EventResponse> {
  const response = await api.get<EventResponse>(`/events/${slug}`);
  return response.data;
}

export async function createEvent(data: CreateEventRequest): Promise<EventResponse> {
  const response = await api.post<EventResponse>('/events', data);
  return response.data;
}

export type UploadErrorKind =
  | 'duplicate'
  | 'auth'
  | 'network'
  | 'server'
  | 'rejected'
  | 'aborted'
  | 'unknown';

export class UploadError extends Error {
  readonly kind: UploadErrorKind;
  readonly retryable: boolean;

  constructor(kind: UploadErrorKind, message: string, retryable: boolean) {
    super(message);
    this.name = 'UploadError';
    this.kind = kind;
    this.retryable = retryable;
  }
}

function asUploadError(error: unknown): UploadError {
  if (error instanceof UploadError) return error;

  if (axios.isCancel(error) || (error instanceof Error && error.name === 'AbortError')) {
    return new UploadError('aborted', 'Cancelled', false);
  }

  if (!axios.isAxiosError(error)) {
    const message = error instanceof Error ? error.message : 'Unexpected error';
    return new UploadError('unknown', message, false);
  }

  if (!error.response) {
    return new UploadError('network', 'Network error — the connection dropped', true);
  }

  const { status } = error.response;
  const message = (error.response.data as { message?: string } | undefined)?.message;

  if (status === 401 || status === 403) {
    return new UploadError('auth', 'Your admin session expired', false);
  }
  if (status === 400 && message?.includes('already exists')) {
    return new UploadError('duplicate', 'Already uploaded to this event', false);
  }
  if (status === 429 || status >= 500) {
    return new UploadError('server', message ?? `Server error (${status})`, true);
  }
  return new UploadError('rejected', message ?? `Rejected by the server (${status})`, false);
}

async function uploadPhotoOnce(
  slug: string,
  file: File,
  signal?: AbortSignal,
): Promise<PhotoUploadResponse> {
  // Step 1: Get presigned URL from backend
  const presignResponse = await api.post<{
    uploadUrl: string;
    s3Key: string;
    photoId: string;
  }>(`/events/${slug}/photos/presign`, null, {
    params: { filename: file.name },
    signal,
  });

  const { uploadUrl, s3Key, photoId } = presignResponse.data;

  // Step 2: Upload directly to S3
  let s3Response: Response;
  try {
    s3Response = await fetch(uploadUrl, {
      method: 'PUT',
      body: file,
      headers: { 'Content-Type': 'image/jpeg' },
      signal,
    });
  } catch (error) {
    throw asUploadError(error);
  }

  if (!s3Response.ok) {
    // A 403 here is almost always an expired signature, which a fresh presign fixes.
    const retryable = s3Response.status === 403 || s3Response.status === 429 || s3Response.status >= 500;
    throw new UploadError('server', `Storage rejected the file (${s3Response.status})`, retryable);
  }

  // Step 3: Confirm upload with backend
  const confirmResponse = await api.post<PhotoUploadResponse>(
    `/events/${slug}/photos/confirm`,
    null,
    { params: { photoId, s3Key, filename: file.name }, signal },
  );

  return confirmResponse.data;
}

const UPLOAD_ATTEMPTS = 3;

export async function uploadPhoto(
  slug: string,
  file: File,
  signal?: AbortSignal,
): Promise<PhotoUploadResponse> {
  for (let attempt = 1; ; attempt++) {
    try {
      return await uploadPhotoOnce(slug, file, signal);
    } catch (error) {
      const failure = asUploadError(error);
      if (!failure.retryable || attempt >= UPLOAD_ATTEMPTS || signal?.aborted) throw failure;
      const backoff = 400 * 2 ** (attempt - 1) + Math.random() * 200;
      await new Promise((resolve) => setTimeout(resolve, backoff));
    }
  }
}

export async function uploadCoverImage(slug: string, file: File): Promise<EventResponse> {
  const formData = new FormData();
  formData.append('cover', file);
  const response = await api.post<EventResponse>(`/events/${slug}/cover`, formData);
  return response.data;
}

export async function deleteEvent(slug: string): Promise<void> {
  await api.delete(`/events/${slug}`);
}

export async function listPhotos(slug: string, includeOriginal = false): Promise<PhotoResponse[]> {
  const response = await api.get<PhotoResponse[]>(`/events/${slug}/photos`, {
    params: includeOriginal ? { includeOriginal: true } : undefined,
  });
  return response.data;
}

export async function deletePhoto(slug: string, photoId: string): Promise<void> {
  await api.delete(`/events/${slug}/photos/${photoId}`);
}

export async function searchByFace(
  slug: string,
  selfie: File,
): Promise<PhotoResponse[]> {
  const formData = new FormData();
  formData.append('selfie', selfie);

  const response = await api.post<PhotoResponse[]>(
    `/events/${slug}/search`,
    formData,
  );
  return response.data;
}


export async function listOrders(): Promise<OrderSummaryResponse[]> {
  const response = await api.get<OrderSummaryResponse[]>('/orders');
  return response.data;
}

export async function createOrder(email: string, photoIds: string[]): Promise<OrderResponse> {
  const response = await api.post<OrderResponse>('/orders', { email, photoIds });
  return response.data;
}

export async function getOrder(orderId: string): Promise<OrderResponse> {
  const response = await api.get<OrderResponse>(`/orders/${orderId}`);
  return response.data;
}

export async function capturePayment(orderId: string): Promise<OrderResponse> {
  const response = await api.post<OrderResponse>(`/orders/${orderId}/capture`);
  return response.data;
}

export async function getPayPalClientId(): Promise<string> {
  const response = await api.get<{ clientId: string }>('/orders/paypal-client-id');
  return response.data.clientId;
}

export async function getDownloads(orderId: string): Promise<OrderItemResponse[]> {
  const response = await api.get<OrderItemResponse[]>(`/orders/${orderId}/downloads`);
  return response.data;
}

export async function getPassPrice(slug: string): Promise<number> {
  const response = await api.get<{ price: number }>(`/events/${slug}/passes/price`);
  return response.data.price;
}

export async function createPass(slug: string, email: string): Promise<EventPassResponse> {
  const response = await api.post<EventPassResponse>(`/events/${slug}/passes`, { email });
  return response.data;
}

export async function capturePassPayment(slug: string, passId: string): Promise<EventPassResponse> {
  const response = await api.post<EventPassResponse>(`/events/${slug}/passes/${passId}/capture`);
  return response.data;
}

export async function redeemPass(slug: string, email: string, selfie: File): Promise<string[]> {
  const formData = new FormData();
  formData.append('email', email);
  formData.append('selfie', selfie);
  const response = await api.post<{ downloadUrls: string[] }>(`/events/${slug}/passes/redeem`, formData);
  return response.data.downloadUrls;
}

export async function listPasses(): Promise<EventPassResponse[]> {
  const response = await api.get<EventPassResponse[]>('/passes');
  return response.data;
}

export async function getSearchStats(): Promise<SearchStatsResponse[]> {
  const response = await api.get<SearchStatsResponse[]>('/admin/stats/searches');
  return response.data;
}

export async function getPlatformUsage(): Promise<PlatformUsageResponse> {
  const response = await api.get<PlatformUsageResponse>('/admin/stats/usage');
  return response.data;
}

export async function checkAuth(): Promise<{ authenticated: boolean; username?: string }> {
  const response = await api.get<{ authenticated: boolean; username?: string }>('/auth/check');
  return response.data;
}

export async function login(
  username: string,
  password: string,
): Promise<{ authenticated: boolean; username?: string }> {
  const response = await api.post<{ authenticated: boolean; username?: string }>('/auth/login', {
    username,
    password,
  });
  return response.data;
}

export async function logout(): Promise<void> {
  await api.post('/auth/logout');
}
