import axios from 'axios';
import type {
  EventResponse,
  CreateEventRequest,
  PhotoResponse,
  PhotoUploadResponse,
} from '../types/api';

const api = axios.create({
  baseURL: '/api',
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

export async function uploadPhotos(
  slug: string,
  files: File[],
  photographerEmail: string,
  photographerName: string,
): Promise<PhotoUploadResponse[]> {
  const formData = new FormData();
  files.forEach((file) => formData.append('files', file));
  formData.append('photographerEmail', photographerEmail);
  formData.append('photographerName', photographerName);

  const response = await api.post<PhotoUploadResponse[]>(
    `/events/${slug}/photos`,
    formData,
  );
  return response.data;
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
