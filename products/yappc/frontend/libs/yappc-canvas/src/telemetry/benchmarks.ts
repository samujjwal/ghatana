/**
 * Canvas Performance Benchmark Harness
 * Phase E.3 - Performance Monitoring and Analysis
 *
 * Provides structured benchmarking for Canvas operations:
 * - Rendering performance measurement
 * - State operation benchmarks
 * - Memory usage tracking
 * - Performance regression detection
 *
 * @package @ghatana/yappc-canvas-benchmarks
 * @version 1.0.0
 * @since Phase E.3
 */

import { CanvasTelemetry, CanvasTelemetryEvents } from './index';

/**
 * Benchmark result interface
 */
export interface BenchmarkResult {
  name: string;
  duration: number;
  memoryUsage?: {
    before: number;
    after: number;
    delta: number;
  };
  iterations: number;
  averageTime: number;
  minTime: number;
  maxTime: number;
  standardDeviation: number;
  timestamp: number;
}

/**
 * Benchmark configuration
 */
export interface BenchmarkConfig {
  iterations?: number;
  warmupIterations?: number;
  measureMemory?: boolean;
  timeout?: number;
}

/**
 * Canvas Performance Benchmark Suite
 */
export class CanvasBenchmarkHarness {
  private results: Map<string, BenchmarkResult[]> = new Map();

  /**
   * Run a performance benchmark
   */
  async runBenchmark(
    name: string,
    testFunction: () => void | Promise<void>,
    config: BenchmarkConfig = {}
  ): Promise<BenchmarkResult> {
    const {
      iterations = 100,
      warmupIterations = 10,
      measureMemory = false,
      timeout = 30000,
    } = config;

    // Warmup runs
    for (let i = 0; i < warmupIterations; i++) {
      await testFunction();
    }

    // Force garbage collection if available
    if (typeof window !== 'undefined' && (window as unknown).gc) {
      (window as unknown).gc();
    }

    const durations: number[] = [];
    let memoryBefore = 0;
    let memoryAfter = 0;

    if (measureMemory && 'memory' in performance) {
      memoryBefore = (performance as unknown).memory.usedJSHeapSize;
    }

    const startTime = performance.now();

    for (let i = 0; i < iterations; i++) {
      const iterationStart = performance.now();

      try {
        await Promise.race([
          testFunction(),
          new Promise((_, reject) =>
            setTimeout(() => reject(new Error('Benchmark timeout')), timeout)
          ),
        ]);
      } catch (error) {
        CanvasTelemetry.recordError(
          CanvasTelemetryEvents.CANVAS_RENDER,
          error instanceof Error ? error : new Error(String(error)),
          {
            'canvas.benchmark.name': name,
            'canvas.benchmark.iteration': i,
          }
        );
        throw error;
      }

      const iterationEnd = performance.now();
      durations.push(iterationEnd - iterationStart);
    }

    if (measureMemory && 'memory' in performance) {
      memoryAfter = (performance as unknown).memory.usedJSHeapSize;
    }

    const totalTime = performance.now() - startTime;
    const averageTime = durations.reduce((a, b) => a + b, 0) / durations.length;
    const minTime = Math.min(...durations);
    const maxTime = Math.max(...durations);

    // Calculate standard deviation
    const variance =
      durations.reduce((acc, time) => {
        return acc + Math.pow(time - averageTime, 2);
      }, 0) / durations.length;
    const standardDeviation = Math.sqrt(variance);

    const result: BenchmarkResult = {
      name,
      duration: totalTime,
      iterations,
      averageTime,
      minTime,
      maxTime,
      standardDeviation,
      timestamp: Date.now(),
      ...(measureMemory && {
        memoryUsage: {
          before: memoryBefore,
          after: memoryAfter,
          delta: memoryAfter - memoryBefore,
        },
      }),
    };

    // Store result
    if (!this.results.has(name)) {
      this.results.set(name, []);
    }
    this.results.get(name)!.push(result);

    // Record telemetry
    CanvasTelemetry.recordEvent(CanvasTelemetryEvents.CANVAS_RENDER, {
      'canvas.benchmark.name': name,
      'canvas.benchmark.iterations': iterations,
      'canvas.benchmark.average_time': averageTime,
      'canvas.benchmark.min_time': minTime,
      'canvas.benchmark.max_time': maxTime,
      'canvas.benchmark.std_dev': standardDeviation,
      'canvas.performance.duration': totalTime,
    });

    return result;
  }

