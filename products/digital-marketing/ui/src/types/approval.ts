/**
 * Shared TypeScript types for the DMOS approval workflow.
 *
 * @doc.type types
 * @doc.purpose Canonical type definitions for approval domain objects
 * @doc.layer frontend
 */

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';

export type ApprovalTargetType =
  | 'STRATEGY'
  | 'CAMPAIGN'
  | 'CONTENT'
  | 'AUDIENCE_SEGMENT'
  | 'BUDGET_PLAN'
  | 'ANALYTICS_REPORT'
  | 'INTEGRATION_CONFIG';

export type ApprovalDecision = 'APPROVE' | 'REJECT';

export interface ApprovalRequest {
  requestId: string;
  workspaceId: string;
  tenantId: string;
  targetType: ApprovalTargetType;
  targetId: string;
  description: string;
  riskLevel: number;
  status: ApprovalStatus;
  requiredApproverRole: string;
  submittedAt: string;
  decidedAt: string | null;
  decidedBy: string | null;
  comment: string | null;
}

export interface ApprovalSnapshotField {
  key: string;
  value: unknown;
}

export interface ApprovalSnapshot {
  requestId: string;
  capturedAt: string;
  fields: ApprovalSnapshotField[];
}

export interface SubmitApprovalRequest {
  targetType: ApprovalTargetType;
  targetId: string;
  description: string;
  riskLevel?: number;
  requiredApproverRole?: string;
}

export interface DecideApprovalRequest {
  decision: ApprovalDecision;
  comment?: string;
}
