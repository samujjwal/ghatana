/**
 * KernelLifecycleEvent - base event contract for Kernel lifecycle events.
 *
 * @doc.type interface
 * @doc.purpose Base event contract for Kernel lifecycle operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

import { z } from "zod";
import type { ProductLifecyclePhase } from "../lifecycle/ProductLifecyclePhase";

export const KERNEL_EVENT_SCHEMA_VERSION = "1.0.0";

export const KERNEL_LIFECYCLE_EVENT_TYPES = [
  "product-unit.intent.created",
  "product-unit.intent.validated",
  "product-unit.intent.applied",
  "lifecycle.plan.created",
  "lifecycle.phase.started",
  "lifecycle.phase.completed",
  "lifecycle.step.started",
  "lifecycle.step.completed",
  "lifecycle.gate.evaluated",
  "lifecycle.artifact.recorded",
  "lifecycle.manifest.written",
  "lifecycle.deployment.completed",
  "lifecycle.health.checked",
  "lifecycle.agent.governance.evaluated",
  "lifecycle.approval.requested",
  "lifecycle.approval.decided",
] as const;

export type KernelLifecycleEventType =
  (typeof KERNEL_LIFECYCLE_EVENT_TYPES)[number];

export const KernelLifecycleEventTypeSchema = z.enum(
  KERNEL_LIFECYCLE_EVENT_TYPES
);

const PRODUCT_LIFECYCLE_PHASES = [
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

export const LIFECYCLE_EVENT_STATUSES = [
  "healthy",
  "degraded",
  "blocked",
  "failed",
  "skipped",
  "running",
  "pending approval",
  "requires verification",
  "obsolete",
  "quarantined",
  "unknown",
] as const;

export type LifecycleEventStatus = (typeof LIFECYCLE_EVENT_STATUSES)[number];

export const LifecycleEventStatusSchema = z.enum(LIFECYCLE_EVENT_STATUSES);

/**
 * Base event metadata for all Kernel lifecycle events.
 */
export interface KernelEventMetadata {
  /**
   * Unique event identifier.
   */
  readonly eventId: string;

  /**
   * Schema version for event contract compatibility.
   */
  readonly schemaVersion: "1.0.0";

  /**
   * Event type identifier.
   */
  readonly eventType: KernelLifecycleEventType;

  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Lifecycle run identifier.
   */
  readonly runId: string;

  /**
   * Lifecycle phase.
   */
  readonly phase: ProductLifecyclePhase;

  /**
   * Event timestamp (ISO 8601).
   */
  readonly timestamp: string;

  /**
   * Event source (e.g., "kernel-lifecycle", "provider-xyz").
   */
  readonly source: string;

  /**
   * Optional tenant identifier for multi-tenant environments.
   */
  readonly tenantId?: string;

  /**
   * Optional workspace identifier.
   */
  readonly workspaceId?: string;

  /**
   * Optional project identifier.
   */
  readonly projectId?: string;

  /**
   * Correlation identifier for tracing related events.
   */
  readonly correlationId: string;
}

export interface ProductUnitIntentCreatedPayload {
  readonly intentId: string;
  readonly intentType: "create" | "update" | "promote-candidate";
  readonly producerId: string;
  readonly producerType: string;
  readonly productUnitDraftId: string;
}

export interface ProductUnitIntentValidatedPayload {
  readonly intentId: string;
  readonly valid: boolean;
  readonly errors: readonly string[];
}

export interface ProductUnitIntentAppliedPayload {
  readonly intentId: string;
  readonly productUnitId: string;
  readonly applied: boolean;
  readonly changedFiles: readonly string[];
}

export interface LifecyclePlanCreatedPayload {
  readonly planRunId: string;
  readonly phase: ProductLifecyclePhase;
  readonly providerMode: "bootstrap" | "platform";
  readonly environment?: string;
  readonly dryRun?: boolean;
  readonly createdAt: string;
}

export interface LifecyclePhaseStartedPayload {
  readonly phase: ProductLifecyclePhase;
  readonly status: "running";
  readonly startedAt: string;
}

export interface LifecyclePhaseCompletedPayload {
  readonly phase: ProductLifecyclePhase;
  readonly status: "succeeded" | "failed" | "skipped";
  readonly durationMs: number;
  readonly completedAt: string;
}

export interface LifecycleStepStartedPayload {
  readonly stepId: string;
  readonly stepKind: string;
  readonly surface: string;
  readonly adapter: string;
  readonly status: "running";
  readonly startedAt: string;
}

