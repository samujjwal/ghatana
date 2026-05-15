/**
 * LearningHealthSnapshot - health snapshot for learning delta operations.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for learning delta operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import type { HealthStatus } from "./HealthStatus.js";

/**
 * Learning delta status.
 */
export interface LearningDeltaStatus {
  readonly deltaId: string;
  readonly status: HealthStatus;
  readonly promotionStatus: string;
  readonly message: string;
  readonly createdAt: string;
}

/**
 * Learning health snapshot.
 */
export interface LearningHealthSnapshot {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Learning delta statuses.
   */
  readonly learningDeltas: readonly LearningDeltaStatus[];

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}
