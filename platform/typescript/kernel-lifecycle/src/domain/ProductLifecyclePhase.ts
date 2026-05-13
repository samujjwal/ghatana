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
  surfaces: Record<string, ProductSurface>;
  phases: Record<string, LifecyclePhaseConfiguration>;
}

/**
 * Lifecycle plan step
 */
export interface LifecyclePlanStep {
  id: string;
  phase: ProductLifecyclePhase;
  surface: string;
  adapter: string;
  description: string;
  dependsOn: string[];
  estimatedDurationMs?: number;
  execution?: {
    command: string;
    args: string[];
    workingDirectory: string;
  };
}

/**
 * Lifecycle plan
 */
export interface LifecyclePlan {
  schemaVersion?: '1.0.0';
  productId: string;
  phase: ProductLifecyclePhase;
  lifecycleProfile?: string;
  environment?: string;
  sourceRef?: string;
  surfaces: string[];
  gates?: ProductGatePlan[];
  steps: LifecyclePlanStep[];
  expectedArtifacts?: ProductExpectedArtifact[];
  outputDirectory?: string;
  estimatedDurationMs: number;
}

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
 * Product lifecycle plan
 */
export interface ProductLifecyclePlan {
  schemaVersion: '1.0.0';
  productId: string;
  phase: ProductLifecyclePhase;
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
  phase: ProductLifecyclePhase;
  surface: string;
  adapter: string;
  description: string;
  dependsOn: string[];
  estimatedDurationMs: number;
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
 */
export interface ProductArtifact {
  id: string;
  surface: string;
  type: string;
  path: string;
  fingerprint: string;
  producedBy: string;
  sizeBytes?: number;
  metadata?: Record<string, unknown>;
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
