import { useQuery } from '@tanstack/react-query';

export const useMetrics = () => {
  return useQuery({
    queryKey: ['metrics'],
    queryFn: async () => {
      try {
        const response = await (window as any).browser?.runtime?.sendMessage({
          type: 'GET_METRICS',
        });
        return response?.metrics || {};
      } catch (error) {
        console.warn('Failed to fetch metrics:', error);
        return {};
      }
    },
    refetchInterval: 5000, // Refresh every 5 seconds
    staleTime: 0,
    retry: 1,
  });
};
