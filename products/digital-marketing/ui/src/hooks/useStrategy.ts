/**
 * Hook for fetching and managing marketing strategies.
 *
 * @doc.type hook
 * @doc.purpose Fetch and mutate strategies for a workspace
 * @doc.layer frontend
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  generateStrategy,
  getLatestStrategy,
  submitStrategyForApproval,
  approveStrategy,
} from '@/api/strategy';
import type { MarketingStrategy, GenerateStrategyRequest } from '@/types/strategy';

export function useStrategy(workspaceId: string | null): {
  strategy: MarketingStrategy | null;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  refetch: () => void;
} {
  const { data, isLoading, isError, error, refetch } = useQuery<MarketingStrategy, Error>({
    queryKey: ['strategy', 'latest', workspaceId],
    queryFn: () => getLatestStrategy(workspaceId!),
    enabled: workspaceId !== null,
    staleTime: 30_000,
  });

  return {
    strategy: data ?? null,
    isLoading,
    isError,
    error: error ?? null,
    refetch,
  };
}

export function useGenerateStrategy(workspaceId: string | null): {
  generate: (body: GenerateStrategyRequest) => Promise<MarketingStrategy>;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
} {
  const queryClient = useQueryClient();

  const mutation = useMutation<MarketingStrategy, Error, GenerateStrategyRequest>({
    mutationFn: (body) => generateStrategy(workspaceId!, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['strategy', 'latest', workspaceId] });
    },
  });

  return {
    generate: mutation.mutateAsync,
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export function useSubmitStrategyApproval(workspaceId: string | null): {
  submit: (strategyId: string) => Promise<MarketingStrategy>;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
} {
  const queryClient = useQueryClient();

  const mutation = useMutation<MarketingStrategy, Error, string>({
    mutationFn: (strategyId) => submitStrategyForApproval(workspaceId!, strategyId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['strategy', 'latest', workspaceId] });
    },
  });

  return {
    submit: mutation.mutateAsync,
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export function useApproveStrategy(workspaceId: string | null): {
  approve: (strategyId: string) => Promise<MarketingStrategy>;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
} {
  const queryClient = useQueryClient();

  const mutation = useMutation<MarketingStrategy, Error, string>({
    mutationFn: (strategyId) => approveStrategy(workspaceId!, strategyId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['strategy', 'latest', workspaceId] });
    },
  });

  return {
    approve: mutation.mutateAsync,
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}