export interface LifecycleStepCompletedPayload {
  readonly stepId: string;
  readonly stepKind: string;
  readonly surface: string;
  readonly adapter: string;
  readonly status: "succeeded" | "failed" | "skipped";
  readonly durationMs: number;
  readonly completedAt: string;
  readonly exitCode?: number;
  readonly evidenceRefs: readonly string[];
}

export interface LifecycleGateEvaluatedPayload {
  readonly gateId: string;
  readonly status: "passed" | "failed" | "skipped";
  readonly required: boolean;
  readonly reason: string;
  readonly evidenceRefs: readonly string[];
  readonly durationMs: number;
}

export interface LifecycleArtifactRecordedPayload {
  readonly artifactId: string;
  readonly artifactType: string;
  readonly required: boolean;
  readonly path?: string;
  readonly fingerprint?: string;
  readonly evidenceRefs: readonly string[];
}

export interface LifecycleManifestWrittenPayload {
  readonly manifestType:
    | "lifecycle-plan"
    | "lifecycle-result"
    | "gate-result-manifest"
    | "artifact-manifest"
    | "deployment-manifest"
    | "rollback-manifest"
    | "verify-health-report"
    | "lifecycle-health-snapshot"
    | "lifecycle-events";
  readonly path: string;
  readonly required: boolean;
  readonly status: "written" | "failed";
}

export interface LifecycleDeploymentCompletedPayload {
  readonly deploymentId: string;
  readonly environment: string;
  readonly status: "succeeded" | "failed" | "skipped";
  readonly artifactIds: readonly string[];
  readonly endpoints: readonly string[];
  readonly durationMs: number;
}

export interface LifecycleHealthCheckedPayload {
  readonly checkId: string;
  readonly checkName: string;
  readonly status: "healthy" | "degraded" | "blocked" | "failed" | "skipped" | "unknown";
  readonly message: string;
  readonly durationMs: number;
  readonly deploymentId?: string;
  readonly environment?: string;
}

export interface LifecycleAgentGovernanceEvaluatedPayload {
  readonly agentId: string;
  readonly actionType: string;
  readonly decision: "allowed" | "denied" | "requires-approval";
  readonly reason: string;
  readonly masteryState?: string;
  readonly executionMode?: string;
  readonly evidenceRefs: readonly string[];
}

export interface LifecycleApprovalRequestedPayload {
  readonly approvalId: string;
  readonly action: string;
  readonly riskLevel: "low" | "medium" | "high" | "critical";
  readonly requestedBy: string;
  readonly evidenceRefs: readonly string[];
}

export interface LifecycleApprovalDecidedPayload {
  readonly approvalId: string;
  readonly decision: "approved" | "rejected";
  readonly decidedBy: string;
  readonly reason: string;
}

export type KernelLifecycleEventPayload =
  | ProductUnitIntentCreatedPayload
  | ProductUnitIntentValidatedPayload
  | ProductUnitIntentAppliedPayload
  | LifecyclePlanCreatedPayload
  | LifecyclePhaseStartedPayload
  | LifecyclePhaseCompletedPayload
  | LifecycleStepStartedPayload
  | LifecycleStepCompletedPayload
  | LifecycleGateEvaluatedPayload
  | LifecycleArtifactRecordedPayload
  | LifecycleManifestWrittenPayload
  | LifecycleDeploymentCompletedPayload
  | LifecycleHealthCheckedPayload
  | LifecycleAgentGovernanceEvaluatedPayload
  | LifecycleApprovalRequestedPayload
  | LifecycleApprovalDecidedPayload;

/**
 * Kernel lifecycle event.
 */
export interface KernelLifecycleEvent {
  /**
   * Event metadata.
   */
  readonly metadata: KernelEventMetadata;

  /**
   * Event payload.
   */
  readonly payload: KernelLifecycleEventPayload;
}

export interface KernelLifecycleEventValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
}

export const KernelEventMetadataSchema = z
  .object({
    eventId: z.string().trim().min(1),
    schemaVersion: z.literal(KERNEL_EVENT_SCHEMA_VERSION),
    eventType: KernelLifecycleEventTypeSchema,
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    phase: z.enum(PRODUCT_LIFECYCLE_PHASES),
    timestamp: z.string().datetime({ offset: true }),
    source: z.string().trim().min(1),
    tenantId: z.string().trim().min(1).optional(),
    workspaceId: z.string().trim().min(1).optional(),
    projectId: z.string().trim().min(1).optional(),
    correlationId: z.string().trim().min(1),
  })
  .strict();

