/**
 * Hook for fetching and managing budget recommendations.
 *
 * <p>P1-032: Mutations invalidate approval queue and AI action log queries
 * to ensure related state updates without manual refresh.</p>
 *
 * @doc.type hook
 * @doc.purpose Fetch and mutate budget recommendations with cache invalidation
 * @doc.layer frontend
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  generateBudgetRecommendation,
  getLatestBudgetRecommendation,
  submitBudgetForApproval,
  approveBudgetRecommendation,
} from '@/api/budget';
import { useIdempotencyKeys } from '@/hooks/useIdempotencyKeys';
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

export interface UseBudgetMutationOptions {
  onError?: (error: Error) => void;
}

export function useGenerateBudget(
  workspaceId: string | null,
  options: UseBudgetMutationOptions = {}
): {
  generate: (body: GenerateBudgetRequest) => Promise<BudgetRecommendation>;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
} {
  const queryClient = useQueryClient();
  const { getIdempotencyKey, clearIdempotencyKey } = useIdempotencyKeys('budget:generate');

  const mutation = useMutation<BudgetRecommendation, Error, { body: GenerateBudgetRequest; intentParts: readonly unknown[] }>({
    mutationFn: ({ body, intentParts }) => {
      const idempotencyKey = getIdempotencyKey(intentParts);
      return generateBudgetRecommendation(workspaceId!, body, idempotencyKey);
    },
    onSuccess: (_recommendation, variables) => {
      clearIdempotencyKey(variables.intentParts);
      // P1-032: Invalidate budget and AI action log queries
      queryClient.invalidateQueries({ queryKey: ['budget', 'latest', workspaceId] });
      queryClient.invalidateQueries({ queryKey: ['ai-actions', workspaceId] });
    },
    onError: (error) => {
      options.onError?.(error);
    },
  });

  return {
    generate: (body) => {
      const intentParts = [workspaceId, body];
      return mutation.mutateAsync({ body, intentParts });
    },
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export function useSubmitBudgetApproval(
  workspaceId: string | null,
  options: UseBudgetMutationOptions = {}
): {
  submit: (recId: string) => Promise<BudgetRecommendation>;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
} {
  const queryClient = useQueryClient();
  const { getIdempotencyKey, clearIdempotencyKey } = useIdempotencyKeys('budget:submit-approval');

  const mutation = useMutation<BudgetRecommendation, Error, { recId: string; intentParts: readonly unknown[] }>({
    mutationFn: ({ recId, intentParts }) => {
      const idempotencyKey = getIdempotencyKey(intentParts);
      return submitBudgetForApproval(workspaceId!, recId, idempotencyKey);
    },
    onSuccess: (_recommendation, variables) => {
      clearIdempotencyKey(variables.intentParts);
      // P1-032: Invalidate budget and approval queue queries
      queryClient.invalidateQueries({ queryKey: ['budget', 'latest', workspaceId] });
      queryClient.invalidateQueries({ queryKey: ['approvals', 'pending', workspaceId] });
    },
    onError: (error) => {
      options.onError?.(error);
    },
  });

  return {
    submit: (recId) => {
      const intentParts = [workspaceId, recId];
      return mutation.mutateAsync({ recId, intentParts });
    },
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}

export function useApproveBudget(
  workspaceId: string | null,
  options: UseBudgetMutationOptions = {}
): {
  approve: (recId: string, auditComment?: string) => Promise<BudgetRecommendation>;
  isPending: boolean;
  isError: boolean;
  error: Error | null;
} {
  const queryClient = useQueryClient();
  const { getIdempotencyKey, clearIdempotencyKey } = useIdempotencyKeys('budget:approve');

  const mutation = useMutation<BudgetRecommendation, Error, { recId: string; auditComment?: string; intentParts: readonly unknown[] }>({
    mutationFn: ({ recId, auditComment, intentParts }) => {
      const idempotencyKey = getIdempotencyKey(intentParts);
      return approveBudgetRecommendation(workspaceId!, recId, idempotencyKey, auditComment);
    },
    onSuccess: (_recommendation, variables) => {
      clearIdempotencyKey(variables.intentParts);
      queryClient.invalidateQueries({ queryKey: ['budget', 'latest', workspaceId] });
      queryClient.invalidateQueries({ queryKey: ['approvals', 'pending', workspaceId] });
    },
    onError: (error) => {
      options.onError?.(error);
    },
  });

  return {
    approve: (recId, auditComment) => {
      const intentParts = [workspaceId, recId, auditComment ?? ''];
      return mutation.mutateAsync({ recId, auditComment, intentParts });
    },
    isPending: mutation.isPending,
    isError: mutation.isError,
    error: mutation.error ?? null,
  };
}
