import type {
  ProductLifecyclePhase,
  ProductSurface,
  ProductSurfaceType,
  LifecycleFailureClassifier,
} from "@ghatana/kernel-product-contracts";
import type { ProductLifecycleManifestRefs } from "@ghatana/kernel-lifecycle";

export type { ProductLifecyclePhase, ProductSurface, ProductSurfaceType, LifecycleFailureClassifier };

export const TOOLCHAIN_EXECUTION_RESULT_SCHEMA_VERSION = "1.0.0" as const;

export type ToolchainExecutionResultSchemaVersion =
  typeof TOOLCHAIN_EXECUTION_RESULT_SCHEMA_VERSION;

/**
 * A toolchain adapter abstracts a specific tool (Gradle, pnpm, Docker, etc.)
 * for executing lifecycle phases on product surfaces.
 */
export interface ToolchainAdapter {
  /**
   * Unique identifier for this adapter (e.g., "gradle-java-service").
   */
  readonly id: string;

  /**
   * Lifecycle phases this adapter supports (e.g., ["dev", "validate", "test", "build", "package"]).
   */
  readonly supportedPhases: ProductLifecyclePhase[];

  /**
   * Surface types this adapter supports (e.g., ["backend-api", "worker", "operator"]).
   */
  readonly supportedSurfaceTypes: ProductSurfaceType[];

  /**
   * Run preflight checks to validate environment readiness before execution.
   */
  preflight(context: ToolchainAdapterContext): Promise<AdapterPreflightResult>;

  /**
   * Generate an execution plan for the given context without executing.
   * This is used for dry-run and planning.
   */
  plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]>;

  /**
   * Execute the planned steps and return the result.
   */
  execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult>;

  /**
   * Validate that the expected outputs were produced by execution.
   */
  validateOutputs(
    context: ToolchainAdapterContext,
  ): Promise<ToolchainOutputValidationResult>;

  /**
   * Classify execution failures for observability and remediation.
   */
  classifyFailure(
    error: Error,
    context: ToolchainAdapterContext,
  ): Promise<LifecycleFailureClassifier>;
}

/**
 * Context provided to an adapter for planning and execution.
 */
export interface ToolchainAdapterContext {
  /**
   * Kernel lifecycle run identifier for evidence correlation.
   */
  runId?: string;

  /**
   * Cross-provider correlation identifier for logs, events, and manifests.
   */
  correlationId?: string;

  /**
   * Product identifier.
   */
  productId: string;

  /**
   * Lifecycle phase being executed.
   */
  phase: ProductLifecyclePhase;

  /**
   * Surface being processed.
   */
  surface: ProductSurface;

  /**
   * Environment (for deploy/verify/promote/rollback phases).
   */
  environment?: string;

  /**
   * Source reference (git branch, commit, etc.).
   */
  sourceRef?: string;

  /**
   * Dry-run mode: plan only, do not execute.
   */
  dryRun: boolean;

  /**
   * Surface-specific configuration from the product manifest.
   */
  surfaceConfig: Record<string, unknown>;

  /**
   * Phase-specific configuration from the lifecycle profile.
   */
  phaseConfig: Record<string, unknown>;

  /**
   * Logger for structured output.
   */
  logger: AdapterLogger;

  /**
   * Build metadata
   */
  metadata?: BuildMetadata;

  /**
   * Output directory for artifacts and results.
   */
  outputDir?: string;
}

/**
 * Logger interface for adapter execution
 */
export interface AdapterLogger {
  info(message: string): void;
  warn(message: string): void;
  error(message: string): void;
  debug(message: string): void;
}

/**
 * Build metadata
 */
export interface BuildMetadata {
  version: string;
  buildNumber?: string;
  gitCommit?: string;
  gitBranch?: string;
}

/**
 * A single step in an adapter's execution plan.
 */
export interface ToolchainPlanStep {
  /**
   * Step identifier (unique within the plan).
   */
  id: string;

  /**
   * Human-readable description.
   */
  description: string;

  /**
   * Command to execute (as an argument array, never a shell string).
   */
  command: string[];

  /**
   * Working directory for the command.
   */
  workingDirectory: string;

  /**
   * Environment variables for the command.
   */
  env?: Record<string, string>;

  /**
   * Expected outputs from this step.
   */
  expectedOutputs?: string[];

  /**
   * Whether this step can be executed in parallel with others.
   */
  parallelizable?: boolean;

  /**
   * Dependencies on other steps (by step ID).
   */
  dependsOn?: string[];
}

/**
 * Result of executing an adapter's plan.
 */
export interface ToolchainExecutionResult {
  /**
   * Schema version for this adapter result contract.
   */
  schemaVersion?: ToolchainExecutionResultSchemaVersion;

  /**
   * Kernel lifecycle run identifier echoed from context.
   */
  runId?: string;

