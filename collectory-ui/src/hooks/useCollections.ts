import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getCollections, getCollection, getCollectionContacts, collectionsApi } from '../api/endpoints/collections';
import type { Collection } from '../api/types';

export function useCollections() {
  return useQuery({
    queryKey: ['collections'],
    queryFn: getCollections,
  });
}

export function useCollection(uid: string) {
  return useQuery({
    queryKey: ['collection', uid],
    queryFn: () => getCollection(uid),
    enabled: !!uid,
  });
}

export function useCollectionContacts(uid: string) {
  return useQuery({
    queryKey: ['collection', uid, 'contacts'],
    queryFn: () => getCollectionContacts(uid),
    enabled: !!uid,
  });
}

export function useCreateCollection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<Collection>) => collectionsApi.create(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['collections'] }),
  });
}

export function useUpdateCollection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ uid, data }: { uid: string; data: Partial<Collection> }) =>
      collectionsApi.update(uid, data),
    onSuccess: (_, { uid }) => {
      queryClient.invalidateQueries({ queryKey: ['collections'] });
      queryClient.invalidateQueries({ queryKey: ['collection', uid] });
    },
  });
}

export function useDeleteCollection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (uid: string) => collectionsApi.delete(uid),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['collections'] }),
  });
}
