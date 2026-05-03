/**
 * Hook for fetching an approval request and its snapshot.
 *
 * @doc.type hook
 * @doc.purpose Fetch approval status and content snapshot for detail view
 * @doc.layer frontend
 */

import { useQuery } from '@tanstack/react-query';
import { getApprovalSnapshot, getApprovalStatus } from '@/api/approvals';
import type { ApprovalRecordResponse, ApprovalSnapshot } from '@/types/approval';

export function useApprovalDetail(
  workspaceId: string | null,
  requestId: string | null,
): {
  request: ApprovalRecordResponse | null;
  snapshot: ApprovalSnapshot | null;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
} {
  const enabled = workspaceId !== null && requestId !== null;

  const statusQuery = useQuery<ApprovalRecordResponse, Error>({
    queryKey: ['approvals', 'status', workspaceId, requestId],
    queryFn: () => getApprovalStatus(workspaceId!, requestId!),
    enabled,
    staleTime: 15_000,
  });

  const snapshotQuery = useQuery<ApprovalSnapshot, Error>({
    queryKey: ['approvals', 'snapshot', workspaceId, requestId],
    queryFn: () => getApprovalSnapshot(workspaceId!, requestId!),
    enabled,
    staleTime: 60_000,
  });

  const isLoading = statusQuery.isLoading || snapshotQuery.isLoading;
  const isError = statusQuery.isError || snapshotQuery.isError;
  const error = statusQuery.error ?? snapshotQuery.error ?? null;

  return {
    request: statusQuery.data ?? null,
    snapshot: snapshotQuery.data ?? null,
    isLoading,
    isError,
    error,
  };
}