  /**
   * Cross-provider correlation identifier echoed from context.
   */
  correlationId?: string;

  /**
   * Overall status.
   */
  status: "succeeded" | "failed" | "skipped";

  /**
   * Step-level results.
   */
  steps: ToolchainStepResult[];

  /**
   * Artifacts produced (paths relative to output directory).
   */
  artifacts: string[];

  /**
   * Test results (if applicable).
   */
  testResults?: ToolchainTestResults;

  /**
   * Coverage results (if applicable).
   */
  coverageResults?: ToolchainCoverageResults;

  /**
   * Execution duration in milliseconds.
   */
  durationMs: number;

  /**
   * Failure information if status is 'failed'.
   */
  failure?: {
    stepId: string;
    message: string;
    cause?: string;
  };

  warnings?: string[];

  stdout?: string;

  stderr?: string;

  manifestRefs?: ProductLifecycleManifestRefs;

  evidenceRefs?: string[];

  observability?: ToolchainExecutionObservability;

  /**
   * Additional adapter-specific metadata for production evidence.
   */
  metadata?: Record<string, unknown>;
}

/**
 * Stable execution telemetry that is safe to persist as lifecycle evidence.
 */
export interface ToolchainExecutionObservability {
  commandId: string;
  durationMs: number;
  exitCode?: number;
  stdoutBytes: number;
  stderrBytes: number;
  stdoutTruncated: boolean;
  stderrTruncated: boolean;
  outputLimitBytes: number;
}

/**
 * Result of executing a single step.
 */
export interface ToolchainStepResult {
  /**
   * Step identifier.
   */
  stepId: string;

  /**
   * Execution status.
   */
  status: "succeeded" | "failed" | "skipped";

  /**
   * Exit code (0 for success).
   */
  exitCode?: number;

  /**
   * Standard output (truncated if large).
   */
  stdout?: string;

  /**
   * Standard error (truncated if large).
   */
  stderr?: string;

  /**
   * Execution duration in milliseconds.
   */
  durationMs: number;
}

/**
 * Test results
 */
export interface ToolchainTestResults {
  tests: number;
  failures: number;
  skipped: number;
  durationMs: number;
}

/**
 * Coverage results
 */
export interface ToolchainCoverageResults {
  lineCoverage: number;
  branchCoverage: number;
  instructionCoverage: number;
}

/**
 * Result of validating adapter outputs.
 */
export interface ToolchainOutputValidationResult {
  /**
   * Overall validation status.
   */
  status: "valid" | "invalid" | "partial";

  /**
   * Validation errors (if any).
   */
  errors: ValidationError[];

  /**
   * Missing expected artifacts (if any).
   */
  missingArtifacts: string[];

  /**
   * Unexpected artifacts found (if any).
   */
  unexpectedArtifacts: string[];
}

/**
 * Validation error
 */
export interface ValidationError {
  path: string;
  message: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase 2.3 additions: AdapterPreflightResult, AdapterSafetyPolicy,
//                     AdapterFailureClassification
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Default preflight result for adapters that haven't implemented custom preflight.
 */
export function createDefaultPreflightResult(): AdapterPreflightResult {
  return {
    status: "ready",
    checks: [
      {
        checkId: "default-preflight",
        checkName: "Default Preflight",
        status: "skipped",
        message: "Adapter does not implement custom preflight checks",
        checkedAt: new Date().toISOString(),
      },
    ],
    blockingIssues: [],
    warnings: ["Adapter does not implement custom preflight checks"],
  };
}

/**
 * Default failure classifier for adapters that haven't implemented custom classification.
 */
export function createDefaultFailureClassifier(
  error: Error,
  adapterId: string,
): LifecycleFailureClassifier {
  const errorMessage = error.message.toLowerCase();
  
  // Determine category based on error message
  let category: LifecycleFailureClassifier["category"] = "unknown";
  if (errorMessage.includes("permission") || errorMessage.includes("access denied")) {
    category = "security";
  } else if (errorMessage.includes("network") || errorMessage.includes("connection")) {
    category = "infrastructure";
  } else if (errorMessage.includes("timeout")) {
    category = "infrastructure";
  } else if (errorMessage.includes("config") || errorMessage.includes("configuration")) {
    category = "config";
  } else if (errorMessage.includes("command") || errorMessage.includes("executable")) {
    category = "adapter";
  }
  
  // Determine severity
  let severity: LifecycleFailureClassifier["severity"] = "medium";
  if (category === "security" || category === "config") {
    severity = "high";
  } else if (category === "infrastructure") {
    severity = "medium";
  }
  
  return {
    category,
    severity,
    retryable: category === "infrastructure",
    requiresHumanIntervention: category === "security" || category === "config",
    remediationSteps: [
      "Check adapter configuration",
      "Verify required dependencies are installed",
      "Review error message for specific failure details",
    ],
    relatedFailureCodes: [`${adapterId}-failure`],
    component: adapterId,
  };
}

/**
 * Result of adapter preflight checks before execution.
 */
export interface AdapterPreflightResult {
  /**
   * Overall preflight status.
   */
  status: "ready" | "not-ready" | "degraded" | "blocked";

