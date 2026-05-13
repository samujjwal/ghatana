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
 * Lifecycle phases
 */
export type ProductLifecyclePhase =
  | 'create'
  | 'bootstrap'
  | 'dev'
  | 'validate'
  | 'test'
  | 'build'
  | 'package'
  | 'release'
  | 'deploy'
  | 'verify'
  | 'promote'
  | 'rollback'
  | 'operate'
  | 'retire';

/**
 * Surface types
 */
export type ProductSurfaceType =
  | 'backend-api'
  | 'web'
  | 'worker'
  | 'operator'
  | 'mobile-ios'
  | 'mobile-android'
  | 'sdk'
  | 'domain-pack';

/**
 * Product surface
 */
export interface ProductSurface {
  type: ProductSurfaceType;
  adapter: string;
  path: string;
  [key: string]: unknown;
}

/**
 * Context provided to an adapter for planning and execution.
 */
export interface ToolchainAdapterContext {
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
