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
import {
  ProductUnitScopeSchema,
  type ProductUnitScope,
} from "../product-unit/ProductUnit.js";

export type AgentLifecycleRequestedAction =
  | "create-lifecycle-plan"
  | "execute-lifecycle-phase"
  | "request-approval"
  | "verify-lifecycle-health"
  | "prepare-rollback";

export type AgentLifecycleRiskLevel = "low" | "medium" | "high" | "critical";
export type AgentLifecycleMasteryState =
  | "novice"
  | "learning"
  | "competent"
  | "mastered"
  | "maintenance-only";
export type AgentLifecyclePolicyDecision =
  | "allowed"
  | "denied"
  | "requires-approval";
export type AgentLifecycleFallbackMode =
  | "rollback"
  | "dry-run"
  | "manual-handoff"
  | "degraded-safe-mode";

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

export interface AgentLifecycleMasteryEvidence {
  readonly state: AgentLifecycleMasteryState;
  readonly stateRef: string;
  readonly evaluatedAt: string;
}

export interface AgentLifecyclePolicyDecisionEvidence {
  readonly decisionId: string;
  readonly decision: AgentLifecyclePolicyDecision;
  readonly evaluatedAt: string;
  readonly reasonCodes: readonly string[];
  readonly evidenceRefs: readonly string[];
}

export interface AgentLifecycleToolPermission {
  readonly toolId: string;
  readonly permissionRef: string;
  readonly granted: boolean;
  readonly allowedActions: readonly AgentLifecycleRequestedAction[];
}

export interface AgentLifecycleActionRequest {
  readonly schemaVersion: "1.0.0";
  readonly requestId: string;
  readonly correlationId: string;
  readonly productUnitId: string;
  readonly scope: ProductUnitScope;
  readonly requestedByAgent: string;
  readonly requestedByAgentVersion: string;
  readonly masteryState: AgentLifecycleMasteryEvidence;
  readonly policyDecision: AgentLifecyclePolicyDecisionEvidence;
  readonly toolPermissions: readonly AgentLifecycleToolPermission[];
  readonly requestedAction: AgentLifecycleRequestedAction;
  readonly lifecyclePhase: ProductLifecyclePhase;
  readonly proposedPlanRef: string;
  readonly riskLevel: AgentLifecycleRiskLevel;
  readonly approvalRequired: boolean;
  readonly requiredApprovals: readonly AgentLifecycleApprovalRequirement[];
  readonly requiredVerification: readonly AgentLifecycleVerificationRequirement[];
  readonly inputRefs: readonly string[];
  readonly outputRefs: readonly string[];
  readonly verificationProofRefs: readonly string[];
  readonly evidenceRefs: readonly string[];
  readonly rollbackPlanRef: string;
  readonly fallbackMode: AgentLifecycleFallbackMode;
  readonly policyEvidenceRefs?: readonly string[] | undefined;
  readonly masteryStateRef?: string | undefined;
  readonly toolPermissionRefs?: readonly string[] | undefined;
  readonly approvalTicketRefs?: readonly string[] | undefined;
  readonly verificationEvidenceRefs?: readonly string[] | undefined;
  readonly privacyClassification?:
    | "public"
    | "internal"
    | "confidential"
    | "restricted"
    | undefined;
  readonly retention?:
    | {
        readonly expiresAt: string;
      }
    | undefined;
  readonly modelDecisionContextRef?: string | undefined;
  readonly redactionRequired?: boolean | undefined;
}

export type AgentLifecycleActionRequestReasonCode =
  | "raw-command-not-allowed"
  | "missing-evidence"
  | "missing-tool-permission"
  | "tool-permission-denied"
  | "policy-denied"
  | "approval-required"
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
        : "Invalid AgentLifecycleActionRequest",
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