  /**
   * Individual preflight checks performed.
   */
  checks: AdapterPreflightCheck[];

  /**
   * Blocking issues that prevent execution.
   */
  blockingIssues: string[];

  /**
   * Warnings that don't block execution but should be surfaced.
   */
  warnings: string[];
}

/**
 * Individual preflight check result.
 */
export interface AdapterPreflightCheck {
  /**
   * Check identifier.
   */
  checkId: string;

  /**
   * Check name/description.
   */
  checkName: string;

  /**
   * Check status.
   */
  status: "passed" | "failed" | "warning" | "skipped";

  /**
   * Human-readable message.
   */
  message: string;

  /**
   * Check severity (for failed/warning checks).
   */
  severity?: "critical" | "high" | "medium" | "low";

  /**
   * Suggested remediation steps.
   */
  remediation?: string[];

  /**
   * When the check was performed.
   */
  checkedAt: string;
}

/**
 * Safety policy for adapter execution.
 */
export interface AdapterSafetyPolicy {
  /**
   * Whether the adapter is allowed to execute in the current context.
   */
  allowed: boolean;

  /**
   * Safety level classification.
   */
  safetyLevel: ToolchainSafetyLevel;

  /**
   * Required approvals before execution.
   */
  requiredApprovals: readonly string[];

  /**
   * Safety constraints.
   */
  constraints: AdapterSafetyConstraint[];

  /**
   * Risk assessment for this execution.
   */
  riskAssessment: AdapterRiskAssessment;
}

/**
 * Individual safety constraint.
 */
export interface AdapterSafetyConstraint {
  /**
   * Constraint type.
   */
  type: "environment" | "network" | "filesystem" | "resource" | "permission";

  /**
   * Constraint description.
   */
  description: string;

  /**
   * Whether this constraint is enforced (block) or advisory (warn).
   */
  enforced: boolean;

  /**
   * Constraint value or pattern.
   */
  value?: string;
}

/**
 * Risk assessment for adapter execution.
 */
export interface AdapterRiskAssessment {
  /**
   * Overall risk level.
   */
  riskLevel: "low" | "medium" | "high" | "critical";

  /**
   * Risk factors identified.
   */
  riskFactors: readonly string[];

