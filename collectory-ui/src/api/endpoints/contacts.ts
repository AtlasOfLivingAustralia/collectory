import apiClient from '../client';
import type { Contact, ContactFor } from '../types';

export async function getContacts(): Promise<Contact[]> {
  const { data } = await apiClient.get<Contact[]>('/contacts');
  return data;
}

export async function getContact(id: number): Promise<Contact> {
  const { data } = await apiClient.get<Contact>(`/contacts/${id}`);
  return data;
}

export async function getEntityContacts(entityType: string, uid: string): Promise<ContactFor[]> {
  const { data } = await apiClient.get<ContactFor[]>(`/${entityType}/${uid}/contacts`);
  return data;
}

export const contactsApi = {
  list: getContacts,
  get: getContact,
  create: (data: Partial<Contact>) => apiClient.post<Contact>('/contacts', data).then((r) => r.data),
  update: (id: number, data: Partial<Contact>) => apiClient.put<Contact>(`/contacts/${id}`, data).then((r) => r.data),
  delete: (id: number) => apiClient.delete(`/contacts/${id}`),
  getByEmail: (email: string) => apiClient.get<Contact>(`/contacts/email/${email}`).then((r) => r.data),

  // Entity contacts
  forEntity: getEntityContacts,
  addToEntity: (entityType: string, uid: string, data: Partial<ContactFor>) =>
    apiClient.post<ContactFor>(`/${entityType}/${uid}/contacts`, data).then((r) => r.data),
  updateForEntity: (entityType: string, uid: string, contactId: number, data: Partial<ContactFor>) =>
    apiClient.put<ContactFor>(`/${entityType}/${uid}/contacts/${contactId}`, data).then((r) => r.data),
  removeFromEntity: (entityType: string, uid: string, contactId: number) =>
    apiClient.delete(`/${entityType}/${uid}/contacts/${contactId}`),
};
