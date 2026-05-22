/**
 * ToolchainMetricsCollector - collects metrics for toolchain adapter execution.
 *
 * Provides structured metrics collection for adapter execution including:
 * - Execution duration
 * - Success/failure counts
 * - Adapter-specific metrics
 * - Surface-specific metrics
 *
 * @doc.type class
 * @doc.purpose Collect metrics for toolchain adapter execution
 * @doc.layer kernel-toolchains
 * @doc.pattern Metrics
 */

import type { ToolchainExecutionResult } from "./ToolchainAdapter.js";

export interface AdapterMetrics {
  readonly adapterId: string;
  readonly surfaceId: string;
  readonly phase: string;
  readonly executionCount: number;
  readonly successCount: number;
  readonly failureCount: number;
  readonly skipCount: number;
  readonly totalDurationMs: number;
  readonly averageDurationMs: number;
  readonly lastExecutionAt: string;
  readonly lastStatus: "succeeded" | "failed" | "skipped";
}

export interface SurfaceMetrics {
  readonly surfaceId: string;
  readonly adapterMetrics: Map<string, AdapterMetrics>;
}

export class ToolchainMetricsCollector {
  private readonly metrics: Map<string, AdapterMetrics> = new Map();
  private readonly surfaceMetrics: Map<string, SurfaceMetrics> = new Map();

  /**
   * Records a toolchain execution result.
   *
   * @param adapterId the adapter identifier
   * @param surfaceId the surface identifier
   * @param phase the lifecycle phase
   * @param result the execution result
   */
  recordExecution(
    adapterId: string,
    surfaceId: string,
    phase: string,
    result: ToolchainExecutionResult
  ): void {
    const key = `${adapterId}:${surfaceId}:${phase}`;
    const existing = this.metrics.get(key);
    const durationMs = result.durationMs ?? 0;
    const now = new Date().toISOString();

    const updated: AdapterMetrics = {
      adapterId,
      surfaceId,
      phase,
      executionCount: (existing?.executionCount ?? 0) + 1,
      successCount: (existing?.successCount ?? 0) + (result.status === "succeeded" ? 1 : 0),
      failureCount: (existing?.failureCount ?? 0) + (result.status === "failed" ? 1 : 0),
      skipCount: (existing?.skipCount ?? 0) + (result.status === "skipped" ? 1 : 0),
      totalDurationMs: (existing?.totalDurationMs ?? 0) + durationMs,
      averageDurationMs: existing
        ? (existing.totalDurationMs + durationMs) / (existing.executionCount + 1)
        : durationMs,
      lastExecutionAt: now,
      lastStatus: result.status as "succeeded" | "failed" | "skipped",
    };

    this.metrics.set(key, updated);
    this.updateSurfaceMetrics(surfaceId, adapterId, updated);
  }

  private updateSurfaceMetrics(surfaceId: string, adapterId: string, adapterMetrics: AdapterMetrics): void {
    let surfaceMetric = this.surfaceMetrics.get(surfaceId);
    if (!surfaceMetric) {
      surfaceMetric = {
        surfaceId,
        adapterMetrics: new Map(),
      };
      this.surfaceMetrics.set(surfaceId, surfaceMetric);
    }
    surfaceMetric.adapterMetrics.set(adapterId, adapterMetrics);
  }

  /**
   * Gets metrics for a specific adapter, surface, and phase.
   *
   * @param adapterId the adapter identifier
   * @param surfaceId the surface identifier
   * @param phase the lifecycle phase
   * @returns the adapter metrics, or undefined if not found
   */
  getAdapterMetrics(adapterId: string, surfaceId: string, phase: string): AdapterMetrics | undefined {
    return this.metrics.get(`${adapterId}:${surfaceId}:${phase}`);
  }

  /**
   * Gets all metrics for a specific surface.
   *
   * @param surfaceId the surface identifier
   * @returns the surface metrics, or undefined if not found
   */
  getSurfaceMetrics(surfaceId: string): SurfaceMetrics | undefined {
    return this.surfaceMetrics.get(surfaceId);
  }

  /**
   * Gets all collected metrics.
   *
   * @returns a map of all adapter metrics
   */
  getAllMetrics(): ReadonlyMap<string, AdapterMetrics> {
    return this.metrics;
  }

  /**
   * Gets all surface metrics.
   *
   * @returns a map of all surface metrics
   */
  getAllSurfaceMetrics(): ReadonlyMap<string, SurfaceMetrics> {
    return this.surfaceMetrics;
  }

  /**
   * Clears all collected metrics.
   */
  clear(): void {
    this.metrics.clear();
    this.surfaceMetrics.clear();
  }

  /**
   * Gets aggregated metrics across all adapters.
   *
   * @returns aggregated metrics
   */
  getAggregatedMetrics(): {
    totalExecutions: number;
    totalSuccesses: number;
    totalFailures: number;
    totalSkips: number;
    totalDurationMs: number;
    averageDurationMs: number;
  } {
    let totalExecutions = 0;
    let totalSuccesses = 0;
    let totalFailures = 0;
    let totalSkips = 0;
    let totalDurationMs = 0;

    for (const metrics of this.metrics.values()) {
      totalExecutions += metrics.executionCount;
      totalSuccesses += metrics.successCount;
      totalFailures += metrics.failureCount;
      totalSkips += metrics.skipCount;
      totalDurationMs += metrics.totalDurationMs;
    }

    return {
      totalExecutions,
      totalSuccesses,
      totalFailures,
      totalSkips,
      totalDurationMs,
      averageDurationMs: totalExecutions > 0 ? totalDurationMs / totalExecutions : 0,
    };
  }

  /**
   * Exports metrics as JSON for dashboard consumption.
   *
   * @returns JSON-serializable metrics object
   */
  exportMetrics(): {
    adapterMetrics: Record<string, AdapterMetrics>;
    surfaceMetrics: Record<string, SurfaceMetrics>;
    aggregated: {
      totalExecutions: number;
      totalSuccesses: number;
      totalFailures: number;
      totalSkips: number;
      totalDurationMs: number;
      averageDurationMs: number;
    };
    exportedAt: string;
  } {
    const adapterMetrics: Record<string, AdapterMetrics> = {};
    for (const [key, value] of this.metrics.entries()) {
      adapterMetrics[key] = value;
    }

    const surfaceMetrics: Record<string, SurfaceMetrics> = {};
    for (const [key, value] of this.surfaceMetrics.entries()) {
      surfaceMetrics[key] = value;
    }

    const aggregated = {
      totalExecutions: 0,
      totalSuccesses: 0,
      totalFailures: 0,
      totalSkips: 0,
      totalDurationMs: 0,
      averageDurationMs: 0,
    };

    for (const metrics of this.metrics.values()) {
      aggregated.totalExecutions += metrics.executionCount;
      aggregated.totalSuccesses += metrics.successCount;
      aggregated.totalFailures += metrics.failureCount;
      aggregated.totalSkips += metrics.skipCount;
      aggregated.totalDurationMs += metrics.totalDurationMs;
    }
    aggregated.averageDurationMs = aggregated.totalExecutions > 0 ? aggregated.totalDurationMs / aggregated.totalExecutions : 0;

    return {
      adapterMetrics,
      surfaceMetrics,
      aggregated,
      exportedAt: new Date().toISOString(),
    };
  }
}