export const ProductUnitIntentCreatedPayloadSchema = z
  .object({
    intentId: z.string().trim().min(1),
    intentType: z.enum(["create", "update", "promote-candidate"]),
    producerId: z.string().trim().min(1),
    producerType: z.string().trim().min(1),
    productUnitDraftId: z.string().trim().min(1),
  })
  .strict();

export const ProductUnitIntentValidatedPayloadSchema = z
  .object({
    intentId: z.string().trim().min(1),
    valid: z.boolean(),
    errors: z.array(z.string()),
  })
  .strict();

export const ProductUnitIntentAppliedPayloadSchema = z
  .object({
    intentId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1),
    applied: z.boolean(),
    changedFiles: z.array(z.string().trim().min(1)),
  })
  .strict();

export const LifecyclePlanCreatedPayloadSchema = z
  .object({
    planRunId: z.string().trim().min(1),
    phase: z.enum(PRODUCT_LIFECYCLE_PHASES),
    providerMode: z.enum(["bootstrap", "platform"]),
    environment: z.string().trim().min(1).optional(),
    dryRun: z.boolean().optional(),
    createdAt: z.string().datetime({ offset: true }),
  })
  .strict();

export const LifecyclePhaseStartedPayloadSchema = z
  .object({
    phase: z.enum(PRODUCT_LIFECYCLE_PHASES),
    status: z.literal("running"),
    startedAt: z.string().datetime({ offset: true }),
  })
  .strict();

export const LifecyclePhaseCompletedPayloadSchema = z
  .object({
    phase: z.enum(PRODUCT_LIFECYCLE_PHASES),
    status: z.enum(["succeeded", "failed", "skipped"]),
    durationMs: z.number().nonnegative(),
    completedAt: z.string().datetime({ offset: true }),
  })
  .strict();

export const LifecycleStepStartedPayloadSchema = z
  .object({
    stepId: z.string().trim().min(1),
    stepKind: z.string().trim().min(1),
    surface: z.string().trim().min(1),
    adapter: z.string().trim().min(1),
    status: z.literal("running"),
    startedAt: z.string().datetime({ offset: true }),
  })
  .strict();

export const LifecycleStepCompletedPayloadSchema = z
  .object({
    stepId: z.string().trim().min(1),
    stepKind: z.string().trim().min(1),
    surface: z.string().trim().min(1),
    adapter: z.string().trim().min(1),
    status: z.enum(["succeeded", "failed", "skipped"]),
    durationMs: z.number().nonnegative(),
    completedAt: z.string().datetime({ offset: true }),
    exitCode: z.number().int().optional(),
    evidenceRefs: z.array(z.string().trim().min(1)),
  })
  .strict();

export const LifecycleGateEvaluatedPayloadSchema = z
  .object({
    gateId: z.string().trim().min(1),
    status: z.enum(["passed", "failed", "skipped"]),
    required: z.boolean(),
    reason: z.string().trim().min(1),
    evidenceRefs: z.array(z.string().trim().min(1)),
    durationMs: z.number().nonnegative(),
  })
  .strict();

export const LifecycleArtifactRecordedPayloadSchema = z
  .object({
    artifactId: z.string().trim().min(1),
    artifactType: z.string().trim().min(1),
    required: z.boolean(),
    path: z.string().trim().min(1).optional(),
    fingerprint: z.string().trim().min(1).optional(),
    evidenceRefs: z.array(z.string().trim().min(1)),
  })
  .strict();

export const LifecycleManifestWrittenPayloadSchema = z
  .object({
    manifestType: z.enum([
      "lifecycle-plan",
      "lifecycle-result",
      "gate-result-manifest",
      "artifact-manifest",
      "deployment-manifest",
      "rollback-manifest",
      "verify-health-report",
      "lifecycle-health-snapshot",
      "lifecycle-events",
    ]),
    path: z.string().trim().min(1),
    required: z.boolean(),
    status: z.enum(["written", "failed"]),
  })
  .strict();

export const LifecycleDeploymentCompletedPayloadSchema = z
  .object({
    deploymentId: z.string().trim().min(1),
    environment: z.string().trim().min(1),
    status: z.enum(["succeeded", "failed", "skipped"]),
    artifactIds: z.array(z.string().trim().min(1)),
    endpoints: z.array(z.string().trim().min(1)),
    durationMs: z.number().nonnegative(),
  })
  .strict();

export const LifecycleHealthCheckedPayloadSchema = z
  .object({
    checkId: z.string().trim().min(1),
    checkName: z.string().trim().min(1),
    status: z.enum(["healthy", "degraded", "blocked", "failed", "skipped", "unknown"]),
    message: z.string().trim().min(1),
    durationMs: z.number().nonnegative(),
    deploymentId: z.string().trim().min(1).optional(),
    environment: z.string().trim().min(1).optional(),
  })
  .strict();

