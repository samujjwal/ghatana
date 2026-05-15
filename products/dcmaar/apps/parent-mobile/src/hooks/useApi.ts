import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services/api';
import type { Policy } from '@/types';

export const useDevices = () =>
  useQuery({
    queryKey: ['devices'],
    queryFn: api.getDevices,
    refetchInterval: 30000,
  });

export const useUsageData = (deviceId: string, days: number = 7) =>
  useQuery({
    queryKey: ['usage', deviceId, days],
    queryFn: () => api.getUsageData(deviceId, days),
    enabled: Boolean(deviceId),
  });

export const usePolicies = (deviceId?: string) =>
  useQuery({
    queryKey: deviceId ? ['policies', deviceId] : ['policies'],
    queryFn: () => api.getPolicies(deviceId),
  });

export const useCreatePolicy = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (policy: Omit<Policy, 'id'>) => api.createPolicy(policy),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['policies'] });
    },
  });
};

export const useUpdatePolicy = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, updates }: { id: string; updates: Partial<Policy> }) =>
      api.updatePolicy(id, updates),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['policies'] });
    },
  });
};

export const useDeletePolicy = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.deletePolicy(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['policies'] });
    },
  });
};

export const useAlerts = () =>
  useQuery({
    queryKey: ['alerts'],
    queryFn: api.getAlerts,
    refetchInterval: 60000,
  });

export const useMarkAlertRead = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.markAlertRead(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['alerts'] });
    },
  });
};
