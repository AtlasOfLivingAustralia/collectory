import apiClient from '../client';
import type { DataHub, ContactFor } from '../types';

export async function getDataHubs(): Promise<DataHub[]> {
  const { data } = await apiClient.get<DataHub[]>('/dataHub');
  return data;
}

export async function getDataHub(uid: string): Promise<DataHub> {
  const { data } = await apiClient.get<DataHub>(`/dataHub/${uid}`);
  return data;
}

export async function getDataHubContacts(uid: string): Promise<ContactFor[]> {
  const { data } = await apiClient.get<ContactFor[]>(`/dataHub/${uid}/contacts`);
  return data;
}

export const dataHubsApi = {
  list: getDataHubs,
  get: getDataHub,
  contacts: getDataHubContacts,
  create: (data: Partial<DataHub>) => apiClient.post<DataHub>('/dataHub', data).then((r) => r.data),
  update: (uid: string, data: Partial<DataHub>) => apiClient.put<DataHub>(`/dataHub/${uid}`, data).then((r) => r.data),
  delete: (uid: string) => apiClient.delete(`/dataHub/${uid}`),
  count: () => apiClient.get<number>('/dataHub/count').then((r) => r.data),
};
