/**
 * KernelHealthEvent - event contract for health check events.
 *
 * @doc.type interface
 * @doc.purpose Event contract for health check operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

import type { KernelEventMetadata } from "./KernelLifecycleEvent.js";

/**
 * Health check event payload.
 */
export interface HealthEventPayload {
  /**
   * Check identifier.
   */
  readonly checkId: string;

  /**
   * Check name.
   */
  readonly checkName: string;

  /**
   * Health status.
   */
  readonly status: "healthy" | "degraded" | "blocked" | "failed" | "skipped" | "unknown";

  /**
   * Health check message.
   */
  readonly message: string;

  /**
   * Deployment identifier (if applicable).
   */
  readonly deploymentId?: string;

  /**
   * Environment (if applicable).
   */
  readonly environment?: string;

  /**
   * Check duration in milliseconds.
   */
  readonly duration: number;

  /**
   * Index signature for additional properties.
   */
  readonly [key: string]: unknown;
}

/**
 * Health check event.
 */
export interface KernelHealthEvent {
  /**
   * Event metadata.
   */
  readonly metadata: KernelEventMetadata;

  /**
   * Health-specific payload.
   */
  readonly payload: HealthEventPayload;
}
