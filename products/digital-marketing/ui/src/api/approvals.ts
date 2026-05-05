/**
 * Approval workflow API client.
 *
 * @doc.type api-client
 * @doc.purpose Typed wrappers for DMOS approval HTTP endpoints
 * @doc.layer frontend
 */

import { apiRequest } from '@/lib/http-client';
import type {
  ApprovalRecordResponse,
  ApprovalSnapshot,
  DecideApprovalRequest,
  PendingApprovalsResponse,
  SubmitApprovalRequest,
} from '@/types/approval';

function base(workspaceId: string): string {
  return `/v1/workspaces/${encodeURIComponent(workspaceId)}/approvals`;
}

export async function submitApproval(
  workspaceId: string,
  body: SubmitApprovalRequest,
  idempotencyKey?: string,
): Promise<ApprovalRecordResponse> {
  return apiRequest<ApprovalRecordResponse>(base(workspaceId), {
    method: 'POST',
    body,
    idempotencyKey,
  });
}

export async function getApprovalStatus(
  workspaceId: string,
  requestId: string,
): Promise<ApprovalRecordResponse> {
  return apiRequest<ApprovalRecordResponse>(
    `${base(workspaceId)}/${encodeURIComponent(requestId)}`,
  );
}

export async function getApprovalSnapshot(
  workspaceId: string,
  requestId: string,
): Promise<ApprovalSnapshot> {
  return apiRequest<ApprovalSnapshot>(
    `${base(workspaceId)}/${encodeURIComponent(requestId)}/snapshot`,
  );
}

export async function listPendingApprovals(
  workspaceId: string,
  subjectId: string,
): Promise<ApprovalRecordResponse[]> {
  const response = await apiRequest<PendingApprovalsResponse>(
    `${base(workspaceId)}/pending/${encodeURIComponent(subjectId)}`,
  );
  return response.items;
}

// P1-013: Workspace-scoped pending approvals - lists all pending approvals in a workspace
export async function listPendingApprovalsForWorkspace(
  workspaceId: string,
): Promise<ApprovalRecordResponse[]> {
  const response = await apiRequest<PendingApprovalsResponse>(
    `${base(workspaceId)}/pending`,
  );
  return response.items;
}

export async function decideApproval(
  workspaceId: string,
  requestId: string,
  body: DecideApprovalRequest,
  idempotencyKey?: string,
): Promise<ApprovalRecordResponse> {
  return apiRequest<ApprovalRecordResponse>(
    `${base(workspaceId)}/${encodeURIComponent(requestId)}/decide`,
    { method: 'POST', body, idempotencyKey },
  );
}
