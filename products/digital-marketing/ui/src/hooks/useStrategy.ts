/**
 * Hook for fetching and managing marketing strategies.
 *
 * <p>P1-032: Mutations invalidate approval queue and AI action log queries
 * to ensure related state updates without manual refresh.</p>
 *
 * @doc.type hook
 * @doc.purpose Fetch and mutate strategies with cache invalidation
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
    mutationFn: (body) => {
      // P1-022: Generate idempotency key at mutation start
      const idempotencyKey = crypto.randomUUID();
      return generateStrategy(workspaceId!, body, idempotencyKey);
    },
    onSuccess: () => {
      // P1-032: Invalidate strategy and AI action log queries
      queryClient.invalidateQueries({ queryKey: ['strategy', 'latest', workspaceId] });
      queryClient.invalidateQueries({ queryKey: ['ai-actions', workspaceId] });
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
    mutationFn: (strategyId) => {
      // P1-022: Generate idempotency key at mutation start
      const idempotencyKey = crypto.randomUUID();
      return submitStrategyForApproval(workspaceId!, strategyId, idempotencyKey);
    },
    onSuccess: () => {
      // P1-032: Invalidate strategy and approval queue queries
      queryClient.invalidateQueries({ queryKey: ['strategy', 'latest', workspaceId] });
      queryClient.invalidateQueries({ queryKey: ['approvals', 'pending', workspaceId] });
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
    mutationFn: (strategyId) => {
      // P1-022: Generate idempotency key at mutation start
      const idempotencyKey = crypto.randomUUID();
      return approveStrategy(workspaceId!, strategyId, idempotencyKey);
    },
    onSuccess: () => {
      // P1-032: Invalidate strategy and approval queue queries
      queryClient.invalidateQueries({ queryKey: ['strategy', 'latest', workspaceId] });
      queryClient.invalidateQueries({ queryKey: ['approvals', 'pending', workspaceId] });
    },
  });

  return {
    approve: mutation.mutateAsync,
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}
