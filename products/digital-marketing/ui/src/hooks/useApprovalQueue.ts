/**
 * Hook for fetching the pending approval queue.
 *
 * P1-013: Updated to use workspace-scoped pending approvals endpoint.
 * Approver sees all pending approvals in their workspace, not subject-scoped.
 *
 * @doc.type hook
 * @doc.purpose Fetch and cache pending approvals for a workspace
 * @doc.layer frontend
 */

import { useQuery } from '@tanstack/react-query';
import { listPendingApprovalsForWorkspace } from '@/api/approvals';
import type { ApprovalRecordResponse } from '@/types/approval';

export function useApprovalQueue(
  workspaceId: string | null,
  subjectId: string | null,
): {
  approvals: ApprovalRecordResponse[];
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  refetch: () => void;
} {
  // P1-013: Use workspace-scoped pending approvals
  const { data, isLoading, isError, error, refetch } = useQuery<
    ApprovalRecordResponse[],
    Error
  >({
    queryKey: ['approvals', 'pending', workspaceId],
    queryFn: () => listPendingApprovalsForWorkspace(workspaceId!),
    enabled: workspaceId !== null,
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
