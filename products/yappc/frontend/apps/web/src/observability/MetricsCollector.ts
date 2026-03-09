/**
 * Observability collector for metrics tracking and performance monitoring.
 *
 * <p><b>Purpose</b><br>
 * Provides a centralized, abstracted interface for recording metrics
 * (counters, gauges, histograms, timers) throughout the application.
 * Abstracts away implementation details of the underlying metrics library,
 * enabling easy switching between providers (Prometheus, Datadog, etc.).
 *
 * <p><b>Supported Metrics</b><br>
 * - Counters: Monotonically increasing values (e.g., requests processed)
 * - Gauges: Point-in-time values (e.g., active connections)
 * - Histograms: Value distributions (e.g., response times)
 * - Timers: Duration measurements (e.g., request processing time)
 *
 * <p><b>Tag-Based Filtering</b><br>
 * All metrics support optional tags (key-value pairs) for dimensional
 * analysis. Tags enable drilling down into metrics by service, endpoint,
 * status code, etc.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const metrics = new MetricsCollector();
 *
 * // Counter: total requests processed
 * metrics.incrementCounter("requests_total", 1, { endpoint: "/api/users" });
 *
 * // Histogram: request duration
 * metrics.recordHistogram("request_duration_ms", 245, { endpoint: "/api/users" });
 *
 * // Timer: measure operation duration
 * const timer = metrics.startTimer("database_query_duration_ms");
 * // ... perform database query
 * timer.end();
 *
 * // Gauge: current value
 * metrics.recordGauge("active_connections", 42);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Centralized metrics collection and observability
 * @doc.layer core
 * @doc.pattern Facade
 */

/**
 * Timer result for measuring operation duration.
 */
export interface TimerResult {
  /**
   * Completes the timer measurement and records duration.
   */
  readonly end: () => void;
}

/**
 * MetricsCollector implementation.
 */
export class MetricsCollector {
  private readonly _counterMap: Map<string, number> = new Map();
  private readonly _gaugeMap: Map<string, number> = new Map();
  private readonly _histogramMap: Map<string, number[]> = new Map();
  private readonly _timerMap: Map<string, number> = new Map();

  /**
   * Creates a new MetricsCollector instance.
   *
   * <p>This is the primary constructor used for creating a metrics collector.
   * In production, this would be wired through dependency injection to use
   * the appropriate backing implementation (Prometheus, Datadog, etc.).
   */
  constructor() {}

  /**
   * Increments a counter metric by specified delta.
   *
   * <p><b>Counter Semantics</b>
   * - Monotonically increasing (never decreases)
   * - Used for counting occurrences (requests, errors, events)
   * - Tags enable filtering by dimension (status, endpoint, etc.)
   *
   * @param metricName Name of the counter (e.g., "requests_total")
   * @param delta Amount to increment (default: 1)
   * @param tags Optional key-value pairs for metric dimensions
   */
  incrementCounter(
    metricName: string,
    delta: number = 1,
    tags?: Record<string, string>
  ): void {
    const key = this._buildMetricKey(metricName, tags);
    const currentValue = this._counterMap.get(key) || 0;
    this._counterMap.set(key, currentValue + delta);
  }

  /**
   * Records a gauge value (instantaneous measurement).
   *
   * <p><b>Gauge Semantics</b>
   * - Can increase or decrease
   * - Represents point-in-time values (e.g., current CPU usage)
   * - Useful for capacity and state tracking
   *
   * @param metricName Name of the gauge (e.g., "active_connections")
   * @param value The gauge value to record
   * @param tags Optional key-value pairs for metric dimensions
   */
  recordGauge(
    metricName: string,
    value: number,
    tags?: Record<string, string>
  ): void {
    const key = this._buildMetricKey(metricName, tags);
    this._gaugeMap.set(key, value);
  }

  /**
   * Records a histogram value (distribution measurement).
   *
   * <p><b>Histogram Semantics</b>
   * - Captures value distributions for percentile analysis
   * - Stores raw values for calculating p50, p95, p99, etc.
   * - Used for performance monitoring (latency, throughput)
   *
   * @param metricName Name of the histogram (e.g., "response_time_ms")
   * @param value The value to add to the distribution
   * @param tags Optional key-value pairs for metric dimensions
   */
  recordHistogram(
    metricName: string,
    value: number,
    tags?: Record<string, string>
  ): void {
    const key = this._buildMetricKey(metricName, tags);
    const values = this._histogramMap.get(key) || [];
    values.push(value);
    this._histogramMap.set(key, values);
  }

  /**
   * Starts a timer for measuring operation duration.
   *
   * <p><b>Timer Usage Pattern</b>
   * 1. Call startTimer() at operation start
   * 2. Perform operation
   * 3. Call timer.end() at operation completion
   * 4. Duration is automatically recorded as histogram
   *
   * <p><b>Example</b><br>
   * <pre>{@code
   * const timer = metrics.startTimer("database_query_time_ms");
   * const results = await database.query(sql);
   * timer.end();
   * }</pre>
   *
   * @param metricName Name of the timer (e.g., "operation_duration_ms")
   * @param tags Optional key-value pairs for metric dimensions
   * @returns TimerResult with end() method
   */
  startTimer(
    metricName: string,
    tags?: Record<string, string>
  ): TimerResult {
    const startTime = Date.now();
    const key = this._buildMetricKey(metricName, tags);

    return {
      end: () => {
        const duration = Date.now() - startTime;
        this.recordHistogram(metricName, duration, tags);
      },
    };
  }

