/**
 * Hook for fetching and managing budget recommendations.
 *
 * @doc.type hook
 * @doc.purpose Fetch and mutate budget recommendations for a workspace
 * @doc.layer frontend
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  generateBudgetRecommendation,
  getLatestBudgetRecommendation,
  submitBudgetForApproval,
  approveBudgetRecommendation,
} from '@/api/budget';
import type { BudgetRecommendation, GenerateBudgetRequest } from '@/types/budget';

export function useBudgetRecommendation(workspaceId: string | null): {
  recommendation: BudgetRecommendation | null;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  refetch: () => void;
} {
  const { data, isLoading, isError, error, refetch } = useQuery<BudgetRecommendation, Error>({
    queryKey: ['budget', 'latest', workspaceId],
    queryFn: () => getLatestBudgetRecommendation(workspaceId!),
    enabled: workspaceId !== null,
    staleTime: 30_000,
  });

  return {
    recommendation: data ?? null,
    isLoading,
    isError,
    error: error ?? null,
    refetch,
  };
}

export function useGenerateBudget(workspaceId: string | null): {
  generate: (body: GenerateBudgetRequest) => Promise<BudgetRecommendation>;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
} {
  const queryClient = useQueryClient();

  const mutation = useMutation<BudgetRecommendation, Error, GenerateBudgetRequest>({
    mutationFn: (body) => generateBudgetRecommendation(workspaceId!, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['budget', 'latest', workspaceId] });
    },
  });

  return {
    generate: mutation.mutateAsync,
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export function useSubmitBudgetApproval(workspaceId: string | null): {
  submit: (recId: string) => Promise<BudgetRecommendation>;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
} {
  const queryClient = useQueryClient();

  const mutation = useMutation<BudgetRecommendation, Error, string>({
    mutationFn: (recId) => submitBudgetForApproval(workspaceId!, recId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['budget', 'latest', workspaceId] });
    },
  });

  return {
    submit: mutation.mutateAsync,
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export function useApproveBudget(workspaceId: string | null): {
  approve: (recId: string) => Promise<BudgetRecommendation>;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
} {
  const queryClient = useQueryClient();

  const mutation = useMutation<BudgetRecommendation, Error, string>({
    mutationFn: (recId) => approveBudgetRecommendation(workspaceId!, recId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['budget', 'latest', workspaceId] });
    },
  });

  return {
    approve: mutation.mutateAsync,
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}
