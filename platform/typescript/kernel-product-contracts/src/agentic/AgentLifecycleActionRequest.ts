/**
 * Agent lifecycle action request contract.
 *
 * @doc.type module
 * @doc.purpose Governed request contract for agent-proposed lifecycle actions
 * @doc.layer kernel-product-contracts
 * @doc.pattern Contract
 */

import { z } from "zod";
import type { ProductLifecyclePhase } from "../lifecycle/ProductLifecyclePhase";
import { ProductUnitScopeSchema, type ProductUnitScope } from "../product-unit/ProductUnit.js";

export type AgentLifecycleRequestedAction =
  | "create-lifecycle-plan"
  | "execute-lifecycle-phase"
  | "request-approval"
  | "verify-lifecycle-health"
  | "prepare-rollback";

export type AgentLifecycleRiskLevel = "low" | "medium" | "high" | "critical";

export interface AgentLifecycleApprovalRequirement {
  readonly approvalId: string;
  readonly approverRole: string;
  readonly required: boolean;
}

export interface AgentLifecycleVerificationRequirement {
  readonly verificationId: string;
  readonly kind: "test" | "policy" | "health" | "artifact" | "deployment";
  readonly required: boolean;
}

export interface AgentLifecycleActionRequest {
  readonly schemaVersion: "1.0.0";
  readonly requestId: string;
  readonly correlationId: string;
  readonly productUnitId: string;
  readonly scope: ProductUnitScope;
  readonly requestedByAgent: string;
  readonly requestedAction: AgentLifecycleRequestedAction;
  readonly lifecyclePhase: ProductLifecyclePhase;
  readonly proposedPlanRef: string;
  readonly riskLevel: AgentLifecycleRiskLevel;
  readonly requiredApprovals: readonly AgentLifecycleApprovalRequirement[];
  readonly requiredVerification: readonly AgentLifecycleVerificationRequirement[];
  readonly evidenceRefs: readonly string[];
  readonly rollbackPlanRef: string;
  readonly policyEvidenceRefs?: readonly string[];
  readonly masteryStateRef?: string;
  readonly toolPermissionRefs?: readonly string[];
  readonly approvalTicketRefs?: readonly string[];
  readonly verificationEvidenceRefs?: readonly string[];
  readonly privacyClassification?: "public" | "internal" | "confidential" | "restricted";
  readonly retention?: {
    readonly expiresAt: string;
  };
  readonly modelDecisionContextRef?: string;
  readonly redactionRequired?: boolean;
}

export type AgentLifecycleActionRequestReasonCode =
  | "raw-command-not-allowed"
  | "missing-evidence"
  | "missing-rollback-plan"
  | "invalid-scope"
  | "unsupported-action"
  | "schema-invalid";

export interface AgentLifecycleActionRequestValidationIssue {
  readonly path: string;
  readonly reasonCode: AgentLifecycleActionRequestReasonCode;
  readonly message: string;
}

export class AgentLifecycleActionRequestValidationError extends Error {
  readonly issues: readonly AgentLifecycleActionRequestValidationIssue[];

  constructor(issues: readonly AgentLifecycleActionRequestValidationIssue[]) {
    super(
      issues.length > 0
        ? `Invalid AgentLifecycleActionRequest: ${issues.map((issue) => issue.message).join("; ")}`
        : "Invalid AgentLifecycleActionRequest"
    );
    this.name = "AgentLifecycleActionRequestValidationError";
    this.issues = issues;
  }
}

const REQUESTED_ACTIONS = [
  "create-lifecycle-plan",
  "execute-lifecycle-phase",
  "request-approval",
  "verify-lifecycle-health",
  "prepare-rollback",
] as const satisfies readonly AgentLifecycleRequestedAction[];

const LIFECYCLE_PHASES = [
  "create",
  "bootstrap",
  "dev",
  "validate",
  "test",
  "build",
  "package",
  "release",
  "deploy",
  "verify",
  "promote",
  "rollback",
  "operate",
  "retire",
] as const satisfies readonly ProductLifecyclePhase[];

const RISK_LEVELS = ["low", "medium", "high", "critical"] as const;
const VERIFICATION_KINDS = ["test", "policy", "health", "artifact", "deployment"] as const;
const PRIVACY_CLASSIFICATIONS = ["public", "internal", "confidential", "restricted"] as const;
const RAW_COMMAND_VALUE_PATTERN = /\b(gradle|gradlew|pnpm|npm|yarn|docker|docker\s+buildx|kubectl)\b/i;

