import axios from 'axios';
import type {
  EventResponse,
  CreateEventRequest,
  PhotoResponse,
  PhotoUploadResponse,
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
  photographerEmail: string,
  photographerName: string,
): Promise<PhotoUploadResponse> {
  const formData = new FormData();
  formData.append('files', file);
  formData.append('photographerEmail', photographerEmail);
  formData.append('photographerName', photographerName);

  const response = await api.post<PhotoUploadResponse[]>(
    `/events/${slug}/photos`,
    formData,
  );
  return response.data[0];
}

export async function updateEvent(slug: string, data: CreateEventRequest): Promise<EventResponse> {
  const response = await api.put<EventResponse>(`/events/${slug}`, data);
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
