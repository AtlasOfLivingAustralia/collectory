import apiClient from '../client';
import type { Collection, ContactFor } from '../types';

export async function getCollections(): Promise<Collection[]> {
  const { data } = await apiClient.get<Collection[]>('/collection');
  return data;
}

export async function getCollection(uid: string): Promise<Collection> {
  const { data } = await apiClient.get<Collection>(`/collection/${uid}`);
  return data;
}

export async function getCollectionContacts(uid: string): Promise<ContactFor[]> {
  const { data } = await apiClient.get<ContactFor[]>(`/collection/${uid}/contacts`);
  return data;
}

export const collectionsApi = {
  list: getCollections,
  get: getCollection,
  contacts: getCollectionContacts,
  create: (data: Partial<Collection>) => apiClient.post<Collection>('/collection', data).then((r) => r.data),
  update: (uid: string, data: Partial<Collection>) => apiClient.put<Collection>(`/collection/${uid}`, data).then((r) => r.data),
  delete: (uid: string) => apiClient.delete(`/collection/${uid}`),
  count: () => apiClient.get<number>('/collection/count').then((r) => r.data),
};
