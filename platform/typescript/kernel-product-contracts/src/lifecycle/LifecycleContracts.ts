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
// LifecycleFailureClassifier — enhanced failure classification
// ---------------------------------------------------------------------------

/**
 * Failure category for classification and routing.
 */
export const FAILURE_CATEGORIES = [
  "config",
  "adapter",
  "command",
  "gate",
  "artifact",
  "dependency",
  "environment",
  "approval",
  "policy",
  "security",
  "provider",
  "infrastructure",
  "unknown",
] as const;

export type FailureCategory = (typeof FAILURE_CATEGORIES)[number];

/**
 * Failure severity for prioritization and alerting.
 */
export const FAILURE_SEVERITIES = [
  "critical",
  "high",
  "medium",
  "low",
  "info",
] as const;

export type FailureSeverity = (typeof FAILURE_SEVERITIES)[number];

/**
 * LifecycleFailureClassifier provides detailed failure classification.
 */
export const LifecycleFailureClassifierSchema = z.object({
  /**
   * High-level failure category.
   */
  category: z.enum(FAILURE_CATEGORIES),

  /**
   * Failure severity for prioritization.
   */
  severity: z.enum(FAILURE_SEVERITIES),

  /**
   * Whether the failure is retryable with the same input.
   */
  retryable: z.boolean(),

  /**
   * Whether the failure requires human intervention.
   */
  requiresHumanIntervention: z.boolean(),

  /**
   * Suggested remediation steps.
   */
  remediationSteps: z.array(z.string()).optional(),

  /**
   * Related failure codes for grouping.
   */
  relatedFailureCodes: z.array(z.string()).optional(),

  /**
   * Component or subsystem that failed.
   */
  component: z.string().optional(),

  /**
   * Whether this failure is a known issue with a workaround.
   */
  knownWorkaround: z
    .object({
      description: z.string(),
      workaroundSteps: z.array(z.string()),
    })
    .optional(),
});

export type LifecycleFailureClassifier = z.infer<
  typeof LifecycleFailureClassifierSchema
>;

// ---------------------------------------------------------------------------
// Dependency Graph
// ---------------------------------------------------------------------------

/**
 * Node in the dependency graph.
 */
export const DependencyGraphNodeSchema = z.object({
  stepId: z.string().min(1),
  phase: z.string().min(1),
  surface: z.string().min(1),
  adapter: z.string().min(1),
  status: z.enum(["pending", "running", "succeeded", "failed", "skipped"]),
  estimatedDurationMs: z.number().int().nonnegative(),
  actualDurationMs: z.number().int().nonnegative().optional(),
  dependencies: z.array(z.string().min(1)),
  dependents: z.array(z.string().min(1)),
});

export type DependencyGraphNode = z.infer<typeof DependencyGraphNodeSchema>;

/**
 * Dependency graph for plan explain output.
 */
export const DependencyGraphSchema = z.object({
  nodes: z.array(DependencyGraphNodeSchema),
  edges: z.array(
    z.object({
      from: z.string().min(1),
      to: z.string().min(1),
      type: z.enum(["depends-on", "runs-before", "provides-artifact"]),
    }),
  ),
  criticalPath: z.array(z.string().min(1)).optional(),
});

export type DependencyGraph = z.infer<typeof DependencyGraphSchema>;

// ---------------------------------------------------------------------------
// Provider Checks
// ---------------------------------------------------------------------------

/**
 * Individual provider health check result.
 */
export const ProviderCheckSchema = z.object({
  providerId: z.string().min(1),
  providerKind: z.string().min(1),
  status: z.enum(["healthy", "degraded", "unhealthy", "unknown"]),
  message: z.string(),
  latencyMs: z.number().int().nonnegative().optional(),
  checkedAt: z.string().datetime(),
  capabilities: z.array(
    z.object({
      name: z.string().min(1),
      available: z.boolean(),
      required: z.boolean(),
    }),
  ),
});

export type ProviderCheck = z.infer<typeof ProviderCheckSchema>;

