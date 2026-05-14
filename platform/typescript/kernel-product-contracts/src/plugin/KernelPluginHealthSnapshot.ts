/**
 * KernelPluginHealthSnapshot - represents the health status of a plugin.
 *
 * Health snapshots provide visibility into plugin execution status,
 * error rates, and performance metrics.
 *
 * @doc.type interface
 * @doc.purpose Plugin health status representation
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

import type { HealthStatus } from '../health/HealthStatus';

/**
 * Represents the health status of a plugin.
 */
export interface KernelPluginHealthSnapshot {
  /**
   * Plugin identifier.
   */
  readonly pluginId: string;

  /**
   * Overall health status of the plugin.
   */
  readonly status: HealthStatus;

  /**
   * Timestamp of the health snapshot.
   */
  readonly timestamp: string;

  /**
   * Number of successful executions since last snapshot.
   */
  readonly successCount: number;

  /**
   * Number of failed executions since last snapshot.
   */
  readonly failureCount: number;

  /**
   * Average execution duration in milliseconds.
   */
  readonly averageDurationMs: number;

  /**
   * Last error message (if any).
   */
  readonly lastError?: string;

  /**
   * Health metrics specific to the plugin.
   */
  readonly metrics: Record<string, number | string>;

  /**
   * Additional health metadata.
   */
  readonly metadata?: Record<string, unknown>;
}

/**
 * Type guard to check if a value is a KernelPluginHealthSnapshot.
 */
export function isKernelPluginHealthSnapshot(value: unknown): value is KernelPluginHealthSnapshot {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const snapshot = value as Record<string, unknown>;
  return (
    typeof snapshot.pluginId === 'string' &&
    typeof snapshot.status === 'string' &&
    typeof snapshot.timestamp === 'string' &&
    typeof snapshot.successCount === 'number' &&
    typeof snapshot.failureCount === 'number' &&
    typeof snapshot.averageDurationMs === 'number' &&
    typeof snapshot.metrics === 'object'
  );
}
