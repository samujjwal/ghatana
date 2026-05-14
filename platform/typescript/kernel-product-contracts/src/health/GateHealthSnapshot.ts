/**
 * GateHealthSnapshot - health snapshot for gate evaluation.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for gate evaluation operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import type { HealthStatus } from "./HealthStatus";

/**
 * Gate evaluation status.
 */
export interface GateEvaluationStatus {
  readonly gateId: string;
  readonly passed: boolean;
  readonly reason: string;
  readonly evaluatedAt: string;
  readonly duration: number;
}

/**
 * Gate health snapshot.
 */
export interface GateHealthSnapshot {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Lifecycle run identifier.
   */
  readonly runId: string;

  /**
   * Phase identifier.
   */
  readonly phase: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Gate evaluation statuses.
   */
  readonly gates: readonly GateEvaluationStatus[];

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}
