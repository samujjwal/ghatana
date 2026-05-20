/**
 * LifecycleContracts - canonical lifecycle plan, execution, and result schemas.
 *
 * Complements ProductLifecyclePhase.ts with the full set of persisted/external
 * truth contracts required by the Product Development Kernel.
 *
 * Rules:
 * - All persisted contracts include schemaVersion, createdAt, runId, correlationId.
 * - Use discriminated unions for status fields.
 * - All exported parser functions validate at system boundaries.
 * - No product-specific business logic here.
 *
 * @doc.type module
 * @doc.purpose Canonical lifecycle plan, execution, and result contracts
 * @doc.layer kernel-product-contracts
 * @doc.pattern Contract
 */

import { z } from "zod";

// ---------------------------------------------------------------------------
// Branded types
// ---------------------------------------------------------------------------

/** Opaque run identifier — a UUID assigned at plan creation time. */
export type LifecycleRunId = string & { readonly __brand: "LifecycleRunId" };

/** Opaque correlation identifier — propagated across service boundaries. */
export type LifecycleCorrelationId = string & {
  readonly __brand: "LifecycleCorrelationId";
};

/** Creates a LifecycleRunId from a plain string. Only use at system entry points. */
export function createLifecycleRunId(id: string): LifecycleRunId {
  if (!id || id.trim().length === 0) {
    throw new Error("LifecycleRunId must be a non-empty string");
  }
  return id as LifecycleRunId;
}

/** Creates a LifecycleCorrelationId from a plain string. Only use at system entry points. */
export function createLifecycleCorrelationId(
  id: string,
): LifecycleCorrelationId {
  if (!id || id.trim().length === 0) {
    throw new Error("LifecycleCorrelationId must be a non-empty string");
  }
  return id as LifecycleCorrelationId;
}

// ---------------------------------------------------------------------------
// Lifecycle run status — discriminated union
// ---------------------------------------------------------------------------

/** Canonical shared status vocabulary for lifecycle operations. */
export const LIFECYCLE_RUN_STATUSES = [
  "pending",
  "running",
  "succeeded",
  "failed",
  "blocked",
  "skipped",
  "degraded",
  "requires-approval",
  "requires-verification",
  "unknown",
] as const;

export type LifecycleRunStatus = (typeof LIFECYCLE_RUN_STATUSES)[number];

export const LifecycleRunStatusSchema = z.enum(LIFECYCLE_RUN_STATUSES);

// ---------------------------------------------------------------------------
// LifecycleProfile
// ---------------------------------------------------------------------------

export const LifecycleProfileSchema = z.object({
  schemaVersion: z.literal("1.0.0"),
  profileId: z.string().min(1),
  displayName: z.string().min(1),
  description: z.string().optional(),
  defaultPhases: z.array(z.string()).min(1),
  phaseGraph: z
    .object({
      nodes: z.array(z.string().trim().min(1)),
      edges: z.array(
        z
          .object({
            from: z.string().trim().min(1),
            to: z.string().trim().min(1),
          })
          .strict(),
      ),
    })
    .strict()
    .optional(),
  phaseDefaults: z
    .record(
      z.string(),
      z.object({
        mode: z.enum(["parallel", "sequential", "dag"]),
        defaultSurfaces: z.array(z.string()),
        requiredGates: z.array(z.string().trim().min(1)).optional(),
        phaseOutputs: z.array(z.string().trim().min(1)).optional(),
        providerModes: z.array(z.enum(["bootstrap", "platform"])).optional(),
        evidencePolicy: z.enum(["none", "best-effort", "required"]).optional(),
        envConstraints: z.array(z.string().trim().min(1)).optional(),
      }),
    )
    .optional(),
  requiredProviders: z.array(z.string().trim().min(1)).optional(),
  requiredManifests: z.array(z.string()).optional(),
  tags: z.array(z.string()).optional(),
});

export type LifecycleProfile = z.infer<typeof LifecycleProfileSchema>;

export function parseLifecycleProfile(input: unknown): LifecycleProfile {
  return LifecycleProfileSchema.parse(input);
}