  /**
   * Compare benchmark results
   */
  compareBenchmarks(
    baselineName: string,
    comparisonName: string
  ): {
    baselineAverage: number;
    comparisonAverage: number;
    performanceDelta: number;
    significantDifference: boolean;
  } | null {
    const baseline = this.getLatestResult(baselineName);
    const comparison = this.getLatestResult(comparisonName);

    if (!baseline || !comparison) {
      return null;
    }

    const performanceDelta = comparison.averageTime - baseline.averageTime;
    const significantDifference =
      Math.abs(performanceDelta) >
      Math.max(baseline.standardDeviation, comparison.standardDeviation);

    return {
      baselineAverage: baseline.averageTime,
      comparisonAverage: comparison.averageTime,
      performanceDelta,
      significantDifference,
    };
  }

  /**
   * Get latest result for a benchmark
   */
  getLatestResult(name: string): BenchmarkResult | null {
    const results = this.results.get(name);
    return results && results.length > 0 ? results[results.length - 1] : null;
  }

  /**
   * Get all results for a benchmark
   */
  getAllResults(name: string): BenchmarkResult[] {
    return this.results.get(name) || [];
  }

  /**
   * Get performance trend for a benchmark
   */
  getPerformanceTrend(
    name: string,
    sampleSize = 10
  ): {
    trend: 'improving' | 'degrading' | 'stable';
    changePercent: number;
    confidence: number;
  } | null {
    const results = this.results.get(name);
    if (!results || results.length < sampleSize) {
      return null;
    }

    const recent = results.slice(-sampleSize);
    const firstHalf = recent.slice(0, Math.floor(sampleSize / 2));
    const secondHalf = recent.slice(Math.floor(sampleSize / 2));

    const firstAvg =
      firstHalf.reduce((sum, r) => sum + r.averageTime, 0) / firstHalf.length;
    const secondAvg =
      secondHalf.reduce((sum, r) => sum + r.averageTime, 0) / secondHalf.length;

    const changePercent = ((secondAvg - firstAvg) / firstAvg) * 100;
    const confidence = Math.min(100, sampleSize * 10); // Simple confidence calculation

    let trend: 'improving' | 'degrading' | 'stable' = 'stable';
    if (Math.abs(changePercent) > 5) {
      // 5% threshold
      trend = changePercent < 0 ? 'improving' : 'degrading';
    }

    return {
      trend,
      changePercent,
      confidence,
    };
  }

  /**
   * Export results as JSON
   */
  exportResults(): string {
    const exportData = {
      timestamp: Date.now(),
      results: Object.fromEntries(this.results),
    };

    return JSON.stringify(exportData, null, 2);
  }

  /**
   * Clear all stored results
   */
  clearResults(): void {
    this.results.clear();
  }
}

/**
 * Pre-defined Canvas operation benchmarks
 */
export class CanvasOperationBenchmarks {
  private harness = new CanvasBenchmarkHarness();

  /**
   * Benchmark Canvas initialization
   */
  async benchmarkCanvasInit(
    initFunction: () => void | Promise<void>,
    config?: BenchmarkConfig
  ): Promise<BenchmarkResult> {
    return this.harness.runBenchmark('canvas-init', initFunction, {
      iterations: 50,
      measureMemory: true,
      ...config,
    });
  }

  /**
   * Benchmark element addition
   */
  async benchmarkElementAdd(
    addFunction: () => void | Promise<void>,
    config?: BenchmarkConfig
  ): Promise<BenchmarkResult> {
    return this.harness.runBenchmark('element-add', addFunction, {
      iterations: 1000,
      ...config,
    });
  }

  /**
   * Benchmark viewport rendering
   */
  async benchmarkViewportRender(
    renderFunction: () => void | Promise<void>,
    config?: BenchmarkConfig
  ): Promise<BenchmarkResult> {
    return this.harness.runBenchmark('viewport-render', renderFunction, {
      iterations: 200,
      measureMemory: true,
      ...config,
    });
  }

  /**
   * Benchmark state synchronization
   */
  async benchmarkStateSync(
    syncFunction: () => void | Promise<void>,
    config?: BenchmarkConfig
  ): Promise<BenchmarkResult> {
    return this.harness.runBenchmark('state-sync', syncFunction, {
      iterations: 500,
      ...config,
    });
  }

  /**
   * Get benchmark harness for custom benchmarks
   */
  getHarness(): CanvasBenchmarkHarness {
    return this.harness;
  }
}

/**
 * Default benchmark harness instance
 */
export const canvasBenchmarks = new CanvasOperationBenchmarks();

/**
 * Utility function to create a benchmark suite
 */
export function createBenchmarkSuite(): CanvasBenchmarkHarness {
  return new CanvasBenchmarkHarness();
}
