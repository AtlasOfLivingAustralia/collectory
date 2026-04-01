import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getDataResources, getDataResource, getDataResourceContacts, dataResourcesApi } from '../api/endpoints/dataResources';
import type { DataResource } from '../api/types';

export function useDataResources() {
  return useQuery({
    queryKey: ['dataResources'],
    queryFn: getDataResources,
  });
}

export function useDataResource(uid: string) {
  return useQuery({
    queryKey: ['dataResource', uid],
    queryFn: () => getDataResource(uid),
    enabled: !!uid,
  });
}

export function useDataResourceContacts(uid: string) {
  return useQuery({
    queryKey: ['dataResource', uid, 'contacts'],
    queryFn: () => getDataResourceContacts(uid),
    enabled: !!uid,
  });
}

export function useCreateDataResource() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<DataResource>) => dataResourcesApi.create(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dataResources'] }),
  });
}

export function useUpdateDataResource() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ uid, data }: { uid: string; data: Partial<DataResource> }) =>
      dataResourcesApi.update(uid, data),
    onSuccess: (_, { uid }) => {
      queryClient.invalidateQueries({ queryKey: ['dataResources'] });
      queryClient.invalidateQueries({ queryKey: ['dataResource', uid] });
    },
  });
}
