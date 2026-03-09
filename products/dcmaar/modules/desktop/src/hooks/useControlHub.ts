import { useQuery } from '@tanstack/react-query';
import { controlHubClient } from '../services/mockClient';

export const CONTROL_HUB_QUERY_KEY = ['control-hub', 'defaults'] as const;

export const useControlHubDefaults = () =>
  useQuery({
    queryKey: CONTROL_HUB_QUERY_KEY,
    queryFn: controlHubClient.fetchDefaults,
    staleTime: 30_000,
  });