function containsRawCommand(value: unknown): boolean {
  if (typeof value === "string") {
    return RAW_COMMAND_VALUE_PATTERN.test(value);
  }
  if (Array.isArray(value)) {
    return value.some((item: unknown) => containsRawCommand(item));
  }
  if (typeof value !== "object" || value === null) {
    return false;
  }
  return Object.values(value as Record<string, unknown>).some((nested) =>
    containsRawCommand(nested)
  );
}

export const AgentLifecycleApprovalRequirementSchema = z
  .object({
    approvalId: z.string().trim().min(1),
    approverRole: z.string().trim().min(1),
    required: z.boolean(),
  })
  .strict();

export const AgentLifecycleVerificationRequirementSchema = z
  .object({
    verificationId: z.string().trim().min(1),
    kind: z.enum(VERIFICATION_KINDS),
    required: z.boolean(),
  })
  .strict();

export const AgentLifecycleActionRequestSchema = z
  .object({
    schemaVersion: z.literal("1.0.0"),
    requestId: z.string().trim().min(1),
    correlationId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1),
    scope: ProductUnitScopeSchema,
    requestedByAgent: z.string().trim().min(1),
    requestedAction: z.enum(REQUESTED_ACTIONS),
    lifecyclePhase: z.enum(LIFECYCLE_PHASES),
    proposedPlanRef: z.string().trim().min(1),
    riskLevel: z.enum(RISK_LEVELS),
    requiredApprovals: z.array(AgentLifecycleApprovalRequirementSchema),
    requiredVerification: z.array(AgentLifecycleVerificationRequirementSchema),
    evidenceRefs: z.array(z.string().trim().min(1)).min(1),
    rollbackPlanRef: z.string().trim().min(1),
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
  .strict()
  .superRefine((value: AgentLifecycleActionRequest, context: z.RefinementCtx) => {
    if (containsRawCommand(value)) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["proposedPlanRef"],
        message: "AgentLifecycleActionRequest must not contain raw shell/tool commands",
      });
    }
    // Validation: high/critical risk requires approval
    if ((value.riskLevel === "high" || value.riskLevel === "critical") &&
        value.requiredApprovals.every((approval: { required: boolean }) => !approval.required)) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["requiredApprovals"],
        message: "High or critical risk actions require at least one required approval",
      });
    }
    // Validation: execute-lifecycle-phase and prepare-rollback require rollbackPlanRef
    if ((value.requestedAction === "execute-lifecycle-phase" ||
         value.requestedAction === "prepare-rollback") &&
        value.rollbackPlanRef === "") {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["rollbackPlanRef"],
        message: "Execute-lifecycle-phase and prepare-rollback actions require a rollback plan reference",
      });
    }
    // Validation: restricted classification requires redaction flag
    if (value.privacyClassification === "restricted" && !value.redactionRequired) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["redactionRequired"],
        message: "Restricted classification requires redactionRequired flag to be true",
      });
    }
  });

function reasonCodeForAgentLifecycleActionRequestIssue(
  issue: z.ZodIssue
): AgentLifecycleActionRequestReasonCode {
  const path = issue.path.join(".");
  if (issue.message.includes("raw shell/tool commands")) {
    return "raw-command-not-allowed";
  }
  if (path === "evidenceRefs") {
    return "missing-evidence";
  }
  if (path === "rollbackPlanRef") {
    return "missing-rollback-plan";
  }
  if (path.startsWith("scope")) {
    return "invalid-scope";
  }
  if (path === "requestedAction") {
    return "unsupported-action";
  }
  return "schema-invalid";
}

export function parseAgentLifecycleActionRequest(
  value: unknown
): AgentLifecycleActionRequest {
  const parsed = AgentLifecycleActionRequestSchema.safeParse(value);
  if (parsed.success) {
    return parsed.data;
  }

  throw new AgentLifecycleActionRequestValidationError(
    parsed.error.issues.map((issue) => ({
      path: issue.path.join("."),
      reasonCode: reasonCodeForAgentLifecycleActionRequestIssue(issue),
      message: issue.message,
    }))
  );
}

export function isAgentLifecycleActionRequest(
  value: unknown
): value is AgentLifecycleActionRequest {
  return AgentLifecycleActionRequestSchema.safeParse(value).success;
}
