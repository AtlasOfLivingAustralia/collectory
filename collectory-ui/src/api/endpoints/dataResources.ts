import apiClient from '../client';
import type { DataResource, ContactFor } from '../types';

export async function getDataResources(): Promise<DataResource[]> {
  const { data } = await apiClient.get<DataResource[]>('/dataResource');
  return data;
}

export async function getDataResource(uid: string): Promise<DataResource> {
  const { data } = await apiClient.get<DataResource>(`/dataResource/${uid}`);
  return data;
}

export async function getDataResourceContacts(uid: string): Promise<ContactFor[]> {
  const { data } = await apiClient.get<ContactFor[]>(`/dataResource/${uid}/contacts`);
  return data;
}

export const dataResourcesApi = {
  list: getDataResources,
  get: getDataResource,
  contacts: getDataResourceContacts,
  create: (data: Partial<DataResource>) => apiClient.post<DataResource>('/dataResource', data).then((r) => r.data),
  update: (uid: string, data: Partial<DataResource>) => apiClient.put<DataResource>(`/dataResource/${uid}`, data).then((r) => r.data),
  delete: (uid: string) => apiClient.delete(`/dataResource/${uid}`),
  count: () => apiClient.get<number>('/dataResource/count').then((r) => r.data),
  connectionParameters: (uid: string) => apiClient.get(`/dataResource/${uid}/connectionParameters`).then((r) => r.data),
};
