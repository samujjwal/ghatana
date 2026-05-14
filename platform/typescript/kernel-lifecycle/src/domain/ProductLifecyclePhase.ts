/**
 * Product lifecycle phases
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
 * Product surface types
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
 * Product surface definition
 */
export interface ProductSurface {
  type: ProductSurfaceType;
  adapter: string;
  path: string;
  implementationStatus: 'implemented' | 'planned' | 'backend-only';
  packagePath?: string;
  [key: string]: unknown;
}

/**
 * Lifecycle phase configuration
 */
export interface LifecyclePhaseConfiguration {
  defaultSurfaces: string[];
  mode: 'parallel' | 'sequential';
}

/**
 * Product lifecycle configuration
 */
export interface ProductLifecycleConfiguration {
  enabled: boolean;
  phases: Record<string, LifecyclePhaseConfiguration>;
}

/**
 * Product kernel product configuration
 */
export interface KernelProductConfiguration {
  productId: string;
  lifecycleProfile: string;
  allowExperimentalAdapters?: boolean;
  surfaces: Record<string, ProductSurface>;
  phases: Record<string, LifecyclePhaseConfiguration>;
  package?: Record<string, PackageSurfaceConfig>;
  deployment?: Record<string, DeploymentEnvironmentConfig>;
  verify?: Record<string, VerifyEnvironmentConfig>;
  gates?: Record<string, string[]>;
  artifacts?: Record<string, Record<string, ArtifactConfig>>;
}

/**
 * Package surface configuration (adapter, dockerfile, image, etc.)
 */
export interface PackageSurfaceConfig {
  adapter: string;
  context?: string;
  dockerfile?: string;
  image: string;
  tag?: string;
  buildArgs?: Record<string, string>;
}

/**
 * Deployment environment configuration
 */
export interface DeploymentEnvironmentConfig {
  adapter: string;
  target: string;
  composeFile?: string;
  envFile?: string;
  envExampleFile?: string;
  requireEnvFile?: boolean;
}

/**
 * Verify environment configuration
 */
export interface VerifyEnvironmentConfig {
  adapter: string;
  healthChecks?: Record<string, HealthCheckConfig>;
}

/**
 * Health check configuration
 */
export interface HealthCheckConfig {
  type: 'http' | 'tcp';
  url?: string;
  host?: string;
  port?: number;
  path?: string;
  retries?: number;
  intervalMs?: number;
  timeoutMs?: number;
}

/**
 * Artifact configuration
 */
export interface ArtifactConfig {
  type: string;
  packaging?: string;
  required?: boolean;
  paths?: string[];
}

/**
 * Step kind discriminant — identifies what kind of work a plan step represents.
 */
export type LifecycleStepKind =
  | 'gate'
  | 'surface'
  | 'package'
  | 'deploy'
  | 'verify'
  | 'release'
  | 'promotion'
  | 'rollback';

/**
 * Adapter context payload passed to each step so the executor can build the
 * correct adapter call without re-reading config files.
 */
export interface LifecycleStepAdapterContext {
  surfaceConfig?: Record<string, unknown>;
  packageConfig?: Record<string, unknown>;
  deploymentConfig?: Record<string, unknown>;
  artifactConfig?: Record<string, unknown>;
  environmentConfig?: Record<string, unknown>;
}

/**
 * Lifecycle plan step
 * @deprecated Use ProductLifecycleStep directly. This alias is kept for backward compatibility.
 */
export type LifecyclePlanStep = ProductLifecycleStep;

/**
 * Lifecycle plan
 * @deprecated Use ProductLifecyclePlan directly. This alias is kept for backward compatibility.
 */
export type LifecyclePlan = ProductLifecyclePlan;

/**
 * Execution context
 */
export interface ExecutionContext {
  productId: string;
  phase: ProductLifecyclePhase;
  surface: string;
  environment?: string;
  sourceRef?: string;
  outputDirectory: string;
  dryRun: boolean;
  logger: ExecutionLogger;
}

/**
 * Execution logger
 */
export interface ExecutionLogger {
  info(message: string, meta?: Record<string, unknown>): void;
  warn(message: string, meta?: Record<string, unknown>): void;
  error(message: string, meta?: Record<string, unknown>): void;
  debug(message: string, meta?: Record<string, unknown>): void;
}

/**
 * Execution result
 */
export interface ExecutionResult {
  status: 'succeeded' | 'failed' | 'skipped';
  steps: ExecutionStepResult[];
  artifacts: string[];
  durationMs: number;
  failure?: {
    stepId: string;
    message: string;
    cause?: string;
  };
}

/**
 * Execution step result
 */
