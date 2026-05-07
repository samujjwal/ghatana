/**
 * Performance metrics collection utilities.
 *
 * <p><b>Purpose</b><br>
 * Track and collect performance metrics for queries and operations.
 * Provides timing, profiling, and reporting capabilities.
 *
 * <p><b>Features</b><br>
 * - Operation timing (ns precision)
 * - Performance percentiles
 * - Bottleneck identification
 * - Report generation
 * - Real-time tracking
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { performanceTracker } from '@/lib/performance/performanceMetrics';
 *
 * const tracker = performanceTracker.start('query-execution');
 * // ... do work ...
 * tracker.end();
 *
 * const report = performanceTracker.getReport();
 * }</pre>
 *
 * @doc.type utility
 * @doc.purpose Performance metrics tracking
 * @doc.layer frontend
 * @doc.pattern Utility (Metrics)
 */

interface OperationMetrics {
  name: string;
  startTime: number;
  endTime?: number;
  duration?: number;
  timestamp: string;
}

interface PerformanceReport {
  totalOperations: number;
  averageDuration: number;
  minDuration: number;
  maxDuration: number;
  p50Duration: number;
  p95Duration: number;
  p99Duration: number;
  bottlenecks: Array<{
    name: string;
    count: number;
    averageDuration: number;
  }>;
}

/**
 * Performance tracker utility.
 */
class PerformanceTracker {
  private metrics: OperationMetrics[] = [];
  private activeTrackers: Map<string, number> = new Map();
  private maxMetrics = 1000; // Keep last 1000 metrics

  /**
   * Start tracking an operation.
   *
   * @param name operation name
   * @returns operation ID for tracking
   */
  start(name: string): string {
    const operationId = `${name}-${Date.now()}-${Math.random()}`;
    this.activeTrackers.set(operationId, performance.now());
    return operationId;
  }

  /**
   * End tracking for an operation.
   *
   * @param operationId the operation ID
   */
  end(operationId: string): void {
    const startTime = this.activeTrackers.get(operationId);
    if (!startTime) {
      console.warn(`Operation ${operationId} not found`);
      return;
    }

    const endTime = performance.now();
    const duration = endTime - startTime;

    this.metrics.push({
      name: operationId.split('-')[0],
      startTime,
      endTime,
      duration,
      timestamp: new Date().toISOString(),
    });

    // Keep metrics bounded
    if (this.metrics.length > this.maxMetrics) {
      this.metrics.shift();
    }

    this.activeTrackers.delete(operationId);
  }

  /**
   * Get performance report.
   *
   * @returns performance metrics report
   */
  getReport(): PerformanceReport {
    if (this.metrics.length === 0) {
      return {
        totalOperations: 0,
        averageDuration: 0,
        minDuration: 0,
        maxDuration: 0,
        p50Duration: 0,
        p95Duration: 0,
        p99Duration: 0,
        bottlenecks: [],
      };
    }

    const durations = this.metrics
      .filter((m) => m.duration !== undefined)
      .map((m) => m.duration as number)
      .sort((a, b) => a - b);

    const avgDuration = durations.reduce((a, b) => a + b, 0) / durations.length;

    // Calculate percentiles
    const getPercentile = (arr: number[], p: number) => {
      const index = Math.ceil((p / 100) * arr.length) - 1;
      return arr[Math.max(0, index)];
    };

    // Find bottlenecks (operations slower than 100ms)
    const operationGroups = new Map<string, number[]>();
    for (const metric of this.metrics) {
      if (!operationGroups.has(metric.name)) {
        operationGroups.set(metric.name, []);
      }
      if (metric.duration !== undefined) {
        operationGroups.get(metric.name)!.push(metric.duration);
      }
    }

    const bottlenecks = Array.from(operationGroups.entries())
      .map(([name, durations]) => ({
        name,
        count: durations.length,
        averageDuration: durations.reduce((a, b) => a + b, 0) / durations.length,
      }))
      .filter((b) => b.averageDuration > 100)
      .sort((a, b) => b.averageDuration - a.averageDuration)
      .slice(0, 5);

    return {
      totalOperations: this.metrics.length,
      averageDuration: avgDuration,
      minDuration: durations[0],
      maxDuration: durations[durations.length - 1],
      p50Duration: getPercentile(durations, 50),
      p95Duration: getPercentile(durations, 95),
      p99Duration: getPercentile(durations, 99),
      bottlenecks,
    };
  }

  /**
   * Log performance report.
   */
  logReport(): void {
    const report = this.getReport();
    console.table(report);
    if (report.bottlenecks.length > 0) {
      console.warn('Bottlenecks detected:', report.bottlenecks);
    }
  }

  /**
   * Clear all metrics.
   */
  clear(): void {
    this.metrics = [];
    this.activeTrackers.clear();
  }

  /**
   * Get metrics for specific operation.
   *
   * @param operationName the operation name
   * @returns metrics for that operation
   */
  getMetricsFor(operationName: string): OperationMetrics[] {
    return this.metrics.filter((m) => m.name === operationName);
  }
}

// Export singleton instance
export const performanceTracker = new PerformanceTracker();

/**
 * React hook for performance tracking.
 *
 * @param operationName operation name
 * @returns timing utility
 */
export function usePerformanceTracking(operationName: string) {
  return {
    start: () => performanceTracker.start(operationName),
    end: (id: string) => performanceTracker.end(id),
    report: () => performanceTracker.getReport(),
  };
}

export type { PerformanceReport };
export default performanceTracker;

