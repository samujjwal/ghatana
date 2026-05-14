/**
 * LifecycleHealthSnapshot - health snapshot for lifecycle execution.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for lifecycle execution operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import type { HealthStatus } from "./HealthStatus";

/**
 * Phase health status.
 */
export interface PhaseHealthStatus {
  readonly phase: string;
  readonly status: HealthStatus;
  readonly message: string;
  readonly duration: number;
  readonly completedAt: string;
}

/**
 * Lifecycle health snapshot.
 */
export interface LifecycleHealthSnapshot {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Lifecycle run identifier.
   */
  readonly runId: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Phase health statuses.
   */
  readonly phases: readonly PhaseHealthStatus[];

  /**
   * Current phase.
   */
  readonly currentPhase?: string;

  /**
   * Total execution duration.
   */
  readonly totalDuration: number;

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}