  /**
   * Mitigation strategies applied or recommended.
   */
  mitigations: readonly string[];
}

// ─────────────────────────────────────────────────────────────────────────────
// §2.5 additions: ToolchainSafetyLevel, ToolchainAdapterCapability,
//                 ToolchainOutputContract, ToolchainExecutionRequest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Classifies the safety and readiness of an adapter for kernel-managed execution.
 *
 * - `safe`      — adapter is fully tested, output-validated, and allowed by default.
 * - `blocked`   — adapter must not execute; returns a `not-ready` result.
 * - `not-ready` — adapter is declared but lacks tests, output contracts, or approvals.
 * - `planned`   — adapter is architecturally scoped but not yet implemented.
 */
export type ToolchainSafetyLevel = "safe" | "blocked" | "not-ready" | "planned";

/**
 * Describes what an adapter requires and produces for a given lifecycle phase.
 * Used to validate that all inputs are available before execution and that
 * all expected outputs are present after execution.
 */
export interface ToolchainOutputContract {
  /** Lifecycle phase this contract applies to. */
  readonly phase: ProductLifecyclePhase;
  /** Relative paths of files that must exist in the output directory after execution. */
  readonly expectedOutputs: readonly string[];
  /** Human-readable description of what these outputs represent. */
  readonly description?: string;
}

/**
 * Rich metadata describing an adapter's capabilities, readiness, and boundaries.
 * Extends the runtime adapter interface with governance information.
 */
export interface ToolchainAdapterCapability {
  /** Unique adapter identifier. */
  readonly adapterId: string;
  /** Lifecycle phases supported. */
  readonly supportedPhases: readonly ProductLifecyclePhase[];
  /** Surface types supported. */
  readonly supportedSurfaceTypes: readonly ProductSurfaceType[];
  /** Inputs required before execution (e.g., environment variable names, file paths). */
  readonly requiredInputs: readonly string[];
  /** Output contracts per phase. */
  readonly outputContracts: readonly ToolchainOutputContract[];
  /** Safety classification. */
  readonly safetyLevel: ToolchainSafetyLevel;
  /** Whether the adapter has passing end-to-end tests. */
  readonly testStatus: "passing" | "failing" | "no-tests";
  /** Implementation status: fully implemented, partial, or stub. */
  readonly implementationStatus: "complete" | "partial" | "stub";
  /** Whether the adapter is allowed to execute without an explicit feature-flag approval. */
  readonly allowedByDefault: boolean;
  /** If blocked or not-ready, the reason code for observability. */
  readonly blockedReasonCode?: string;
}

/**
 * Typed execution request passed to {@link ToolchainAdapter.execute}.
 * Wraps {@link ToolchainAdapterContext} with additional kernel-level fields.
 */
export interface ToolchainExecutionRequest {
  /** Kernel lifecycle run identifier. */
  readonly runId: string;
  /** Cross-provider correlation identifier. */
  readonly correlationId: string;
  /** Product being processed. */
  readonly productId: string;
  /** Lifecycle phase. */
  readonly phase: ProductLifecyclePhase;
  /** Product surface. */
  readonly surface: ProductSurface;
  /** Target environment (required for deploy/verify/promote/rollback). */
  readonly environment?: string;
  /** Source reference (git branch or commit). */
  readonly sourceRef?: string;
  /** Whether to plan only and not execute side effects. */
  readonly dryRun: boolean;
  /** Surface-specific configuration. */
  readonly surfaceConfig: Readonly<Record<string, unknown>>;
  /** Phase-specific configuration. */
  readonly phaseConfig: Readonly<Record<string, unknown>>;
  /** Logger for structured output. */
  readonly logger: AdapterLogger;
  /** Optional output directory. */
  readonly outputDir?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// §2.4 additions: ToolchainTimeoutPolicy, ToolchainRetryPolicy,
//                 ToolchainEnvironmentPolicy, ToolchainOutputValidationPolicy,
//                 ToolchainAdapterRegistryEntry
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Timeout configuration for an adapter's plan and execute operations.
 */
export interface ToolchainTimeoutPolicy {
  /** Timeout for plan (dry-run) operations in milliseconds. */
  readonly planMs: number;
  /** Timeout for full execution in milliseconds. */
  readonly executeMs: number;
  /** Default timeout used when phase-specific timeout is not defined. */
  readonly defaultMs: number;
}

/**
 * Retry policy for an adapter's execution.
 */
export interface ToolchainRetryPolicy {
  /** Maximum number of execution attempts (including the first). */
  readonly maxAttempts: number;
  /** Whether the adapter supports retrying on failure. */
  readonly retryable: boolean;
  /** Exit codes on which a retry should be attempted. */
  readonly retryOnExitCodes: readonly number[];
}

/**
 * Declares which environments this adapter may target.
 */
export interface ToolchainEnvironmentPolicy {
  /** Environments this adapter is allowed to target. */
  readonly allowedEnvironments: readonly string[];
  /** Environments this adapter must never target without an explicit override. */
  readonly blockedEnvironments: readonly string[];
  /** Whether targeting any allowed environment requires explicit approval. */
  readonly requiresEnvironmentApproval: boolean;
}

/**
 * Governs how the adapter validates its output after execution.
 */
export interface ToolchainOutputValidationPolicy {
  /** Whether to run output validation automatically after successful execution. */
  readonly validateAfterExecute: boolean;
  /** Artifact types that must be present in the output directory after execution. */
  readonly requiredArtifactTypes: readonly string[];
  /** Whether to fail the step if any required artifact is missing. */
  readonly failOnMissingArtifacts: boolean;
  /** Whether to fail the step if unexpected artifacts are found. */
  readonly failOnUnexpectedArtifacts: boolean;
}

/**
 * Complete registry entry for a toolchain adapter, combining governance
 * metadata with implementation details. Mirrors the JSON fields in
 * config/toolchain-adapter-registry.json.
 *
 * `implemented` adapters must supply non-null timeout, retryPolicy,
 * environmentPolicy, and outputValidation.  `planned` adapters may use null.
 */
export interface ToolchainAdapterRegistryEntry {
  readonly adapterId: string;
  readonly kind: string;
  readonly supportedPhases: readonly ProductLifecyclePhase[];
  readonly supportedSurfaceTypes: readonly ProductSurfaceType[];
  readonly safeForDefault: boolean;
  readonly requiresApprovalForProduction: boolean;
  readonly outputs: readonly string[];
  readonly tests: readonly string[];
  readonly status: "implemented" | "partial" | "planned";
  readonly timeout: ToolchainTimeoutPolicy | null;
  readonly retryPolicy: ToolchainRetryPolicy | null;
  readonly environmentPolicy: ToolchainEnvironmentPolicy | null;
  readonly outputValidation: ToolchainOutputValidationPolicy | null;
  readonly safetyPolicy: AdapterSafetyPolicy | null;
  readonly preflightChecks: readonly string[] | null;
}
