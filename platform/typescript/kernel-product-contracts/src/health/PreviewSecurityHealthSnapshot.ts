/**
 * PreviewSecurityHealthSnapshot - health snapshot for preview security operations.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for preview security operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import type { HealthStatus } from "./HealthStatus";

/**
 * Security check status.
 */
export interface SecurityCheckStatus {
  readonly checkId: string;
  readonly checkType: string;
  readonly status: HealthStatus;
  readonly severity: string;
  readonly vulnerabilityCount: number;
  readonly message: string;
  readonly lastChecked: string;
}

/**
 * Preview security health snapshot.
 */
export interface PreviewSecurityHealthSnapshot {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Security check statuses.
   */
  readonly securityChecks: readonly SecurityCheckStatus[];

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}
