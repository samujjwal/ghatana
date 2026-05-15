/**
 * ProductUnitHealthSnapshot - health snapshot for a ProductUnit.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for ProductUnit lifecycle operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import type { HealthStatus } from "./HealthStatus.js";

/**
 * Surface health status.
 */
export interface SurfaceHealthStatus {
  readonly surfaceId: string;
  readonly status: HealthStatus;
  readonly message: string;
  readonly lastUpdated: string;
}

/**
 * ProductUnit health snapshot.
 */
export interface ProductUnitHealthSnapshot {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Surface health statuses.
   */
  readonly surfaces: readonly SurfaceHealthStatus[];

  /**
   * Lifecycle execution status.
   */
  readonly lifecycleStatus: string;

  /**
   * Last lifecycle run timestamp.
   */
  readonly lastLifecycleRun?: string;

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;

  /**
   * Additional metadata.
   */
  readonly metadata?: Record<string, unknown>;
}
