/**
 * ArtifactHealthSnapshot - health snapshot for artifacts.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for artifact operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import type { HealthStatus } from "./HealthStatus";

/**
 * Artifact health status.
 */
export interface ArtifactHealthStatus {
  readonly artifactId: string;
  readonly artifactName: string;
  readonly version: string;
  readonly status: HealthStatus;
  readonly message: string;
  readonly createdAt: string;
}

/**
 * Artifact health snapshot.
 */
export interface ArtifactHealthSnapshot {
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
   * Artifact health statuses.
   */
  readonly artifacts: readonly ArtifactHealthStatus[];

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}
