/**
 * Hook for fetching and managing campaigns.
 *
 * @doc.type hook
 * @doc.purpose Fetch and mutate campaigns for a workspace with pagination and per-row states
 * @doc.layer frontend
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, useCallback } from 'react';
import { listCampaigns, createCampaign, launchCampaign, pauseCampaign, completeCampaign, archiveCampaign, rollbackCampaign, duplicateCampaign } from '@/api/campaigns';
import type { Campaign, CreateCampaignRequest, CampaignListResponse } from '@/types/campaign';
import type { ApiError } from '@/lib/http-client';

export interface UseCampaignsOptions {
  limit?: number;
  offset?: number;
}

export function useCampaigns(
  workspaceId: string | null,
  options: UseCampaignsOptions = {}
): {
  campaigns: Campaign[];
  count: number;
  offset: number;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  refetch: () => void;
} {
  const { limit = 20, offset = 0 } = options;

  const { data, isLoading, isError, error, refetch } = useQuery<CampaignListResponse, Error>({
    queryKey: ['campaigns', workspaceId, limit, offset],
    queryFn: () => listCampaigns(workspaceId!, limit, offset),
    enabled: workspaceId !== null,
    staleTime: 30_000,
  });

  return {
    campaigns: data?.items ?? [],
    count: data?.count ?? 0,
    offset: data?.offset ?? 0,
    isLoading,
    isError,
    error: error ?? null,
    refetch,
  };
}

export interface UseCreateCampaignReturn {
  create: (body: CreateCampaignRequest) => Promise<Campaign>;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
}

export function useCreateCampaign(
  workspaceId: string | null,
  onError?: (error: ApiError) => void
): UseCreateCampaignReturn {
  const queryClient = useQueryClient();

  const mutation = useMutation<Campaign, ApiError, CreateCampaignRequest>({
    mutationFn: (body) => {
      const idempotencyKey = crypto.randomUUID();
      return createCampaign(workspaceId!, body, idempotencyKey);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['campaigns', workspaceId] });
    },
    onError: (error) => {
      onError?.(error);
    },
  });

  return {
    create: mutation.mutateAsync,
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export interface UseLaunchCampaignReturn {
  launch: (campaignId: string) => Promise<Campaign>;
  isPendingFor: (campaignId: string) => boolean;
  isError: boolean;
  error: Error | null;
}

export function useLaunchCampaign(
  workspaceId: string | null,
  onError?: (error: ApiError, campaignId: string) => void
): UseLaunchCampaignReturn {
  const queryClient = useQueryClient();
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set());

  const mutation = useMutation<Campaign, ApiError, string>({
    mutationFn: async (campaignId) => {
      setPendingIds((prev) => new Set(prev).add(campaignId));
      try {
        const idempotencyKey = crypto.randomUUID();
        const result = await launchCampaign(workspaceId!, campaignId, idempotencyKey);
        return result;
      } finally {
        setPendingIds((prev) => {
          const next = new Set(prev);
          next.delete(campaignId);
          return next;
        });
      }
    },
    onSuccess: () => {
      // Keep audit timeline in sync after launch commands.
      queryClient.invalidateQueries({ queryKey: ['campaigns', workspaceId] });
      queryClient.invalidateQueries({ queryKey: ['ai-actions', workspaceId] });
    },
    onError: (error, campaignId) => {
      onError?.(error, campaignId);
    },
  });

  return {
    launch: mutation.mutateAsync,
    // Row-specific pending state allows concurrent mutations on different rows.
    isPendingFor: useCallback((campaignId: string) => pendingIds.has(campaignId), [pendingIds]),
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export interface UsePauseCampaignReturn {
  pause: (campaignId: string) => Promise<Campaign>;
  isPendingFor: (campaignId: string) => boolean;
  isError: boolean;
  error: Error | null;
}

export function usePauseCampaign(
  workspaceId: string | null,
  onError?: (error: ApiError, campaignId: string) => void
): UsePauseCampaignReturn {
  const queryClient = useQueryClient();
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set());

  const mutation = useMutation<Campaign, ApiError, string>({
    mutationFn: async (campaignId) => {
      setPendingIds((prev) => new Set(prev).add(campaignId));
      try {
        const idempotencyKey = crypto.randomUUID();
        const result = await pauseCampaign(workspaceId!, campaignId, idempotencyKey);
        return result;
      } finally {
        setPendingIds((prev) => {
          const next = new Set(prev);
          next.delete(campaignId);
          return next;
        });
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['campaigns', workspaceId] });
    },
    onError: (error, campaignId) => {
      onError?.(error, campaignId);
    },
  });

  return {
    pause: mutation.mutateAsync,
    // Row-specific pending state allows concurrent mutations on different rows.
    isPendingFor: useCallback((campaignId: string) => pendingIds.has(campaignId), [pendingIds]),
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export interface UseCampaignActionReturn {
  execute: (campaignId: string) => Promise<Campaign>;
  isPendingFor: (campaignId: string) => boolean;
  isError: boolean;
  error: Error | null;
}

export function useCompleteCampaign(
  workspaceId: string | null,
  onError?: (error: ApiError, campaignId: string) => void,
): UseCampaignActionReturn {
  const queryClient = useQueryClient();
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set());

  const mutation = useMutation<Campaign, ApiError, string>({
    mutationFn: async (campaignId) => {
      setPendingIds((prev) => new Set(prev).add(campaignId));
      try {
        return await completeCampaign(workspaceId!, campaignId, crypto.randomUUID());
      } finally {
        setPendingIds((prev) => { const n = new Set(prev); n.delete(campaignId); return n; });
      }
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['campaigns', workspaceId] }); },
    onError: (error, campaignId) => { onError?.(error, campaignId); },
  });

  return {
    execute: mutation.mutateAsync,
    isPendingFor: useCallback((id: string) => pendingIds.has(id), [pendingIds]),
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export function useArchiveCampaign(
  workspaceId: string | null,
  onError?: (error: ApiError, campaignId: string) => void,
): UseCampaignActionReturn {
  const queryClient = useQueryClient();
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set());

  const mutation = useMutation<Campaign, ApiError, string>({
    mutationFn: async (campaignId) => {
      setPendingIds((prev) => new Set(prev).add(campaignId));
      try {
        return await archiveCampaign(workspaceId!, campaignId, crypto.randomUUID());
      } finally {
        setPendingIds((prev) => { const n = new Set(prev); n.delete(campaignId); return n; });
      }
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['campaigns', workspaceId] }); },
    onError: (error, campaignId) => { onError?.(error, campaignId); },
  });

  return {
    execute: mutation.mutateAsync,
    isPendingFor: useCallback((id: string) => pendingIds.has(id), [pendingIds]),
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export function useRollbackCampaign(
  workspaceId: string | null,
  onError?: (error: ApiError, campaignId: string) => void,
): UseCampaignActionReturn {
  const queryClient = useQueryClient();
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set());

  const mutation = useMutation<Campaign, ApiError, string>({
    mutationFn: async (campaignId) => {
      setPendingIds((prev) => new Set(prev).add(campaignId));
      try {
        return await rollbackCampaign(workspaceId!, campaignId, crypto.randomUUID());
      } finally {
        setPendingIds((prev) => { const n = new Set(prev); n.delete(campaignId); return n; });
      }
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['campaigns', workspaceId] }); },
    onError: (error, campaignId) => { onError?.(error, campaignId); },
  });

  return {
    execute: mutation.mutateAsync,
    isPendingFor: useCallback((id: string) => pendingIds.has(id), [pendingIds]),
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export interface UseDuplicateCampaignReturn {
  execute: (campaignId: string, newName: string) => Promise<Campaign>;
  isPendingFor: (campaignId: string) => boolean;
  isError: boolean;
  error: Error | null;
}

export function useDuplicateCampaign(
  workspaceId: string | null,
  onError?: (error: ApiError, campaignId: string) => void,
): UseDuplicateCampaignReturn {
  const queryClient = useQueryClient();
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set());

  const mutation = useMutation<Campaign, ApiError, { campaignId: string; newName: string }>({
    mutationFn: async ({ campaignId, newName }) => {
      setPendingIds((prev) => new Set(prev).add(campaignId));
      try {
        return await duplicateCampaign(workspaceId!, campaignId, newName, crypto.randomUUID());
      } finally {
        setPendingIds((prev) => { const n = new Set(prev); n.delete(campaignId); return n; });
      }
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['campaigns', workspaceId] }); },
    onError: (error, { campaignId }) => { onError?.(error, campaignId); },
  });

  return {
    execute: (campaignId: string, newName: string) => mutation.mutateAsync({ campaignId, newName }),
    isPendingFor: useCallback((id: string) => pendingIds.has(id), [pendingIds]),
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}
