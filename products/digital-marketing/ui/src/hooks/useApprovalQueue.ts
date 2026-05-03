/**
 * Hook for fetching the pending approval queue.
 *
 * @doc.type hook
 * @doc.purpose Fetch and cache pending approvals for a subject
 * @doc.layer frontend
 */

import { useQuery } from '@tanstack/react-query';
import { listPendingApprovals } from '@/api/approvals';
import type { ApprovalRequest } from '@/types/approval';

export function useApprovalQueue(
  workspaceId: string | null,
  subjectId: string | null,
): {
  approvals: ApprovalRequest[];
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  refetch: () => void;
} {
  const { data, isLoading, isError, error, refetch } = useQuery<
    ApprovalRequest[],
    Error
  >({
    queryKey: ['approvals', 'pending', workspaceId, subjectId],
    queryFn: () => listPendingApprovals(workspaceId!, subjectId!),
    enabled: workspaceId !== null && subjectId !== null,
    staleTime: 30_000,
  });

  return {
    approvals: data ?? [],
    isLoading,
    isError,
    error: error ?? null,
    refetch,
  };
}
