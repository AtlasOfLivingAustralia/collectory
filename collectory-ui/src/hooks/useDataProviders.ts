import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getDataProviders, getDataProvider, getDataProviderContacts, dataProvidersApi } from '../api/endpoints/dataProviders';
import type { DataProvider } from '../api/types';

export function useDataProviders() {
  return useQuery({
    queryKey: ['dataProviders'],
    queryFn: getDataProviders,
  });
}

export function useDataProvider(uid: string) {
  return useQuery({
    queryKey: ['dataProvider', uid],
    queryFn: () => getDataProvider(uid),
    enabled: !!uid,
  });
}

export function useDataProviderContacts(uid: string) {
  return useQuery({
    queryKey: ['dataProvider', uid, 'contacts'],
    queryFn: () => getDataProviderContacts(uid),
    enabled: !!uid,
  });
}

export function useCreateDataProvider() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<DataProvider>) => dataProvidersApi.create(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dataProviders'] }),
  });
}

export function useUpdateDataProvider() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ uid, data }: { uid: string; data: Partial<DataProvider> }) =>
      dataProvidersApi.update(uid, data),
    onSuccess: (_, { uid }) => {
      queryClient.invalidateQueries({ queryKey: ['dataProviders'] });
      queryClient.invalidateQueries({ queryKey: ['dataProvider', uid] });
    },
  });
}
