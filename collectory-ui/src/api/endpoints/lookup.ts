import apiClient from '../client';

export const lookupApi = {
  summary: (uid: string) => apiClient.get(`/lookup/summary/${uid}`).then((r) => r.data),
  collection: (instCode: string, collCode: string) =>
    apiClient.get(`/lookup/inst/${instCode}/coll/${collCode}`).then((r) => r.data),
  citations: (uids?: string) => apiClient.get('/citations', { params: { include: uids } }).then((r) => r.data),
  downloadLimits: () => apiClient.get('/downloadLimits').then((r) => r.data),
};