/**
 * Aggregate provider checks for plan explain output.
 */
export const ProviderChecksSchema = z.object({
  overallStatus: z.enum(["healthy", "degraded", "unhealthy", "unknown"]),
  totalProviders: z.number().int().nonnegative(),
  healthyProviders: z.number().int().nonnegative(),
  degradedProviders: z.number().int().nonnegative(),
  unhealthyProviders: z.number().int().nonnegative(),
  checks: z.array(ProviderCheckSchema),
  missingCapabilities: z.array(z.string().min(1)),
});

export type ProviderChecks = z.infer<typeof ProviderChecksSchema>;

// ---------------------------------------------------------------------------
// Gate Checks
// ---------------------------------------------------------------------------

/**
 * Individual gate check result.
 */
export const GateCheckSchema = z.object({
  gateId: z.string().min(1),
  gateKind: z.string().min(1),
  phase: z.string().min(1),
  status: z.enum(["pending", "passed", "failed", "skipped", "blocked"]),
  message: z.string(),
  evaluatedAt: z.string().datetime(),
  policyPack: z.string().optional(),
  required: z.boolean(),
});

export type GateCheck = z.infer<typeof GateCheckSchema>;

/**
 * Aggregate gate checks for plan explain output.
 */
export const GateChecksSchema = z.object({
  overallStatus: z.enum(["passed", "failed", "blocked", "pending"]),
  totalGates: z.number().int().nonnegative(),
  passedGates: z.number().int().nonnegative(),
  failedGates: z.number().int().nonnegative(),
  blockedGates: z.number().int().nonnegative(),
  pendingGates: z.number().int().nonnegative(),
  checks: z.array(GateCheckSchema),
  blockingGates: z.array(z.string().min(1)),
});

export type GateChecks = z.infer<typeof GateChecksSchema>;

// ---------------------------------------------------------------------------
// Artifact Expectations
// ---------------------------------------------------------------------------

/**
 * Artifact expectation with validation.
 */
export const ArtifactExpectationSchema = z.object({
  artifactId: z.string().min(1),
  artifactKind: z.string().min(1),
  required: z.boolean(),
  expectedPath: z.string().optional(),
  expectedFingerprint: z.string().optional(),
  status: z.enum(["pending", "available", "missing", "invalid"]),
  actualPath: z.string().optional(),
  actualFingerprint: z.string().optional(),
  validatedAt: z.string().datetime().optional(),
  validationMessage: z.string().optional(),
});

export type ArtifactExpectation = z.infer<typeof ArtifactExpectationSchema>;

/**
 * Aggregate artifact expectations for plan explain output.
 */
export const ArtifactExpectationsSchema = z.object({
  totalArtifacts: z.number().int().nonnegative(),
  availableArtifacts: z.number().int().nonnegative(),
  missingArtifacts: z.number().int().nonnegative(),
  invalidArtifacts: z.number().int().nonnegative(),
  expectations: z.array(ArtifactExpectationSchema),
  missingRequired: z.array(z.string().min(1)),
});

export type ArtifactExpectations = z.infer<typeof ArtifactExpectationsSchema>;

// ---------------------------------------------------------------------------
// Approval Policy
// ---------------------------------------------------------------------------

/**
 * Approval policy configuration.
 */
export const ApprovalPolicySchema = z.object({
  policyId: z.string().min(1),
  policyKind: z.enum(["manual", "automatic", "hybrid"]),
  requiresApproval: z.boolean(),
  approvers: z.array(z.string()).optional(),
  approvalGroups: z.array(z.string()).optional(),
  quorum: z.number().int().min(1).optional(),
  timeoutMs: z.number().int().nonnegative().optional(),
  escalationPolicy: z.string().optional(),
});

export type ApprovalPolicy = z.infer<typeof ApprovalPolicySchema>;

/**
 * Approval status for plan explain output.
 */
