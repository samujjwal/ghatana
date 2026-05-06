/**
 * Shared TypeScript types for the DMOS approval workflow.
 *
 * @doc.type types
 * @doc.purpose Canonical type definitions for approval domain objects
 * @doc.layer frontend
 */

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export type ApprovalTargetType =
  | 'STRATEGY'
  | 'PROPOSAL'
  | 'SOW'
  | 'CONTENT_VERSION'
  | 'BUDGET'
  | 'CAMPAIGN_LAUNCH'
  | 'CONNECTOR_WRITE'
  | 'OVERRIDE';

export type ApprovalDecision = 'APPROVE' | 'REJECT';

/**
 * Wire shape returned by the backend for a single approval record.
 * Maps to {@code DmosApprovalDto} in DmosApprovalServlet.
 * Combines plugin ApprovalRecord with DMOS ApprovalSnapshot.
 */
export interface ApprovalRecordResponse {
  requestId: string;
  tenantId: string;
  workspaceId: string;
  action?: string | null;
  subjectId?: string | null;
  requestedBy?: string | null;
  requestedAt?: string | null;
  expiresAt?: string | null;
  reviewerId?: string | null;
  reviewerNotes?: string | null;
  targetType: ApprovalTargetType | null;
  targetId: string | null;
  description: string | null;
  riskLevel: number;
  requiredApproverRole: string;
  status: ApprovalStatus;
  submittedAt: string;
  submittedBy: string;
  decidedAt: string | null;
  decidedBy: string | null;
  comment: string | null;
  snapshotSummary: string | null;
  validationResultId: string | null;
  snapshotAt: string | null;
}

/**
 * Wrapper for the list-pending response: {@code { items: ApprovalRecordResponse[] }}.
 */
export interface PendingApprovalsResponse {
  items: ApprovalRecordResponse[];
}

/**
 * Wire shape returned by the backend for an approval snapshot.
 * Maps to {@code SnapshotResponse} in DmosApprovalServlet.
 */
export interface ApprovalSnapshot {
  requestId: string;
  targetType: ApprovalTargetType;
  targetId: string;
  targetWorkspaceId: string;
  snapshotSummary: string;
  validationResultId: string | null;
  riskLevel: number;
  requiredApproverRole: string;
  snapshotAt: string;
}

/**
 * Request body for submitting an entity for approval.
 */
export interface SubmitApprovalRequest {
  targetType: ApprovalTargetType;
  targetId: string;
  description: string;
  riskLevel?: number;
  requiredApproverRole?: string;
  validationResultId?: string;
}

export interface DecideApprovalRequest {
  decision: ApprovalDecision;
  notes?: string;
}
