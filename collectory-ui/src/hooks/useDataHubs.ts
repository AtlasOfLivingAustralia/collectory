import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getDataHubs, getDataHub, getDataHubContacts, dataHubsApi } from '../api/endpoints/dataHubs';
import type { DataHub } from '../api/types';

export function useDataHubs() {
  return useQuery({
    queryKey: ['dataHubs'],
    queryFn: getDataHubs,
  });
}

export function useDataHub(uid: string) {
  return useQuery({
    queryKey: ['dataHub', uid],
    queryFn: () => getDataHub(uid),
    enabled: !!uid,
  });
}

export function useDataHubContacts(uid: string) {
  return useQuery({
    queryKey: ['dataHub', uid, 'contacts'],
    queryFn: () => getDataHubContacts(uid),
    enabled: !!uid,
  });
}

export function useCreateDataHub() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<DataHub>) => dataHubsApi.create(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dataHubs'] }),
  });
}

export function useUpdateDataHub() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ uid, data }: { uid: string; data: Partial<DataHub> }) =>
      dataHubsApi.update(uid, data),
    onSuccess: (_, { uid }) => {
      queryClient.invalidateQueries({ queryKey: ['dataHubs'] });
      queryClient.invalidateQueries({ queryKey: ['dataHub', uid] });
    },
  });
}
