/**
 * Approval workflow API client.
 *
 * @doc.type api-client
 * @doc.purpose Typed wrappers for DMOS approval HTTP endpoints
 * @doc.layer frontend
 */

import { apiRequest } from '@/lib/http-client';
import type {
  ApprovalRequest,
  ApprovalSnapshot,
  DecideApprovalRequest,
  SubmitApprovalRequest,
} from '@/types/approval';

function base(workspaceId: string): string {
  return `/v1/workspaces/${encodeURIComponent(workspaceId)}/approvals`;
}

export async function submitApproval(
  workspaceId: string,
  body: SubmitApprovalRequest,
): Promise<ApprovalRequest> {
  return apiRequest<ApprovalRequest>(base(workspaceId), {
    method: 'POST',
    body,
  });
}

export async function getApprovalStatus(
  workspaceId: string,
  requestId: string,
): Promise<ApprovalRequest> {
  return apiRequest<ApprovalRequest>(
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
): Promise<ApprovalRequest[]> {
  return apiRequest<ApprovalRequest[]>(
    `${base(workspaceId)}/pending/${encodeURIComponent(subjectId)}`,
  );
}

export async function decideApproval(
  workspaceId: string,
  requestId: string,
  body: DecideApprovalRequest,
): Promise<ApprovalRequest> {
  return apiRequest<ApprovalRequest>(
    `${base(workspaceId)}/${encodeURIComponent(requestId)}/decide`,
    { method: 'POST', body },
  );
}
