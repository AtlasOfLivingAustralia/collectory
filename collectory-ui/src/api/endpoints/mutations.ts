import apiClient from '../client';

export async function createEntity(entityType: string, data: Record<string, unknown>) {
  const res = await apiClient.post(`/${entityType}`, data);
  return res.data;
}

export async function updateEntity(entityType: string, uid: string, data: Record<string, unknown>) {
  const res = await apiClient.put(`/${entityType}/${uid}`, data);
  return res.data;
}

export async function deleteEntity(entityType: string, uid: string) {
  const res = await apiClient.delete(`/${entityType}/${uid}`);
  return res.data;
}
