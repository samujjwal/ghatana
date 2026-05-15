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
  readonly request?: unknown;
}

const DECISIONS = ["allowed", "denied", "requires-approval"] as const;
const APPROVAL_DECISIONS = ["approved", "rejected", "pending", "not-required"] as const;
const HEALTH_STATUSES = ["healthy", "degraded", "unhealthy", "unknown"] as const;
const ROLLBACK_READINESS = ["ready", "not-ready", "not-required"] as const;

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
    request: AgentLifecycleActionRequestSchema.optional(),
  })
  .strict();

export function isAgentLifecycleActionResult(
  value: unknown
): value is AgentLifecycleActionResult {
  return AgentLifecycleActionResultSchema.safeParse(value).success;
}
