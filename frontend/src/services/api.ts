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
} from '../types/api';

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
});

export async function listEvents(): Promise<EventResponse[]> {
  const response = await api.get<EventResponse[]>('/events');
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

export async function uploadPhoto(
  slug: string,
  file: File,
): Promise<PhotoUploadResponse> {
  // Step 1: Get presigned URL from backend
  const presignResponse = await api.post<{
    uploadUrl: string;
    s3Key: string;
    photoId: string;
  }>(`/events/${slug}/photos/presign`);

  const { uploadUrl, s3Key, photoId } = presignResponse.data;

  // Step 2: Upload directly to S3
  await fetch(uploadUrl, {
    method: 'PUT',
    body: file,
    headers: { 'Content-Type': 'image/jpeg' },
  });

  // Step 3: Confirm upload with backend
  const confirmResponse = await api.post<PhotoUploadResponse>(
    `/events/${slug}/photos/confirm`,
    null,
    { params: { photoId, s3Key } },
  );

  return confirmResponse.data;
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

export async function checkAuth(): Promise<{ authenticated: boolean; username?: string }> {
  const response = await api.get<{ authenticated: boolean; username?: string }>('/auth/check');
  return response.data;
}

export function loginWithBasicAuth(): Promise<{ authenticated: boolean; username?: string }> {
  // XMLHttpRequest triggers the native browser credential dialog on 401
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('GET', '/api/auth/login', true);
    xhr.withCredentials = true;
    xhr.onload = () => {
      if (xhr.status === 200) {
        resolve(JSON.parse(xhr.responseText));
      } else {
        reject(new Error('Login failed'));
      }
    };
    xhr.onerror = () => reject(new Error('Login failed'));
    xhr.send();
  });
}
