import api from './api';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

export interface AppConfig {
  theme: 'light' | 'dark' | 'system';
  refreshInterval: number;
  apiUrl: string;
  notificationsEnabled: boolean;
}

export const getConfig = async (): Promise<AppConfig> => {
  const response = await api.get('/config');
  return response.data;
};

export const updateConfig = async (config: Partial<AppConfig>): Promise<AppConfig> => {
  const response = await api.patch('/config', config);
  return response.data;
};

export const useConfig = () => {
  const queryClient = useQueryClient();
  
  const { data: config } = useQuery({
    queryKey: ['config'],
    queryFn: getConfig,
    initialData: {
      theme: 'dark',
      refreshInterval: 30,
      apiUrl: 'http://localhost:8787',
      notificationsEnabled: true,
    },
  });

  const mutation = useMutation({
    mutationFn: updateConfig,
    onSuccess: (newConfig) => {
      queryClient.setQueryData(['config'], newConfig);
    },
  });

  return {
    config,
    updateConfig: mutation.mutate,
    isLoading: mutation.isPending,
    error: mutation.error,
  };
};
