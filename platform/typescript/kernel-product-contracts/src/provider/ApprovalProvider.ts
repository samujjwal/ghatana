/**
 * ApprovalProvider - interface for managing human approval workflows.
 *
 * @doc.type interface
 * @doc.purpose Approval provider interface for approval workflows
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import { z } from "zod";
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

export const ApprovalRequestSchema = z
  .object({
    approvalId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1).optional(),
    correlationId: z.string().trim().min(1).optional(),
    requestedBy: z.string().trim().min(1),
    requestedAt: z.string().datetime({ offset: true }).optional(),
    reason: z.string().trim().min(1),
    environment: z.string().trim().min(1).optional(),
    action: z.string().trim().min(1).optional(),
    riskLevel: z.enum(["low", "medium", "high", "critical"]).optional(),
    evidenceRefs: z.array(z.string().trim().min(1)).optional(),
    requiredApprovers: z.array(z.string().trim().min(1)),
    expiresAt: z.string().datetime({ offset: true }),
  })
  .strict();

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

export const ApprovalDecisionSchema = z
  .object({
    approvalId: z.string().trim().min(1),
    approved: z.boolean(),
    approvedBy: z.string().trim().min(1),
    reason: z.string().trim().min(1),
    decidedAt: z.string().datetime({ offset: true }),
    evidenceRefs: z.array(z.string().trim().min(1)).optional(),
  })
  .strict();

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

export const ApprovalProviderSchema = z.custom<ApprovalProvider>(
  (value) => {
    if (typeof value !== "object" || value === null) {
      return false;
    }
    const provider = value as Record<string, unknown>;
    return (
      typeof provider.requestApproval === "function" &&
      typeof provider.getApprovalStatus === "function" &&
      typeof provider.recordDecision === "function" &&
      typeof provider.listPendingApprovals === "function"
    );
  },
  "ApprovalProvider requires approval workflow functions"
);

export function validateApprovalRequest(
  value: unknown
): value is ApprovalRequest {
  return ApprovalRequestSchema.safeParse(value).success;
}

export function validateApprovalDecision(
  value: unknown
): value is ApprovalDecision {
  return ApprovalDecisionSchema.safeParse(value).success;
}

export function validateApprovalProvider(
  value: unknown
): value is ApprovalProvider {
  return ApprovalProviderSchema.safeParse(value).success;
}
