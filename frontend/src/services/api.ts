import axios from 'axios';
import type {
  EventResponse,
  CreateEventRequest,
  PhotoResponse,
  PhotoUploadResponse,
  OrderResponse,
  OrderItemResponse,
  OrderSummaryResponse,
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
  const formData = new FormData();
  formData.append('files', file);

  const response = await api.post<PhotoUploadResponse[]>(
    `/events/${slug}/photos`,
    formData,
  );
  return response.data[0];
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

export async function listPhotos(slug: string): Promise<PhotoResponse[]> {
  const response = await api.get<PhotoResponse[]>(`/events/${slug}/photos`);
  return response.data;
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

export async function getDownloads(orderId: string): Promise<OrderItemResponse[]> {
  const response = await api.get<OrderItemResponse[]>(`/orders/${orderId}/downloads`);
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
