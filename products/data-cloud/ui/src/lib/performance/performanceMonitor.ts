/**
 * Performance monitoring utilities for CES Workflow Platform.
 *
 * <p><b>Purpose</b><br>
 * Tracks and reports performance metrics including render times,
 * memory usage, and interaction latency.
 *
 * <p><b>Features</b><br>
 * - Component render time tracking
 * - Memory usage monitoring
 * - Interaction latency measurement
 * - Performance metrics reporting
 * - Threshold-based alerts
 *
 * @doc.type utility
 * @doc.purpose Performance monitoring
 * @doc.layer frontend
 */

interface PerformanceMetric {
  name: string;
  duration: number;
  timestamp: number;
  type: 'render' | 'interaction' | 'api' | 'memory';
}

interface PerformanceThresholds {
  renderTime: number; // ms
  interactionLatency: number; // ms
  memoryUsage: number; // MB
}

/**
 * Performance monitor for tracking metrics.
 *
 * @doc.class PerformanceMonitor
 * @doc.purpose Centralized performance tracking
 */
export class PerformanceMonitor {
  private metrics: PerformanceMetric[] = [];
  private thresholds: PerformanceThresholds;
  private maxMetrics: number = 1000;

  constructor(
    thresholds: PerformanceThresholds = {
      renderTime: 1000,
      interactionLatency: 100,
      memoryUsage: 100,
    }
  ) {
    this.thresholds = thresholds;
  }

  /**
   * Record a performance metric.
   *
   * @param name - Metric name
   * @param duration - Duration in milliseconds
   * @param type - Metric type
   */
  recordMetric(
    name: string,
    duration: number,
    type: 'render' | 'interaction' | 'api' | 'memory'
  ): void {
    const metric: PerformanceMetric = {
      name,
      duration,
      timestamp: Date.now(),
      type,
    };

    this.metrics.push(metric);

    // Keep metrics array bounded
    if (this.metrics.length > this.maxMetrics) {
      this.metrics.shift();
    }

    // Check thresholds
    this.checkThresholds(metric);
  }

  /**
   * Check if metric exceeds thresholds.
   *
   * @param metric - Performance metric
   */
  private checkThresholds(metric: PerformanceMetric): void {
    switch (metric.type) {
      case 'render':
        if (metric.duration > this.thresholds.renderTime) {
          console.warn(
            `Render time exceeded: ${metric.name} (${metric.duration}ms)`
          );
        }
        break;
      case 'interaction':
        if (metric.duration > this.thresholds.interactionLatency) {
          console.warn(
            `Interaction latency exceeded: ${metric.name} (${metric.duration}ms)`
          );
        }
        break;
      case 'memory':
        if (metric.duration > this.thresholds.memoryUsage) {
          console.warn(
            `Memory usage exceeded: ${metric.name} (${metric.duration}MB)`
          );
        }
        break;
    }
  }

  /**
   * Get average metric duration.
   *
   * @param name - Metric name
   * @returns Average duration
   */
  getAverageDuration(name: string): number {
    const filtered = this.metrics.filter((m) => m.name === name);
    if (filtered.length === 0) return 0;

    const sum = filtered.reduce((acc, m) => acc + m.duration, 0);
    return sum / filtered.length;
  }

  /**
   * Get metrics summary.
   *
   * @returns Summary object
   */
  getSummary(): {
    totalMetrics: number;
    averageRenderTime: number;
    averageInteractionLatency: number;
    peakMemoryUsage: number;
  } {
    const renderMetrics = this.metrics.filter((m) => m.type === 'render');
    const interactionMetrics = this.metrics.filter(
      (m) => m.type === 'interaction'
    );
    const memoryMetrics = this.metrics.filter((m) => m.type === 'memory');

    const avgRender =
      renderMetrics.length > 0
        ? renderMetrics.reduce((acc, m) => acc + m.duration, 0) /
          renderMetrics.length
        : 0;

    const avgInteraction =
      interactionMetrics.length > 0
        ? interactionMetrics.reduce((acc, m) => acc + m.duration, 0) /
          interactionMetrics.length
        : 0;

    const peakMemory =
      memoryMetrics.length > 0
        ? Math.max(...memoryMetrics.map((m) => m.duration))
        : 0;

    return {
      totalMetrics: this.metrics.length,
      averageRenderTime: avgRender,
      averageInteractionLatency: avgInteraction,
      peakMemoryUsage: peakMemory,
    };
  }

  /**
   * Clear all metrics.
   */
  clear(): void {
    this.metrics = [];
  }

  /**
   * Export metrics as JSON.
   *
   * @returns JSON string
   */
  export(): string {
    return JSON.stringify({
      metrics: this.metrics,
      summary: this.getSummary(),
      timestamp: new Date().toISOString(),
    });
  }
}

// Global performance monitor instance
export const globalPerformanceMonitor = new PerformanceMonitor();

/**
 * Measure function execution time.
 *
 * @param name - Function name
 * @param fn - Function to measure
 * @returns Function result
 */
export function measurePerformance<T>(
  name: string,
  fn: () => T
): T {
  const start = performance.now();
  const result = fn();
  const duration = performance.now() - start;

  globalPerformanceMonitor.recordMetric(name, duration, 'render');
  return result;
}

/**
 * Measure async function execution time.
 *
 * @param name - Function name
 * @param fn - Async function to measure
 * @returns Promise with result
 */
export async function measurePerformanceAsync<T>(
  name: string,
  fn: () => Promise<T>
): Promise<T> {
  const start = performance.now();
  const result = await fn();
  const duration = performance.now() - start;

  globalPerformanceMonitor.recordMetric(name, duration, 'api');
  return result;
}