export const AgentLifecycleRequestedActionSchema = z.enum(REQUESTED_ACTIONS);

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
export const AgentLifecycleRiskLevelSchema = z.enum(RISK_LEVELS);
const MASTERY_STATES = [
  "novice",
  "learning",
  "competent",
  "mastered",
  "maintenance-only",
] as const;
export const AgentLifecycleMasteryStateSchema = z.enum(MASTERY_STATES);
const POLICY_DECISIONS = ["allowed", "denied", "requires-approval"] as const;
export const AgentLifecyclePolicyDecisionSchema = z.enum(POLICY_DECISIONS);
const FALLBACK_MODES = [
  "rollback",
  "dry-run",
  "manual-handoff",
  "degraded-safe-mode",
] as const;
export const AgentLifecycleFallbackModeSchema = z.enum(FALLBACK_MODES);
const VERIFICATION_KINDS = [
  "test",
  "policy",
  "health",
  "artifact",
  "deployment",
] as const;
const PRIVACY_CLASSIFICATIONS = [
  "public",
  "internal",
  "confidential",
  "restricted",
] as const;
export const AgentLifecycleActionRequestReasonCodeSchema = z.enum([
  "raw-command-not-allowed",
  "missing-evidence",
  "missing-tool-permission",
  "tool-permission-denied",
  "policy-denied",
  "approval-required",
  "missing-rollback-plan",
  "invalid-scope",
  "unsupported-action",
  "schema-invalid",
]);
const RAW_COMMAND_VALUE_PATTERN =
  /\b(gradle|gradlew|pnpm|npm|yarn|docker|docker\s+buildx|kubectl)\b/i;

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
    containsRawCommand(nested),
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

export const AgentLifecycleMasteryEvidenceSchema = z
  .object({
    state: AgentLifecycleMasteryStateSchema,
    stateRef: z.string().trim().min(1),
    evaluatedAt: z.string().datetime({ offset: true }),
  })
  .strict();

export const AgentLifecyclePolicyDecisionEvidenceSchema = z
  .object({
    decisionId: z.string().trim().min(1),
    decision: AgentLifecyclePolicyDecisionSchema,
    evaluatedAt: z.string().datetime({ offset: true }),
    reasonCodes: z.array(z.string().trim().min(1)).min(1),
    evidenceRefs: z.array(z.string().trim().min(1)).min(1),
  })
  .strict();

export const AgentLifecycleToolPermissionSchema = z
  .object({
    toolId: z.string().trim().min(1),
    permissionRef: z.string().trim().min(1),
    granted: z.boolean(),
    allowedActions: z.array(AgentLifecycleRequestedActionSchema).min(1),
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
    requestedByAgentVersion: z.string().trim().min(1),
    masteryState: AgentLifecycleMasteryEvidenceSchema,
    policyDecision: AgentLifecyclePolicyDecisionEvidenceSchema,
    toolPermissions: z.array(AgentLifecycleToolPermissionSchema).min(1),
    requestedAction: AgentLifecycleRequestedActionSchema,
    lifecyclePhase: z.enum(LIFECYCLE_PHASES),
    proposedPlanRef: z.string().trim().min(1),
    riskLevel: AgentLifecycleRiskLevelSchema,
    approvalRequired: z.boolean(),
    requiredApprovals: z.array(AgentLifecycleApprovalRequirementSchema),
    requiredVerification: z.array(AgentLifecycleVerificationRequirementSchema),
    inputRefs: z.array(z.string().trim().min(1)).min(1),
    outputRefs: z.array(z.string().trim().min(1)),
    verificationProofRefs: z.array(z.string().trim().min(1)),
    evidenceRefs: z.array(z.string().trim().min(1)).min(1),
    rollbackPlanRef: z.string().trim().min(1),
    fallbackMode: AgentLifecycleFallbackModeSchema,
    policyEvidenceRefs: z.array(z.string().trim().min(1)).optional(),
    masteryStateRef: z.string().trim().min(1).optional(),
    toolPermissionRefs: z.array(z.string().trim().min(1)).optional(),
    approvalTicketRefs: z.array(z.string().trim().min(1)).optional(),
    verificationEvidenceRefs: z.array(z.string().trim().min(1)).optional(),
    privacyClassification: z.enum(PRIVACY_CLASSIFICATIONS).optional(),
    retention: z
      .object({
        expiresAt: z.string().trim().min(1),
      })
      .optional(),
    modelDecisionContextRef: z.string().trim().min(1).optional(),
    redactionRequired: z.boolean().optional(),
  })
  .strict()
  .superRefine(
    (value: AgentLifecycleActionRequest, context: z.RefinementCtx) => {
      if (containsRawCommand(value)) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["proposedPlanRef"],
          message:
            "AgentLifecycleActionRequest must not contain raw shell/tool commands",
        });
      }
      // Validation: high/critical risk requires approval
      if (
        (value.riskLevel === "high" || value.riskLevel === "critical") &&
        value.requiredApprovals.every(
          (approval: { required: boolean }) => !approval.required,
        )
      ) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["requiredApprovals"],
          message:
            "High or critical risk actions require at least one required approval",
        });
      }
      if (
        value.approvalRequired &&
        value.requiredApprovals.every((approval) => !approval.required)
      ) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["approvalRequired"],
          message:
            "approvalRequired=true requires at least one required approval",
        });
      }
      if (value.policyDecision.decision === "denied") {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["policyDecision"],
          message:
            "Denied policy decisions cannot be submitted for lifecycle execution",
        });
      }
      if (
        value.policyDecision.decision === "requires-approval" &&
        !value.approvalRequired
      ) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["approvalRequired"],
          message:
            "Policy decisions requiring approval must set approvalRequired=true",
        });
      }
      if (
        !value.toolPermissions.some(
          (permission) =>
            permission.granted &&
            permission.allowedActions.includes(value.requestedAction),
        )
      ) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["toolPermissions"],
          message:
            "Requested action requires a granted matching tool permission",
        });
      }
      if (
        value.requiredVerification.some(
          (verification) => verification.required,
        ) &&
        value.verificationProofRefs.length === 0
      ) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["verificationProofRefs"],
          message:
            "Required verification must include at least one verification proof reference",
        });
      }
      // Validation: execute-lifecycle-phase and prepare-rollback require rollbackPlanRef
      if (
        (value.requestedAction === "execute-lifecycle-phase" ||
          value.requestedAction === "prepare-rollback") &&
        value.rollbackPlanRef === ""
      ) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["rollbackPlanRef"],
          message:
            "Execute-lifecycle-phase and prepare-rollback actions require a rollback plan reference",
        });
      }
      // Validation: restricted classification requires redaction flag
      if (
        value.privacyClassification === "restricted" &&
        !value.redactionRequired
      ) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["redactionRequired"],
          message:
            "Restricted classification requires redactionRequired flag to be true",
        });
      }
    },
  );

