/**
 * TelemetryProvider - interface for emitting lifecycle events and metrics.
 *
 * @doc.type interface
 * @doc.purpose Telemetry provider interface for event and metric emission
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import type { KernelProvider } from "./KernelProvider";

/**
 * Telemetry event.
 */
export interface TelemetryEvent {
  readonly eventId: string;
  readonly eventType: string;
  readonly timestamp: string;
  readonly productUnitId: string;
  readonly payload: Record<string, unknown>;
}

/**
 * Metric value.
 */
export interface MetricValue {
  readonly name: string;
  readonly value: number;
  readonly labels: Record<string, string>;
  readonly timestamp: string;
}

/**
 * Telemetry provider for emitting lifecycle events and metrics.
 */
export interface TelemetryProvider extends KernelProvider {
  /**
   * Emits a telemetry event.
   */
  emitEvent(event: TelemetryEvent): Promise<void>;

  /**
   * Records a metric value.
   */
  recordMetric(metric: MetricValue): Promise<void>;

  /**
   * Gets events for a product unit.
   */
  getEvents(productUnitId: string): Promise<readonly TelemetryEvent[]>;

  /**
   * Gets metrics for a product unit.
   */
  getMetrics(productUnitId: string): Promise<readonly MetricValue[]>;
}