  /**
   * Gets the current value of a counter.
   *
   * @param metricName Name of the counter
   * @param tags Optional tags for filtering
   * @returns Counter value or 0 if not found
   */
  getCounterValue(
    metricName: string,
    tags?: Record<string, string>
  ): number {
    const key = this._buildMetricKey(metricName, tags);
    return this._counterMap.get(key) || 0;
  }

  /**
   * Gets the current value of a gauge.
   *
   * @param metricName Name of the gauge
   * @param tags Optional tags for filtering
   * @returns Gauge value or 0 if not found
   */
  getGaugeValue(
    metricName: string,
    tags?: Record<string, string>
  ): number {
    const key = this._buildMetricKey(metricName, tags);
    return this._gaugeMap.get(key) || 0;
  }

  /**
   * Gets all values recorded for a histogram.
   *
   * @param metricName Name of the histogram
   * @param tags Optional tags for filtering
   * @returns Array of histogram values
   */
  getHistogramValues(
    metricName: string,
    tags?: Record<string, string>
  ): readonly number[] {
    const key = this._buildMetricKey(metricName, tags);
    return (this._histogramMap.get(key) || []) as readonly number[];
  }

  /**
   * Calculates percentile for a histogram metric.
   *
   * <p><b>Percentile Calculation</b>
   * - p50: median value (50th percentile)
   * - p95: 95th percentile (95% of values below this)
   * - p99: 99th percentile (99% of values below this)
   *
   * @param metricName Name of the histogram
   * @param percentile Percentile to calculate (0-100)
   * @param tags Optional tags for filtering
   * @returns Percentile value or null if no data
   */
  getHistogramPercentile(
    metricName: string,
    percentile: number,
    tags?: Record<string, string>
  ): number | null {
    const values = this.getHistogramValues(metricName, tags);

    if (values.length === 0) {
      return null;
    }

    const sorted = Array.from(values).sort((a, b) => a - b);
    const index = Math.ceil((percentile / 100) * sorted.length) - 1;
    return sorted[Math.max(0, index)];
  }

  /**
   * Builds a composite metric key from name and tags.
   *
   * @param metricName Metric name
   * @param tags Optional tags to include in key
   * @returns Composite key string
   */
  private _buildMetricKey(
    metricName: string,
    tags?: Record<string, string>
  ): string {
    if (!tags || Object.keys(tags).length === 0) {
      return metricName;
    }

    const tagParts = Object.entries(tags)
      .sort(([keyA], [keyB]) => keyA.localeCompare(keyB))
      .map(([key, value]) => `${key}=${value}`)
      .join(",");

    return `${metricName}{${tagParts}}`;
  }

  /**
   * Resets all metrics (useful for testing).
   */
  reset(): void {
    this._counterMap.clear();
    this._gaugeMap.clear();
    this._histogramMap.clear();
    this._timerMap.clear();
  }

  /**
   * Gets all counter metrics.
   *
   * @returns Record of all counter values
   */
  getAllCounters(): Record<string, number> {
    const result: Record<string, number> = {};
    for (const [key, value] of this._counterMap.entries()) {
      result[key] = value;
    }
    return result;
  }

  /**
   * Gets all gauge metrics.
   *
   * @returns Record of all gauge values
   */
  getAllGauges(): Record<string, number> {
    const result: Record<string, number> = {};
    for (const [key, value] of this._gaugeMap.entries()) {
      result[key] = value;
    }
    return result;
  }

  /**
   * Gets histogram statistics for a metric.
   *
   * @param metricName Name of the histogram
   * @param tags Optional tags for filtering
   * @returns Histogram statistics
   */
  getHistogramStats(
    metricName: string,
    tags?: Record<string, string>
  ): {
    count: number;
    min: number | null;
    max: number | null;
    avg: number | null;
    p50: number | null;
    p95: number | null;
    p99: number | null;
  } {
    const values = this.getHistogramValues(metricName, tags);

    if (values.length === 0) {
      return {
        count: 0,
        min: null,
        max: null,
        avg: null,
        p50: null,
        p95: null,
        p99: null,
      };
    }

    const sorted = Array.from(values).sort((a, b) => a - b);
    const sum = values.reduce((a, b) => a + b, 0);

    return {
      count: values.length,
      min: sorted[0],
      max: sorted[sorted.length - 1],
      avg: sum / values.length,
      p50: this.getHistogramPercentile(metricName, 50, tags) || null,
      p95: this.getHistogramPercentile(metricName, 95, tags) || null,
      p99: this.getHistogramPercentile(metricName, 99, tags) || null,
    };
  }
}

/**
 * No-op metrics collector for testing and scenarios where metrics
 * are not needed.
 *
 * <p>All methods are implemented as no-ops (do nothing). Useful for
 * testing and dependency injection where a real metrics collector
 * is not required.
 */
export class NoopMetricsCollector extends MetricsCollector {
  /**
   * Creates a no-op metrics collector.
   */
  override incrementCounter(
    _metricName: string,
    _delta?: number,
    _tags?: Record<string, string>
  ): void {
    // No-op
  }

  override recordGauge(
    _metricName: string,
    _value: number,
    _tags?: Record<string, string>
  ): void {
    // No-op
  }

  override recordHistogram(
    _metricName: string,
    _value: number,
    _tags?: Record<string, string>
  ): void {
    // No-op
  }

  override startTimer(
    _metricName: string,
    _tags?: Record<string, string>
  ): TimerResult {
    return {
      end: () => {
        // No-op
      },
    };
  }
}
