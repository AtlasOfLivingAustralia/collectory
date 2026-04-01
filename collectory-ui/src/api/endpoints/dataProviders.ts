import apiClient from '../client';
import type { DataProvider, ContactFor } from '../types';

export async function getDataProviders(): Promise<DataProvider[]> {
  const { data } = await apiClient.get<DataProvider[]>('/dataProvider');
  return data;
}

export async function getDataProvider(uid: string): Promise<DataProvider> {
  const { data } = await apiClient.get<DataProvider>(`/dataProvider/${uid}`);
  return data;
}

export async function getDataProviderContacts(uid: string): Promise<ContactFor[]> {
  const { data } = await apiClient.get<ContactFor[]>(`/dataProvider/${uid}/contacts`);
  return data;
}

export const dataProvidersApi = {
  list: getDataProviders,
  get: getDataProvider,
  contacts: getDataProviderContacts,
  create: (data: Partial<DataProvider>) => apiClient.post<DataProvider>('/dataProvider', data).then((r) => r.data),
  update: (uid: string, data: Partial<DataProvider>) => apiClient.put<DataProvider>(`/dataProvider/${uid}`, data).then((r) => r.data),
  delete: (uid: string) => apiClient.delete(`/dataProvider/${uid}`),
  count: () => apiClient.get<number>('/dataProvider/count').then((r) => r.data),
};
