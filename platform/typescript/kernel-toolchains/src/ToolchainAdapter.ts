import type {
  ProductLifecyclePhase,
  ProductSurface,
  ProductSurfaceType,
} from '@ghatana/kernel-product-contracts';
import type { ProductLifecycleManifestRefs } from '@ghatana/kernel-lifecycle';

export type {
  ProductLifecyclePhase,
  ProductSurface,
  ProductSurfaceType,
};

export const TOOLCHAIN_EXECUTION_RESULT_SCHEMA_VERSION = '1.0.0' as const;

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
  validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult>;
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
  status: 'succeeded' | 'failed' | 'skipped';

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
  status: 'succeeded' | 'failed' | 'skipped';

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
  status: 'valid' | 'invalid' | 'partial';

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
