import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getInstitutions, getInstitution, getInstitutionContacts, institutionsApi } from '../api/endpoints/institutions';
import type { Institution } from '../api/types';

export function useInstitutions() {
  return useQuery({
    queryKey: ['institutions'],
    queryFn: getInstitutions,
  });
}

export function useInstitution(uid: string) {
  return useQuery({
    queryKey: ['institution', uid],
    queryFn: () => getInstitution(uid),
    enabled: !!uid,
  });
}

export function useInstitutionContacts(uid: string) {
  return useQuery({
    queryKey: ['institution', uid, 'contacts'],
    queryFn: () => getInstitutionContacts(uid),
    enabled: !!uid,
  });
}

export function useCreateInstitution() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<Institution>) => institutionsApi.create(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['institutions'] }),
  });
}

export function useUpdateInstitution() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ uid, data }: { uid: string; data: Partial<Institution> }) =>
      institutionsApi.update(uid, data),
    onSuccess: (_, { uid }) => {
      queryClient.invalidateQueries({ queryKey: ['institutions'] });
      queryClient.invalidateQueries({ queryKey: ['institution', uid] });
    },
  });
}