export const ApprovalStatusSchema = z.object({
  policy: ApprovalPolicySchema,
  status: z.enum(["pending", "approved", "rejected", "expired"]),
  requestedAt: z.string().datetime(),
  approvedBy: z.array(z.string()).optional(),
  rejectedBy: z.array(z.string()).optional(),
  rejectionReason: z.string().optional(),
  expiresAt: z.string().datetime().optional(),
});

export type ApprovalStatus = z.infer<typeof ApprovalStatusSchema>;

// ---------------------------------------------------------------------------
// Environment Preflight
// ---------------------------------------------------------------------------

/**
 * Environment preflight check result.
 */
export const EnvironmentPreflightCheckSchema = z.object({
  checkId: z.string().min(1),
  checkKind: z.string().min(1),
  status: z.enum(["passed", "failed", "warning", "skipped"]),
  message: z.string(),
  checkedAt: z.string().datetime(),
  severity: z.enum(["critical", "high", "medium", "low"]).optional(),
  remediation: z.string().optional(),
});

export type EnvironmentPreflightCheck = z.infer<
  typeof EnvironmentPreflightCheckSchema
>;

/**
 * Aggregate environment preflight for plan explain output.
 */
export const EnvironmentPreflightSchema = z.object({
  environmentName: z.string().min(1),
  environmentTarget: z.string().min(1),
  overallStatus: z.enum(["ready", "not-ready", "degraded", "unknown"]),
  totalChecks: z.number().int().nonnegative(),
  passedChecks: z.number().int().nonnegative(),
  failedChecks: z.number().int().nonnegative(),
  warningChecks: z.number().int().nonnegative(),
  checks: z.array(EnvironmentPreflightCheckSchema),
  blockingIssues: z.array(z.string().min(1)),
  variables: z.record(z.string(), z.string()).optional(),
});

export type EnvironmentPreflight = z.infer<typeof EnvironmentPreflightSchema>;

// ---------------------------------------------------------------------------
// PlanExplain — enhanced plan explain output
// ---------------------------------------------------------------------------

/**
 * PlanExplain provides detailed explanation of a lifecycle plan including
 * dependency graph, provider health, gate status, artifact expectations,
 * approval policy, and environment preflight.
 */
export const PlanExplainSchema = z.object({
  schemaVersion: z.literal("1.0.0"),
  runId: z.string().min(1),
  correlationId: z.string().min(1),
  productUnitId: z.string().min(1),
  phase: z.string().min(1),
  environment: z.string().optional(),
  lifecycleProfile: z.string().min(1),
  generatedAt: z.string().datetime(),

  /**
   * Dependency graph showing step relationships.
   */
  dependencyGraph: DependencyGraphSchema,

  /**
   * Provider health checks.
   */
  providerChecks: ProviderChecksSchema,

  /**
   * Gate evaluation results.
   */
  gateChecks: GateChecksSchema,

  /**
   * Artifact expectations and validation.
   */
  artifactExpectations: ArtifactExpectationsSchema,

  /**
   * Approval policy and status.
   */
  approvalPolicy: ApprovalPolicySchema.optional(),

  /**
   * Approval status if approval is required.
   */
  approvalStatus: ApprovalStatusSchema.optional(),

  /**
   * Environment preflight checks.
   */
  environmentPreflight: EnvironmentPreflightSchema.optional(),

  /**
   * Overall execution readiness.
   */
  overallReadiness: z.enum(["ready", "not-ready", "degraded", "unknown"]),

  /**
   * Blocking reasons if not ready.
   */
  blockingReasons: z.array(z.string()).optional(),

  /**
   * Estimated total duration.
   */
  estimatedTotalDurationMs: z.number().int().nonnegative(),

  /**
   * Warnings that don't block execution.
   */
  warnings: z.array(z.string()).optional(),
});

export type PlanExplain = z.infer<typeof PlanExplainSchema>;

export function parsePlanExplain(input: unknown): PlanExplain {
  return PlanExplainSchema.parse(input);
}

export function isPlanExplain(value: unknown): value is PlanExplain {
  try {
    PlanExplainSchema.parse(value);
    return true;
  } catch {
    return false;
  }
}

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