export const AgentLifecycleActionRequestValidationIssueSchema = z
  .object({
    path: z.string(),
    reasonCode: AgentLifecycleActionRequestReasonCodeSchema,
    message: z.string().trim().min(1),
  })
  .strict();

function reasonCodeForAgentLifecycleActionRequestIssue(
  issue: z.ZodIssue,
): AgentLifecycleActionRequestReasonCode {
  const path = issue.path.join(".");
  if (issue.message.includes("raw shell/tool commands")) {
    return "raw-command-not-allowed";
  }
  if (path === "evidenceRefs") {
    return "missing-evidence";
  }
  if (path === "toolPermissions") {
    return "missing-tool-permission";
  }
  if (path === "policyDecision") {
    return "policy-denied";
  }
  if (path === "approvalRequired" || path === "requiredApprovals") {
    return "approval-required";
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
  value: unknown,
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
    })),
  );
}

export function isAgentLifecycleActionRequest(
  value: unknown,
): value is AgentLifecycleActionRequest {
  return AgentLifecycleActionRequestSchema.safeParse(value).success;
}

export function validateAgentLifecycleRequestedAction(
  value: unknown,
): value is AgentLifecycleRequestedAction {
  return AgentLifecycleRequestedActionSchema.safeParse(value).success;
}

export function validateAgentLifecycleRiskLevel(
  value: unknown,
): value is AgentLifecycleRiskLevel {
  return AgentLifecycleRiskLevelSchema.safeParse(value).success;
}

export function validateAgentLifecycleMasteryState(
  value: unknown,
): value is AgentLifecycleMasteryState {
  return AgentLifecycleMasteryStateSchema.safeParse(value).success;
}

export function validateAgentLifecycleFallbackMode(
  value: unknown,
): value is AgentLifecycleFallbackMode {
  return AgentLifecycleFallbackModeSchema.safeParse(value).success;
}

export function validateAgentLifecycleActionRequestReasonCode(
  value: unknown,
): value is AgentLifecycleActionRequestReasonCode {
  return AgentLifecycleActionRequestReasonCodeSchema.safeParse(value).success;
}

export function validateAgentLifecycleActionRequestValidationIssue(
  value: unknown,
): value is AgentLifecycleActionRequestValidationIssue {
  return AgentLifecycleActionRequestValidationIssueSchema.safeParse(value).success;
}
