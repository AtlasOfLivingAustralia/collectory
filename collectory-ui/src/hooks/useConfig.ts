import { useQuery } from '@tanstack/react-query';
import { getConfig, type AppConfig } from '../api/endpoints/config';

export function useConfig() {
  return useQuery<AppConfig>({
    queryKey: ['appConfig'],
    queryFn: getConfig,
    staleTime: 10 * 60 * 1000, // 10 minutes — config rarely changes
  });
}
