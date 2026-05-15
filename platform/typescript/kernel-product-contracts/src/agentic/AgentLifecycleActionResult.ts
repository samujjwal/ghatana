/**
 * Agent lifecycle action result contract.
 *
 * @doc.type module
 * @doc.purpose Governed result contract for agent lifecycle action evaluation and execution
 * @doc.layer kernel-product-contracts
 * @doc.pattern Contract
 */

import { z } from "zod";
import { AgentLifecycleActionRequestSchema } from "./AgentLifecycleActionRequest.js";

export type AgentLifecycleDecision = "allowed" | "denied" | "requires-approval";
export type AgentLifecycleApprovalDecision = "approved" | "rejected" | "pending" | "not-required";
export type AgentLifecycleHealthStatus = "healthy" | "degraded" | "unhealthy" | "unknown";
export type AgentLifecycleRollbackReadiness = "ready" | "not-ready" | "not-required";
export type AgentLifecycleRequiredNextAction =
  | "request-approval"
  | "run-verification"
  | "prepare-rollback"
  | "inspect-failure"
  | "none";

export interface AgentLifecycleActionFailure {
  readonly reasonCode: string;
  readonly message: string;
  readonly evidenceRefs?: readonly string[] | undefined;
}

export interface AgentLifecycleActionResult {
  readonly schemaVersion: "1.0.0";
  readonly resultId: string;
  readonly requestId: string;
  readonly correlationId: string;
  readonly productUnitId: string;
  readonly policyDecision: AgentLifecycleDecision;
  readonly masteryDecision: AgentLifecycleDecision;
  readonly approvalDecision: AgentLifecycleApprovalDecision;
  readonly lifecycleRunRef: string;
  readonly evidenceRefs: readonly string[];
  readonly healthStatus: AgentLifecycleHealthStatus;
  readonly rollbackReadiness: AgentLifecycleRollbackReadiness;
  readonly evaluatedAt: string;
  readonly failure?: AgentLifecycleActionFailure | undefined;
  readonly requiredNextAction?: AgentLifecycleRequiredNextAction | undefined;
  readonly request?: unknown | undefined;
  readonly policyEvidenceRefs?: readonly string[] | undefined;
  readonly masteryStateRef?: string | undefined;
  readonly toolPermissionRefs?: readonly string[] | undefined;
  readonly approvalTicketRefs?: readonly string[] | undefined;
  readonly verificationEvidenceRefs?: readonly string[] | undefined;
  readonly privacyClassification?: "public" | "internal" | "confidential" | "restricted" | undefined;
  readonly retention?: {
    readonly expiresAt: string;
  } | undefined;
  readonly modelDecisionContextRef?: string | undefined;
  readonly redactionRequired?: boolean | undefined;
}

const DECISIONS = ["allowed", "denied", "requires-approval"] as const;
const APPROVAL_DECISIONS = ["approved", "rejected", "pending", "not-required"] as const;
const HEALTH_STATUSES = ["healthy", "degraded", "unhealthy", "unknown"] as const;
const ROLLBACK_READINESS = ["ready", "not-ready", "not-required"] as const;
const REQUIRED_NEXT_ACTIONS = [
  "request-approval",
  "run-verification",
  "prepare-rollback",
  "inspect-failure",
  "none",
] as const;
const PRIVACY_CLASSIFICATIONS = ["public", "internal", "confidential", "restricted"] as const;

export const AgentLifecycleActionFailureSchema = z
  .object({
    reasonCode: z.string().trim().min(1),
    message: z.string().trim().min(1),
    evidenceRefs: z.array(z.string().trim().min(1)).optional(),
  })
  .strict();

export const AgentLifecycleActionResultSchema = z
  .object({
    schemaVersion: z.literal("1.0.0"),
    resultId: z.string().trim().min(1),
    requestId: z.string().trim().min(1),
    correlationId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1),
    policyDecision: z.enum(DECISIONS),
    masteryDecision: z.enum(DECISIONS),
    approvalDecision: z.enum(APPROVAL_DECISIONS),
    lifecycleRunRef: z.string().trim().min(1),
    evidenceRefs: z.array(z.string().trim().min(1)).min(1),
    healthStatus: z.enum(HEALTH_STATUSES),
    rollbackReadiness: z.enum(ROLLBACK_READINESS),
    evaluatedAt: z.string().datetime({ offset: true }),
    failure: AgentLifecycleActionFailureSchema.optional(),
    requiredNextAction: z.enum(REQUIRED_NEXT_ACTIONS).optional(),
    request: AgentLifecycleActionRequestSchema.optional(),
    policyEvidenceRefs: z.array(z.string().trim().min(1)).optional(),
    masteryStateRef: z.string().trim().min(1).optional(),
    toolPermissionRefs: z.array(z.string().trim().min(1)).optional(),
    approvalTicketRefs: z.array(z.string().trim().min(1)).optional(),
    verificationEvidenceRefs: z.array(z.string().trim().min(1)).optional(),
    privacyClassification: z.enum(PRIVACY_CLASSIFICATIONS).optional(),
    retention: z.object({
      expiresAt: z.string().trim().min(1),
    }).optional(),
    modelDecisionContextRef: z.string().trim().min(1).optional(),
    redactionRequired: z.boolean().optional(),
  })
  .strict();

export function isAgentLifecycleActionResult(
  value: unknown
): value is AgentLifecycleActionResult {
  return AgentLifecycleActionResultSchema.safeParse(value).success;
}
