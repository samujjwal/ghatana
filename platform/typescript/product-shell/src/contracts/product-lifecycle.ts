/**
 * Product Lifecycle Contract
 * 
 * Defines the contract for product lifecycle operations and status.
 * Consumed by lifecycle UI components to display lifecycle information.
 * 
 * @doc.type module
 * @doc.purpose Product lifecycle contract for UI components
 * @doc.layer platform
 */

/**
 * Lifecycle phases available for products
 */
export type ProductLifecyclePhase =
  | 'dev'
  | 'validate'
  | 'test'
  | 'build'
  | 'package'
  | 'deploy'
  | 'verify'
  | 'promote'
  | 'rollback';

/**
 * Lifecycle phase status
 */
export type LifecyclePhaseStatus = 'pending' | 'in-progress' | 'completed' | 'failed' | 'skipped';

/**
 * Lifecycle phase execution result
 */
export interface LifecyclePhaseResult {
  readonly phase: ProductLifecyclePhase;
  readonly status: LifecyclePhaseStatus;
  readonly startedAt?: string;
  readonly completedAt?: string;
  readonly durationMs?: number;
  readonly error?: string;
  readonly steps?: readonly LifecycleStep[];
}

/**
 * Individual step within a lifecycle phase
 */
export interface LifecycleStep {
  readonly id: string;
  readonly name: string;
  readonly status: LifecyclePhaseStatus;
  readonly startedAt?: string;
  readonly completedAt?: string;
  readonly durationMs?: number;
  readonly error?: string;
}

/**
 * Product lifecycle plan
 */
export interface ProductLifecyclePlan {
  readonly productId: string;
  readonly phase: ProductLifecyclePhase;
  readonly surfaces: readonly string[];
  readonly mode: 'parallel' | 'sequential';
  readonly steps: readonly LifecycleStep[];
  readonly dryRun: boolean;
  readonly generatedAt: string;
}

/**
 * Product lifecycle run history
 */
export interface ProductLifecycleRun {
  readonly runId: string;
  readonly productId: string;
  readonly phase: ProductLifecyclePhase;
  readonly triggeredBy: string;
  readonly startedAt: string;
  readonly completedAt?: string;
  readonly status: LifecyclePhaseStatus;
  readonly results: readonly LifecyclePhaseResult[];
  readonly dryRun: boolean;
}

/**
 * Lifecycle status summary for UI display
 */
export interface ProductLifecycleStatus {
  readonly productId: string;
  readonly currentPhase?: ProductLifecyclePhase;
  readonly phaseStatus: LifecyclePhaseStatus;
  readonly lastRun?: ProductLifecycleRun;
  readonly nextPhase?: ProductLifecyclePhase;
}
