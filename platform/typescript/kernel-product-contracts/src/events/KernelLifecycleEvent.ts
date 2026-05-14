/**
 * KernelLifecycleEvent - base event contract for Kernel lifecycle events.
 *
 * @doc.type interface
 * @doc.purpose Base event contract for Kernel lifecycle operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

/**
 * Base event metadata for all Kernel lifecycle events.
 */
export interface KernelEventMetadata {
  /**
   * Unique event identifier.
   */
  readonly eventId: string;

  /**
   * Schema version for event contract compatibility.
   */
  readonly schemaVersion: string;

  /**
   * Event type identifier.
   */
  readonly eventType: string;

  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Lifecycle run identifier.
   */
  readonly runId: string;

  /**
   * Lifecycle phase (dev, validate, test, build, package, deploy, verify, promote, rollback).
   */
  readonly phase: string;

  /**
   * Event timestamp (ISO 8601).
   */
  readonly timestamp: string;

  /**
   * Event source (e.g., "kernel-lifecycle", "provider-xyz").
   */
  readonly source: string;

  /**
   * Optional tenant identifier for multi-tenant environments.
   */
  readonly tenantId?: string;

  /**
   * Optional workspace identifier.
   */
  readonly workspaceId?: string;

  /**
   * Optional project identifier.
   */
  readonly projectId?: string;

  /**
   * Correlation identifier for tracing related events.
   */
  readonly correlationId: string;
}

/**
 * Kernel lifecycle event.
 */
export interface KernelLifecycleEvent {
  /**
   * Event metadata.
   */
  readonly metadata: KernelEventMetadata;

  /**
   * Event payload (specific to event type).
   */
  readonly payload: Record<string, unknown>;
}
