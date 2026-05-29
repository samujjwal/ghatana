/**
 * TelemetryProvider - interface for emitting lifecycle events and metrics.
 *
 * @doc.type interface
 * @doc.purpose Telemetry provider interface for event and metric emission
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import { z } from "zod";
import type { KernelProvider } from "./KernelProvider.js";

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

export const TelemetryEventSchema = z
  .object({
    eventId: z.string().trim().min(1),
    eventType: z.string().trim().min(1),
    timestamp: z.string().datetime({ offset: true }),
    productUnitId: z.string().trim().min(1),
    payload: z.record(z.string(), z.unknown()),
  })
  .strict();

/**
 * Metric value.
 */
export interface MetricValue {
  readonly name: string;
  readonly value: number;
  readonly labels: Record<string, string>;
  readonly timestamp: string;
}

export const MetricValueSchema = z
  .object({
    name: z.string().trim().min(1),
    value: z.number(),
    labels: z.record(z.string(), z.string()),
    timestamp: z.string().datetime({ offset: true }),
  })
  .strict();

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

export const TelemetryProviderSchema = z.custom<TelemetryProvider>(
  (value) => {
    if (typeof value !== "object" || value === null) {
      return false;
    }
    const provider = value as Record<string, unknown>;
    return (
      typeof provider.emitEvent === "function" &&
      typeof provider.recordMetric === "function" &&
      typeof provider.getEvents === "function" &&
      typeof provider.getMetrics === "function"
    );
  },
  "TelemetryProvider requires event and metric functions"
);

export function validateTelemetryEvent(
  value: unknown
): value is TelemetryEvent {
  return TelemetryEventSchema.safeParse(value).success;
}

export function validateMetricValue(value: unknown): value is MetricValue {
  return MetricValueSchema.safeParse(value).success;
}

export function validateTelemetryProvider(
  value: unknown
): value is TelemetryProvider {
  return TelemetryProviderSchema.safeParse(value).success;
}