export const LifecycleAgentGovernanceEvaluatedPayloadSchema = z
  .object({
    agentId: z.string().trim().min(1),
    actionType: z.string().trim().min(1),
    decision: z.enum(["allowed", "denied", "requires-approval"]),
    reason: z.string().trim().min(1),
    masteryState: z.string().trim().min(1).optional(),
    executionMode: z.string().trim().min(1).optional(),
    evidenceRefs: z.array(z.string().trim().min(1)),
  })
  .strict();

export const LifecycleApprovalRequestedPayloadSchema = z
  .object({
    approvalId: z.string().trim().min(1),
    action: z.string().trim().min(1),
    riskLevel: z.enum(["low", "medium", "high", "critical"]),
    requestedBy: z.string().trim().min(1),
    evidenceRefs: z.array(z.string().trim().min(1)),
  })
  .strict();

export const LifecycleApprovalDecidedPayloadSchema = z
  .object({
    approvalId: z.string().trim().min(1),
    decision: z.enum(["approved", "rejected"]),
    decidedBy: z.string().trim().min(1),
    reason: z.string().trim().min(1),
  })
  .strict();

const payloadSchemasByEventType = {
  "product-unit.intent.created": ProductUnitIntentCreatedPayloadSchema,
  "product-unit.intent.validated": ProductUnitIntentValidatedPayloadSchema,
  "product-unit.intent.applied": ProductUnitIntentAppliedPayloadSchema,
  "lifecycle.plan.created": LifecyclePlanCreatedPayloadSchema,
  "lifecycle.phase.started": LifecyclePhaseStartedPayloadSchema,
  "lifecycle.phase.completed": LifecyclePhaseCompletedPayloadSchema,
  "lifecycle.step.started": LifecycleStepStartedPayloadSchema,
  "lifecycle.step.completed": LifecycleStepCompletedPayloadSchema,
  "lifecycle.gate.evaluated": LifecycleGateEvaluatedPayloadSchema,
  "lifecycle.artifact.recorded": LifecycleArtifactRecordedPayloadSchema,
  "lifecycle.manifest.written": LifecycleManifestWrittenPayloadSchema,
  "lifecycle.deployment.completed": LifecycleDeploymentCompletedPayloadSchema,
  "lifecycle.health.checked": LifecycleHealthCheckedPayloadSchema,
  "lifecycle.agent.governance.evaluated": LifecycleAgentGovernanceEvaluatedPayloadSchema,
  "lifecycle.approval.requested": LifecycleApprovalRequestedPayloadSchema,
  "lifecycle.approval.decided": LifecycleApprovalDecidedPayloadSchema,
} as const;

export const KernelLifecycleEventPayloadSchema = z.union([
  ProductUnitIntentCreatedPayloadSchema,
  ProductUnitIntentValidatedPayloadSchema,
  ProductUnitIntentAppliedPayloadSchema,
  LifecyclePlanCreatedPayloadSchema,
  LifecyclePhaseStartedPayloadSchema,
  LifecyclePhaseCompletedPayloadSchema,
  LifecycleStepStartedPayloadSchema,
  LifecycleStepCompletedPayloadSchema,
  LifecycleGateEvaluatedPayloadSchema,
  LifecycleArtifactRecordedPayloadSchema,
  LifecycleManifestWrittenPayloadSchema,
  LifecycleDeploymentCompletedPayloadSchema,
  LifecycleHealthCheckedPayloadSchema,
  LifecycleAgentGovernanceEvaluatedPayloadSchema,
  LifecycleApprovalRequestedPayloadSchema,
  LifecycleApprovalDecidedPayloadSchema,
]);

export const KernelLifecycleEventValidationResultSchema = z
  .object({
    valid: z.boolean(),
    errors: z.array(z.string()),
  })
  .strict();

export const KernelLifecycleEventSchema = z
  .object({
    metadata: KernelEventMetadataSchema,
    payload: z.unknown(),
  })
  .strict()
  .superRefine((event, context) => {
    const schema = payloadSchemasByEventType[event.metadata.eventType];
    const payloadResult = schema.safeParse(event.payload);
    if (!payloadResult.success) {
      for (const issue of payloadResult.error.issues) {
        context.addIssue({
          ...issue,
          path: ["payload", ...issue.path],
        });
      }
    }
  });

