/**
 * Hook for fetching and managing campaigns.
 *
 * @doc.type hook
 * @doc.purpose Fetch and mutate campaigns for a workspace
 * @doc.layer frontend
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { listCampaigns, createCampaign, launchCampaign, pauseCampaign } from '@/api/campaigns';
import type { Campaign, CreateCampaignRequest } from '@/types/campaign';

export function useCampaigns(workspaceId: string | null): {
  campaigns: Campaign[];
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  refetch: () => void;
} {
  const { data, isLoading, isError, error, refetch } = useQuery<Campaign[], Error>({
    queryKey: ['campaigns', workspaceId],
    queryFn: () => listCampaigns(workspaceId!),
    enabled: workspaceId !== null,
    staleTime: 30_000,
  });

  return {
    campaigns: data ?? [],
    isLoading,
    isError,
    error: error ?? null,
    refetch,
  };
}

export function useCreateCampaign(workspaceId: string | null): {
  create: (body: CreateCampaignRequest) => Promise<Campaign>;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
} {
  const queryClient = useQueryClient();

  const mutation = useMutation<Campaign, Error, CreateCampaignRequest>({
    mutationFn: (body) => createCampaign(workspaceId!, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['campaigns', workspaceId] });
    },
  });

  return {
    create: mutation.mutateAsync,
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export function useLaunchCampaign(workspaceId: string | null): {
  launch: (campaignId: string) => Promise<Campaign>;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
} {
  const queryClient = useQueryClient();

  const mutation = useMutation<Campaign, Error, string>({
    mutationFn: (campaignId) => launchCampaign(workspaceId!, campaignId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['campaigns', workspaceId] });
    },
  });

  return {
    launch: mutation.mutateAsync,
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export function usePauseCampaign(workspaceId: string | null): {
  pause: (campaignId: string) => Promise<Campaign>;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
} {
  const queryClient = useQueryClient();

  const mutation = useMutation<Campaign, Error, string>({
    mutationFn: (campaignId) => pauseCampaign(workspaceId!, campaignId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['campaigns', workspaceId] });
    },
  });

  return {
    pause: mutation.mutateAsync,
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}