export interface ExecutionStepResult {
  stepId: string;
  status: 'succeeded' | 'failed' | 'skipped';
  exitCode?: number;
  stdout?: string;
  stderr?: string;
  durationMs: number;
}

/**
 * Validation error
 */
export interface ValidationError {
  path: string;
  message: string;
}

/**
 * Product lifecycle plan — canonical plan type (consolidates LifecyclePlan usage)
 */
export interface ProductLifecyclePlan {
  schemaVersion: '1.0.0';
  runId: string;
  productId: string;
  phase: ProductLifecyclePhase;
  phaseMode: 'parallel' | 'sequential' | 'dag';
  lifecycleProfile: string;
  environment?: string;
  sourceRef?: string;
  surfaces: ProductSurfaceSelection[];
  gates: ProductGatePlan[];
  steps: ProductLifecycleStep[];
  expectedArtifacts: ProductExpectedArtifact[];
  outputDirectory: string;
  estimatedDurationMs: number;
}

/**
 * Product lifecycle result
 */
export interface ProductLifecycleResult {
  schemaVersion: '1.0.0';
  runId: string;
  productId: string;
  phase: ProductLifecyclePhase;
  status: 'succeeded' | 'failed' | 'skipped';
  startedAt: string;
  completedAt: string;
  steps: ProductLifecycleStepResult[];
  gates: ProductGateResult[];
  artifacts: ProductArtifact[];
  outputDirectory: string;
  failure?: {
    stepId: string;
    message: string;
    cause?: string;
  };
}

/**
 * Product lifecycle step
 */
export interface ProductLifecycleStep {
  id: string;
  stepKind: LifecycleStepKind;
  phase: ProductLifecyclePhase;
  surface: string;
  adapter: string;
  description: string;
  dependsOn: string[];
  estimatedDurationMs: number;
  adapterContext?: LifecycleStepAdapterContext;
  execution?: {
    command: string;
    args: string[];
    workingDirectory: string;
  };
}

/**
 * Product lifecycle step result
 */
export interface ProductLifecycleStepResult {
  stepId: string;
  status: 'succeeded' | 'failed' | 'skipped';
  exitCode?: number;
  stdout?: string;
  stderr?: string;
  durationMs: number;
}

/**
 * Product lifecycle event
 */
export interface ProductLifecycleEvent {
  eventId: string;
  productId: string;
  phase: ProductLifecyclePhase;
  eventType: 'started' | 'completed' | 'failed' | 'skipped';
  timestamp: string;
  metadata?: Record<string, unknown>;
}

/**
 * Product artifact
 *
 * For container image artifacts, populate the optional image-specific fields.
 */
export interface ProductArtifact {
  id: string;
  surface: string;
  type: string;
  /** File-system path for file-based artifacts, or image ref for container images. */
  path: string;
  fingerprint: string;
  producedBy: string;
  sizeBytes?: number;
  metadata?: Record<string, unknown>;
  /** Container image reference (e.g. "registry/image:tag"). Set when type === 'container-image'. */
  image?: string;
  /** Container image tag. */
  tag?: string;
  /** Container image digest (sha256:...). */
  digest?: string;
  /** Local Docker image ID (short hash). */
  localImageId?: string;
}

/**
 * Product environment
 */
export interface ProductEnvironment {
  id: string;
  displayName: string;
  deploymentTarget: string;
  secretsProvider: string;
  configProvider: string;
  approvalRequired: boolean;
  requiredGates: string[];
  observabilityProfile: string;
  rollbackPolicy: string;
  promotionPolicy: string;
}

/**
 * Product gate
 */
export interface ProductGate {
  id: string;
  name: string;
  description: string;
  required: boolean;
  phase: ProductLifecyclePhase;
  implementation: string;
}

/**
 * Product gate plan
 */
export interface ProductGatePlan {
  gateId: string;
  gateName: string;
  required: boolean;
  phase: ProductLifecyclePhase;
  status: 'pending' | 'passed' | 'failed' | 'skipped';
}

/**
 * Product gate result
 */
export interface ProductGateResult {
  gateId: string;
  gateName: string;
  status: 'passed' | 'failed' | 'skipped';
  checkedAt: string;
  details?: string;
}

/**
 * Product surface selection
 */
export interface ProductSurfaceSelection {
  surface: string;
  type: ProductSurfaceType;
  adapter: string;
  config: Record<string, unknown>;
}

/**
 * Product expected artifact
 */
export interface ProductExpectedArtifact {
  surface: string;
  type: string;
  required: boolean;
}

/**
 * Product failure policy
 */
export interface ProductFailurePolicy {
  strategy: 'fail-closed' | 'fail-open' | 'continue-on-error';
  retryConfig?: {
    maxRetries: number;
    backoffMs: number;
  };
  notifyOnFailure?: boolean;
}