// ---------------------------------------------------------------------------
// LifecyclePlanStep
// ---------------------------------------------------------------------------

export const LifecyclePlanStepSchema = z.object({
  stepId: z.string().min(1),
  stepKind: z.enum([
    "gate",
    "surface",
    "package",
    "deploy",
    "verify",
    "release",
    "promotion",
    "rollback",
  ]),
  phase: z.string().min(1),
  surface: z.string(),
  adapter: z.string().min(1),
  adapterSelectionSource: z
    .enum(["profile-default", "product-config-override"])
    .optional(),
  description: z.string(),
  dependsOn: z.array(z.string()),
  estimatedDurationMs: z.number().int().nonnegative(),
  adapterContext: z.record(z.string(), z.unknown()).optional(),
});

export type LifecyclePlanStep = z.infer<typeof LifecyclePlanStepSchema>;

// ---------------------------------------------------------------------------
// LifecyclePlan
// ---------------------------------------------------------------------------

export const LifecyclePlanSchema = z.object({
  schemaVersion: z.literal("1.0.0"),
  runId: z.string().min(1),
  correlationId: z.string().min(1),
  createdAt: z.string().datetime(),
  productId: z.string().min(1),
  /** Canonical product unit identifier. Preferred over productId for new contracts. */
  productUnitId: z.string().min(1).optional(),
  productUnitRef: z.string().optional(),
  phase: z.string().min(1),
  phaseMode: z.enum(["parallel", "sequential", "dag"]),
  lifecycleProfile: z.string().min(1),
  environment: z.string().optional(),
  sourceRef: z.string().optional(),
  steps: z.array(LifecyclePlanStepSchema).min(0),
  outputDirectory: z.string().min(1),
  estimatedDurationMs: z.number().int().nonnegative(),
  semanticArtifactRefs: z.array(z.string()).optional(),
  warnings: z.array(z.string()).optional(),
  blockingReasons: z.array(z.string()).optional(),
  surfaces: z.array(z.string()).optional(),
  adapterIds: z.array(z.string()).optional(),
  gates: z.array(z.string()).optional(),
  requiredManifests: z.array(z.string()).optional(),
  expectedArtifacts: z.array(z.string()).optional(),
  healthChecks: z.array(z.string()).optional(),
  approvalRequirements: z.array(z.string()).optional(),
});

export type LifecyclePlan = z.infer<typeof LifecyclePlanSchema>;

export function parseLifecyclePlan(input: unknown): LifecyclePlan {
  return LifecyclePlanSchema.parse(input);
}

// ---------------------------------------------------------------------------
// LifecycleExecutionRequest
// ---------------------------------------------------------------------------

export const LifecycleExecutionRequestSchema = z.object({
  schemaVersion: z.literal("1.0.0"),
  runId: z.string().min(1),
  correlationId: z.string().min(1),
  createdAt: z.string().datetime(),
  productId: z.string().min(1),
  phase: z.string().min(1),
  environment: z.string().optional(),
  sourceRef: z.string().optional(),
  dryRun: z.boolean().default(false),
  surfaceSelector: z.array(z.string()).optional(),
  outputDirectory: z.string().min(1),
  providerMode: z.enum(["real", "simulated", "dry-run"]).optional(),
});

export type LifecycleExecutionRequest = z.infer<
  typeof LifecycleExecutionRequestSchema
>;

export function parseLifecycleExecutionRequest(
  input: unknown,
): LifecycleExecutionRequest {
  return LifecycleExecutionRequestSchema.parse(input);
}

// ---------------------------------------------------------------------------
// LifecycleFailure — standalone failure contract
// ---------------------------------------------------------------------------

/** Canonical set of lifecycle failure reason codes. */
export const LIFECYCLE_FAILURE_REASON_CODES = [
  "config-invalid",
  "adapter-failed",
  "command-failed",
  "gate-failed",
  "artifact-missing",
  "output-missing",
  "environment-blocked",
  "dependency-blocked",
  "manifest-write-failed",
  "approval-required",
  "approval-missing",
  "policy-denied",
  "security-policy-denied",
  "provider-unavailable",
  "disabled-product",
  "missing-adapter",
  "adapter-missing",
  "invalid-registry-state",
  "unknown",
] as const;

