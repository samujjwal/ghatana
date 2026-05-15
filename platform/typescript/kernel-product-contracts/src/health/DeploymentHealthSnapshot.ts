/**
 * DeploymentHealthSnapshot - health snapshot for deployments.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for deployment operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import type { HealthStatus } from "./HealthStatus.js";

/**
 * Deployment health status.
 */
export interface DeploymentHealthStatus {
  readonly deploymentId: string;
  readonly environment: string;
  readonly status: HealthStatus;
  readonly message: string;
  readonly deployedAt: string;
  readonly endpoints: readonly string[];
}

/**
 * Deployment health snapshot.
 */
export interface DeploymentHealthSnapshot {
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
   * Deployment health statuses.
   */
  readonly deployments: readonly DeploymentHealthStatus[];

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}
