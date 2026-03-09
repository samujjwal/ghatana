/**
 * @fileoverview Processor types and interfaces for event transformation pipeline.
 *
 * Processors are pluggable transformation units that can modify, filter, enrich,
 * or validate events as they flow through the connector system. They enable
 * configuration-driven data processing without writing custom code.
 *
 * @module processors/types
 * @since 1.1.0
 */

import { Event } from '../types';

/**
 * Processor execution context with metadata and utilities.
 *
 * Provides processors with access to connector state, metrics, and utilities
 * for enhanced processing capabilities.
 */
export interface ProcessorContext {
  /**
   * Unique identifier of the connector executing this processor.
   */
  connectorId: string;

  /**
   * Type of connector (source/sink).
   */
  connectorType: 'source' | 'sink';

  /**
   * Arbitrary metadata from the connector or manager.
   */
  metadata?: Record<string, any>;

  /**
   * Logger instance for processor logging.
   */
  logger?: {
    debug(message: string, meta?: unknown): void;
    info(message: string, meta?: unknown): void;
    warn(message: string, meta?: unknown): void;
    error(message: string, meta?: unknown): void;
  };

  /**
   * Metrics collector for processor metrics.
   */
  metrics?: {
    increment(metric: string, value?: number, tags?: Record<string, string>): void;
    gauge(metric: string, value: number, tags?: Record<string, string>): void;
    histogram(metric: string, value: number, tags?: Record<string, string>): void;
  };
}

/**
 * Base processor interface for event transformation.
 *
 * All processors must implement this interface. Processors can:
 * - Transform events (modify payload/metadata)
 * - Filter events (return null to drop)
 * - Enrich events (add metadata)
 * - Validate events (throw errors)
 * - Fork events (return array to create multiple)
 *
 * @template TIn - Input event payload type
 * @template TOut - Output event payload type
 */
export interface Processor<TIn = any, TOut = any> {
  /**
   * Unique identifier for this processor instance.
   */
  readonly id: string;

  /**
   * Processor type (validate, transform, filter, etc.).
   */
  readonly type: string;

  /**
   * Human-readable processor name.
   */
  readonly name?: string;

  /**
   * Process an event and return the transformed result.
   *
   * **Behavior**:
   * - Return modified event to continue pipeline
   * - Return null/undefined to filter (drop) event
   * - Return array to fork (create multiple events)
   * - Throw error to halt pipeline with error
   *
   * @param event - Input event to process
   * @param context - Execution context with utilities
   * @returns Processed event(s), null to filter, or Promise thereof
   * @throws Error if processing fails
   */
  process(
    event: Event<TIn>,
    context: ProcessorContext
  ): Promise<Event<TOut> | Event<TOut>[] | null> | Event<TOut> | Event<TOut>[] | null;

  /**
   * Optional configuration validation.
   * Called once when processor is registered.
   *
   * @param config - Processor configuration
   * @returns Validation result
   */
  validateConfig?(config: unknown): { valid: boolean; error?: string };

  /**
   * Optional initialization hook.
   * Called once before first event is processed.
   */
  initialize?(context: ProcessorContext): Promise<void> | void;

  /**
   * Optional cleanup hook.
   * Called when processor is removed or manager shuts down.
   */
  destroy?(): Promise<void> | void;
}

/**
 * Processor configuration schema.
 * Used to define processors in runtime configuration.
 */
export interface ProcessorConfig {
  /**
   * Unique processor identifier.
   */
  id: string;

  /**
   * Processor type (determines which processor class to use).
   */
  type: string;

  /**
   * Human-readable name.
   */
  name?: string;

  /**
   * Processor-specific configuration.
   */
  config: Record<string, any>;

  /**
   * Whether processor is enabled.
   * @default true
   */
  enabled?: boolean;

  /**
   * Execution order within processor chain.
   * Lower numbers execute first.
   * @default 100
   */
  order?: number;
}

/**
 * Processor factory function signature.
 * Used to create processor instances from configuration.
 */
export type ProcessorFactory<T extends Processor = Processor> = (
  config: ProcessorConfig
) => T | Promise<T>;

/**
 * Result of processor execution.
 * Used for detailed error handling and metrics.
 */
export interface ProcessorResult<T = any> {
  /**
   * Execution status.
   */
  status: 'success' | 'filtered' | 'error';

  /**
   * Processed event(s) if successful.
   */
  events?: Event<T> | Event<T>[];

  /**
   * Error if processing failed.
   */
  error?: Error;

  /**
   * Execution time in milliseconds.
   */
  duration?: number;

  /**
   * Additional metadata.
   */
  metadata?: Record<string, any>;
}