export const LifecycleFailureSchema = z.object({
  reasonCode: z.enum(LIFECYCLE_FAILURE_REASON_CODES).optional(),
  stepId: z.string().optional(),
  message: z.string(),
  actionableMessage: z.string().optional(),
  cause: z.string().optional(),
  evidenceRefs: z.array(z.string()).optional(),
  diagnostics: z.record(z.string(), z.unknown()).optional(),
});

export type LifecycleFailure = z.infer<typeof LifecycleFailureSchema>;

// ---------------------------------------------------------------------------
// LifecycleStepResult
// ---------------------------------------------------------------------------

export const LifecycleStepResultSchema = z.object({
  stepId: z.string().min(1),
  status: LifecycleRunStatusSchema,
  startedAt: z.string().datetime(),
  completedAt: z.string().datetime(),
  durationMs: z.number().int().nonnegative(),
  exitCode: z.number().int().optional(),
  artifactRefs: z.array(z.string()).optional(),
  failureReasonCode: z.string().optional(),
  failureMessage: z.string().optional(),
});

export type LifecycleStepResult = z.infer<typeof LifecycleStepResultSchema>;

// ---------------------------------------------------------------------------
// LifecycleExecutionResult
// ---------------------------------------------------------------------------

export const LifecycleExecutionResultSchema = z.object({
  schemaVersion: z.literal("1.0.0"),
  runId: z.string().min(1),
  correlationId: z.string().min(1),
  createdAt: z.string().datetime(),
  productId: z.string().min(1),
  /** Canonical product unit identifier. Preferred over productId for new contracts. */
  productUnitId: z.string().min(1).optional(),
  productUnitRef: z.string().optional(),
  phase: z.string().min(1),
  environment: z.string().optional(),
  sourceRef: z.string().optional(),
  lifecycleProfile: z.string().min(1),
  status: LifecycleRunStatusSchema,
  reasonCode: z.string().optional(),
  actionableMessage: z.string().optional(),
  evidenceRefs: z.array(z.string()).optional(),
  diagnostics: z.record(z.string(), z.unknown()).optional(),
  startedAt: z.string().datetime(),
  completedAt: z.string().datetime(),
  durationMs: z.number().int().nonnegative(),
  steps: z.array(LifecycleStepResultSchema),
  gateRefs: z.array(z.string()),
  artifactRefs: z.array(z.string()),
  deploymentRef: z.string().optional(),
  healthRef: z.string().optional(),
  manifestRefs: z
    .object({
      lifecyclePlan: z.string().optional(),
      lifecycleResult: z.string().optional(),
      lifecycleEvents: z.string().optional(),
      gateResultManifest: z.string().optional(),
      artifactManifest: z.string().optional(),
      deploymentManifest: z.string().optional(),
      rollbackManifest: z.string().optional(),
      verifyHealthReport: z.string().optional(),
      lifecycleHealthSnapshot: z.string().optional(),
    })
    .optional(),
  failure: LifecycleFailureSchema.optional(),
  dryRun: z.boolean().optional(),
});

export type LifecycleExecutionResult = z.infer<
  typeof LifecycleExecutionResultSchema
>;

/** Canonical failure reason codes for lifecycle execution. */
export type LifecycleFailureReasonCode = (typeof LIFECYCLE_FAILURE_REASON_CODES)[number];

/**
 * LifecycleResult is the canonical alias for LifecycleExecutionResult.
 * Prefer LifecycleResult in new code.
 */
export type LifecycleResult = LifecycleExecutionResult;
export const LifecycleResultSchema = LifecycleExecutionResultSchema;

/**
 * LifecycleExecutionContext is the canonical alias for LifecycleExecutionRequest.
 * Prefer LifecycleExecutionContext in new code.
 */
export type LifecycleExecutionContext = LifecycleExecutionRequest;
export const LifecycleExecutionContextSchema = LifecycleExecutionRequestSchema;

export function parseLifecycleExecutionResult(
  input: unknown,
): LifecycleExecutionResult {
  return LifecycleExecutionResultSchema.parse(input);
}