export function validateKernelLifecycleEvent(
  value: unknown
): KernelLifecycleEventValidationResult {
  const parsed = KernelLifecycleEventSchema.safeParse(value);
  if (parsed.success) {
    return { valid: true, errors: [] };
  }
  return {
    valid: false,
    errors: parsed.error.issues.map((issue) => {
      const path = issue.path.join(".");
      return `${path}: ${issue.message}`;
    }),
  };
}

export function isKernelLifecycleEvent(
  value: unknown
): value is KernelLifecycleEvent {
  return validateKernelLifecycleEvent(value).valid;
}

export function validateProductUnitIntentCreatedPayload(
  value: unknown
): value is ProductUnitIntentCreatedPayload {
  return ProductUnitIntentCreatedPayloadSchema.safeParse(value).success;
}

export function validateProductUnitIntentValidatedPayload(
  value: unknown
): value is ProductUnitIntentValidatedPayload {
  return ProductUnitIntentValidatedPayloadSchema.safeParse(value).success;
}

export function validateProductUnitIntentAppliedPayload(
  value: unknown
): value is ProductUnitIntentAppliedPayload {
  return ProductUnitIntentAppliedPayloadSchema.safeParse(value).success;
}

export function validateKernelLifecycleEventType(
  value: unknown
): value is KernelLifecycleEventType {
  return KernelLifecycleEventTypeSchema.safeParse(value).success;
}

export function validateLifecycleEventStatus(
  value: unknown
): value is LifecycleEventStatus {
  return LifecycleEventStatusSchema.safeParse(value).success;
}

export function validateKernelLifecycleEventPayload(
  value: unknown
): value is KernelLifecycleEventPayload {
  return KernelLifecycleEventPayloadSchema.safeParse(value).success;
}

export function validateKernelLifecycleEventValidationResult(
  value: unknown
): value is KernelLifecycleEventValidationResult {
  return KernelLifecycleEventValidationResultSchema.safeParse(value).success;
}

export function validateLifecyclePlanCreatedPayload(
  value: unknown
): value is LifecyclePlanCreatedPayload {
  return LifecyclePlanCreatedPayloadSchema.safeParse(value).success;
}

export function validateLifecyclePhaseStartedPayload(
  value: unknown
): value is LifecyclePhaseStartedPayload {
  return LifecyclePhaseStartedPayloadSchema.safeParse(value).success;
}

export function validateLifecyclePhaseCompletedPayload(
  value: unknown
): value is LifecyclePhaseCompletedPayload {
  return LifecyclePhaseCompletedPayloadSchema.safeParse(value).success;
}

export function validateLifecycleStepStartedPayload(
  value: unknown
): value is LifecycleStepStartedPayload {
  return LifecycleStepStartedPayloadSchema.safeParse(value).success;
}

export function validateLifecycleStepCompletedPayload(
  value: unknown
): value is LifecycleStepCompletedPayload {
  return LifecycleStepCompletedPayloadSchema.safeParse(value).success;
}

export function validateLifecycleGateEvaluatedPayload(
  value: unknown
): value is LifecycleGateEvaluatedPayload {
  return LifecycleGateEvaluatedPayloadSchema.safeParse(value).success;
}

export function validateLifecycleArtifactRecordedPayload(
  value: unknown
): value is LifecycleArtifactRecordedPayload {
  return LifecycleArtifactRecordedPayloadSchema.safeParse(value).success;
}

export function validateLifecycleManifestWrittenPayload(
  value: unknown
): value is LifecycleManifestWrittenPayload {
  return LifecycleManifestWrittenPayloadSchema.safeParse(value).success;
}

export function validateLifecycleDeploymentCompletedPayload(
  value: unknown
): value is LifecycleDeploymentCompletedPayload {
  return LifecycleDeploymentCompletedPayloadSchema.safeParse(value).success;
}

export function validateLifecycleHealthCheckedPayload(
  value: unknown
): value is LifecycleHealthCheckedPayload {
  return LifecycleHealthCheckedPayloadSchema.safeParse(value).success;
}

export function validateLifecycleAgentGovernanceEvaluatedPayload(
  value: unknown
): value is LifecycleAgentGovernanceEvaluatedPayload {
  return LifecycleAgentGovernanceEvaluatedPayloadSchema.safeParse(value).success;
}

export function validateLifecycleApprovalRequestedPayload(
  value: unknown
): value is LifecycleApprovalRequestedPayload {
  return LifecycleApprovalRequestedPayloadSchema.safeParse(value).success;
}

export function validateLifecycleApprovalDecidedPayload(
  value: unknown
): value is LifecycleApprovalDecidedPayload {
  return LifecycleApprovalDecidedPayloadSchema.safeParse(value).success;
}
