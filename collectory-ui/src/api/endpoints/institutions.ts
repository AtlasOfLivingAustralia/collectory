import apiClient from '../client';
import type { Institution, ContactFor } from '../types';

export async function getInstitutions(): Promise<Institution[]> {
  const { data } = await apiClient.get<Institution[]>('/institution');
  return data;
}

export async function getInstitution(uid: string): Promise<Institution> {
  const { data } = await apiClient.get<Institution>(`/institution/${uid}`);
  return data;
}

export async function getInstitutionContacts(uid: string): Promise<ContactFor[]> {
  const { data } = await apiClient.get<ContactFor[]>(`/institution/${uid}/contacts`);
  return data;
}

export const institutionsApi = {
  list: getInstitutions,
  get: getInstitution,
  contacts: getInstitutionContacts,
  create: (data: Partial<Institution>) => apiClient.post<Institution>('/institution', data).then((r) => r.data),
  update: (uid: string, data: Partial<Institution>) => apiClient.put<Institution>(`/institution/${uid}`, data).then((r) => r.data),
  delete: (uid: string) => apiClient.delete(`/institution/${uid}`),
  count: () => apiClient.get<number>('/institution/count').then((r) => r.data),
};
