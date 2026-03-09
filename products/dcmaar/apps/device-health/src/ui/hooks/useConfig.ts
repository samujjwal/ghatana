import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

export const useConfig = () => {
  return useQuery({
    queryKey: ['config'],
    queryFn: async () => {
      try {
        const result = await (window as any).browser?.storage?.local?.get('dcmaar_config');
        return (
          result?.dcmaar_config || {
            serverUrl: 'ws://localhost:9774',
            connectionStatus: 'disconnected',
            autoConnect: false,
          }
        );
      } catch (error) {
        console.warn('Failed to load config from storage, using defaults:', error);
        return {
          serverUrl: 'ws://localhost:9774',
          connectionStatus: 'disconnected',
          autoConnect: false,
        };
      }
    },
    staleTime: 1000 * 60 * 5, // 5 minutes
    retry: 1,
  });
};

export const useUpdateConfig = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (newConfig: any) => {
      try {
        await (window as any).browser?.storage?.local?.set({ dcmaar_config: newConfig });
        return newConfig;
      } catch (error) {
        console.warn('Failed to save config to storage:', error);
        return newConfig;
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['config'] });
    },
  });
};
