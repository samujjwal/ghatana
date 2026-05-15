/**
 * ApprovalProvider - interface for managing human approval workflows.
 *
 * @doc.type interface
 * @doc.purpose Approval provider interface for approval workflows
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import type { KernelProvider } from "./KernelProvider.js";

/**
 * Approval request.
 */
export interface ApprovalRequest {
  readonly approvalId: string;
  readonly productUnitId: string;
  readonly runId?: string;
  readonly correlationId?: string;
  readonly requestedBy: string;
  readonly requestedAt?: string;
  readonly reason: string;
  readonly environment?: string;
  readonly action?: string;
  readonly riskLevel?: "low" | "medium" | "high" | "critical";
  readonly evidenceRefs?: readonly string[];
  readonly requiredApprovers: readonly string[];
  readonly expiresAt: string;
}

/**
 * Approval decision.
 */
export interface ApprovalDecision {
  readonly approvalId: string;
  readonly approved: boolean;
  readonly approvedBy: string;
  readonly reason: string;
  readonly decidedAt: string;
  readonly evidenceRefs?: readonly string[];
}

/**
 * Approval provider for managing human approval workflows.
 */
export interface ApprovalProvider extends KernelProvider {
  /**
   * Requests approval.
   */
  requestApproval(request: ApprovalRequest): Promise<void>;

  /**
   * Gets approval status.
   */
  getApprovalStatus(approvalId: string): Promise<{
    status: string;
    decision: ApprovalDecision | null;
  }>;

  /**
   * Records an approval decision.
   */
  recordDecision(decision: ApprovalDecision): Promise<void>;

  /**
   * Lists pending approvals.
   */
  listPendingApprovals(): Promise<readonly ApprovalRequest[]>;
}
