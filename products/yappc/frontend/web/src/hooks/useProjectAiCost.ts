/**
 * useProjectAiCost
 *
 * TanStack Query hook that returns aggregated AI agent run counts and estimated
 * cost for a given project. Data originates from the
 * `GET /api/projects/:projectId/ai-cost` REST endpoint.
 *
 * @doc.type hook
 * @doc.purpose Fetch per-project AI cost metrics for the cost tile
 * @doc.layer product
 * @doc.pattern Query Hook
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import {
  fetchProjectAiCost,
  type ProjectAiCostResponse,
} from '../services/ai/projectAiCostApi';

export interface UseProjectAiCostResult {
  data: ProjectAiCostResponse | undefined;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  refetch: UseQueryResult<ProjectAiCostResponse>['refetch'];
}

export function useProjectAiCost(projectId: string | undefined): UseProjectAiCostResult {
  const { data, isLoading, isError, error, refetch } = useQuery<
    ProjectAiCostResponse,
    Error
  >({
    queryKey: ['project', projectId, 'ai-cost'],
    queryFn: () => {
      if (!projectId) {
        return Promise.reject(new Error('projectId is required'));
      }
      return fetchProjectAiCost(projectId);
    },
    enabled: Boolean(projectId),
    staleTime: 60_000, // refresh at most once per minute
  });

  return { data, isLoading, isError, error: error ?? null, refetch };
}
