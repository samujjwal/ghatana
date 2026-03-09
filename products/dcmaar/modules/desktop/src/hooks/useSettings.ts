import { useQuery } from '@tanstack/react-query';
import { settingsClient } from '../services/mockClient';

export const SETTINGS_QUERY_KEY = ['settings', 'snapshot'] as const;

export const useSettingsSnapshot = () =>
  useQuery({
    queryKey: SETTINGS_QUERY_KEY,
    queryFn: settingsClient.fetchSettings,
    staleTime: 60_000,
  });
