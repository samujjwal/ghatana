/**
 * Hook for backend-computed dashboard summary facts.
 *
 * @doc.type hook
 * @doc.purpose Query canonical dashboard summary API
 * @doc.layer frontend
 */

import { useQuery } from '@tanstack/react-query';
import { getDashboardSummary } from '@/api/dashboard';
import type { DashboardSummary } from '@/types/dashboard';

export function useDashboardSummary(workspaceId: string | null): {
  summary: DashboardSummary | null;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
} {
  const { data, isLoading, isError, error } = useQuery<DashboardSummary, Error>({
    queryKey: ['dashboard-summary', workspaceId],
    queryFn: () => getDashboardSummary(workspaceId!),
    enabled: workspaceId !== null,
    staleTime: 30_000,
  });

  return {
    summary: data ?? null,
    isLoading,
    isError,
    error: error ?? null,
  };
}
