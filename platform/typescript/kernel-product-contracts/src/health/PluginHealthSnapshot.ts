/**
 * PluginHealthSnapshot - health snapshot for plugins.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for plugin operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import type { HealthStatus } from "./HealthStatus";

/**
 * Plugin health status.
 */
export interface PluginHealthStatus {
  readonly pluginId: string;
  readonly pluginKind: string;
  readonly status: HealthStatus;
  readonly message: string;
  readonly lastExecuted: string;
}

/**
 * Plugin health snapshot.
 */
export interface PluginHealthSnapshot {
  /**
   * ProductUnit identifier (optional for global plugins).
   */
  readonly productUnitId?: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Plugin health statuses.
   */
  readonly plugins: readonly PluginHealthStatus[];

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}
