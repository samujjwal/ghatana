import { EventEmitter } from 'events';

/**
 * @fileoverview In-memory metrics collection and aggregation utilities.
 *
 * Supports counters, gauges, histograms, and summaries with label support,
 * real-time event emission, and snapshot extraction for exporting to
 * telemetry backends.
 */

/**
 * Real-time metric event emitted by `MetricsCollector`.
 */
export interface Metric {
  /** Metric name (e.g., `connector_requests_total`). */
  name: string;
  /** Current metric value. */
  value: number;
  /** Millisecond timestamp when value recorded. */
  timestamp: number;
  /** Optional label set associated with metric instance. */
  labels?: Record<string, string>;
  /** Metric instrument type. */
  type: 'counter' | 'gauge' | 'histogram' | 'summary';
}

/**
 * Snapshot representation of metrics for exporting.
 */
export interface MetricSnapshot {
  name: string;
  type: string;
  value: number;
  labels: Record<string, string>;
  timestamp: number;
}

/**
 * In-memory metrics collector providing basic aggregation and snapshots.
 *
 * **Features:**
 * - Counter/gauge mutation helpers with label support
 * - Histogram/summary observation storage with percentile stats
 * - Real-time `metric` event emission for streaming
 * - Snapshot generation for exporting to Prometheus/OTLP
 *
 * @example
 * ```ts
 * const collector = new MetricsCollector();
 * collector.incrementCounter('connector_requests_total', 1, { connector: 'http' });
 * collector.setGauge('connector_connections', 5);
 * collector.observeHistogram('connector_latency_ms', 42);
 * const snapshot = collector.getSnapshot();
 * ```
 *
 * @example
 * ```ts
 * const collector = new MetricsCollector();
 * collector.on('metric', metric => console.log('metric event', metric));
 * collector.observeSummary('batch_size', 10, { pipeline: 'orders' });
 * collector.observeSummary('batch_size', 15, { pipeline: 'orders' });
 * const stats = collector.getSummaryStats('batch_size', { pipeline: 'orders' });
 * console.log(stats?.mean); // Average batch size
 * ```
 */
export class MetricsCollector extends EventEmitter {
  /** Counter values keyed by metric name + labels. */
  private counters: Map<string, number> = new Map();
  /** Gauge values keyed by metric name + labels. */
  private gauges: Map<string, number> = new Map();
  /** Histogram observation arrays keyed by metric name + labels. */
  private histograms: Map<string, number[]> = new Map();
  /** Summary observation arrays keyed by metric name + labels. */
  private summaries: Map<string, number[]> = new Map();
  /** Stored label sets keyed by metric name + labels string. */
  private labels: Map<string, Record<string, string>> = new Map();

  /**
   * Increments a counter by `value` (default 1).
   *
   * @param {string} name - Metric name
   * @param {number} [value=1] - Increment amount
   * @param {Record<string, string>} [labels] - Optional label set
   * @fires MetricsCollector#metric
   */
  incrementCounter(name: string, value: number = 1, labels?: Record<string, string>): void {
    const key = this._getKey(name, labels);
    const current = this.counters.get(key) || 0;
    this.counters.set(key, current + value);
    
    if (labels) {
      this.labels.set(key, labels);
    }

    this.emit('metric', {
      name,
      value: current + value,
      timestamp: Date.now(),
      labels,
      type: 'counter',
    });
  }

  /**
   * Sets gauge to `value` (overwriting existing).
   *
   * @param {string} name - Metric name
   * @param {number} value - Gauge value
   * @param {Record<string, string>} [labels] - Optional label set
   * @fires MetricsCollector#metric
   */
  setGauge(name: string, value: number, labels?: Record<string, string>): void {
    const key = this._getKey(name, labels);
    this.gauges.set(key, value);
    
    if (labels) {
      this.labels.set(key, labels);
    }

    this.emit('metric', {
      name,
      value,
      timestamp: Date.now(),
      labels,
      type: 'gauge',
    });
  }

  /**
   * Records observation for histogram metric.
   *
   * @param {string} name - Metric name
   * @param {number} value - Observation value
   * @param {Record<string, string>} [labels] - Optional label set
   * @fires MetricsCollector#metric
   */
  observeHistogram(name: string, value: number, labels?: Record<string, string>): void {
    const key = this._getKey(name, labels);
    const values = this.histograms.get(key) || [];
    values.push(value);
    this.histograms.set(key, values);
    
    if (labels) {
      this.labels.set(key, labels);
    }

    this.emit('metric', {
      name,
      value,
      timestamp: Date.now(),
      labels,
      type: 'histogram',
    });
  }

  /**
   * Records observation for summary metric.
   *
   * @param {string} name - Metric name
   * @param {number} value - Observation value
   * @param {Record<string, string>} [labels] - Optional label set
   * @fires MetricsCollector#metric
   */
  observeSummary(name: string, value: number, labels?: Record<string, string>): void {
    const key = this._getKey(name, labels);
    const values = this.summaries.get(key) || [];
    values.push(value);
    this.summaries.set(key, values);
    
    if (labels) {
      this.labels.set(key, labels);
    }

    this.emit('metric', {
      name,
      value,
      timestamp: Date.now(),
      labels,
      type: 'summary',
    });
  }

  /**
   * Retrieves current counter value.
   *
   * @param {string} name - Metric name
   * @param {Record<string, string>} [labels]
   * @returns {number}
   */
  getCounter(name: string, labels?: Record<string, string>): number {
    const key = this._getKey(name, labels);
    return this.counters.get(key) || 0;
  }

  /**
   * Retrieves current gauge value.
   *
   * @param {string} name - Metric name
   * @param {Record<string, string>} [labels]
   * @returns {number}
   */
  getGauge(name: string, labels?: Record<string, string>): number {
    const key = this._getKey(name, labels);
    return this.gauges.get(key) || 0;
  }

  /**
   * Computes histogram statistics for observations.
   *
   * @param {string} name - Metric name
   * @param {Record<string, string>} [labels]
   * @returns {object | null} Stats or null if no observations
   */
  getHistogramStats(name: string, labels?: Record<string, string>): {
    count: number;
    sum: number;
    min: number;
    max: number;
    mean: number;
    p50: number;
    p95: number;
    p99: number;
  } | null {
    const key = this._getKey(name, labels);
    const values = this.histograms.get(key);
    
    if (!values || values.length === 0) {
      return null;
    }

    const sorted = [...values].sort((a, b) => a - b);
    const sum = sorted.reduce((acc, val) => acc + val, 0);
    
    return {
      count: sorted.length,
      sum,
      min: sorted[0],
      max: sorted[sorted.length - 1],
      mean: sum / sorted.length,
      p50: this._percentile(sorted, 50),
      p95: this._percentile(sorted, 95),
      p99: this._percentile(sorted, 99),
    };
  }

  /**
   * Computes summary statistics for observations.
   *
   * @param {string} name - Metric name
   * @param {Record<string, string>} [labels]
   * @returns {object | null} Stats or null
   */
  getSummaryStats(name: string, labels?: Record<string, string>): {
    count: number;
    sum: number;
    min: number;
    max: number;
    mean: number;
  } | null {
    const key = this._getKey(name, labels);
    const values = this.summaries.get(key);
    
    if (!values || values.length === 0) {
      return null;
    }

    const sum = values.reduce((acc, val) => acc + val, 0);
    
    return {
      count: values.length,
      sum,
      min: Math.min(...values),
      max: Math.max(...values),
      mean: sum / values.length,
    };
  }

  /**
   * Produces snapshot array suitable for exporting.
   */
  getSnapshot(): MetricSnapshot[] {
    const snapshot: MetricSnapshot[] = [];
    const timestamp = Date.now();

    // Counters
    for (const [key, value] of this.counters.entries()) {
      const { name, labels } = this._parseKey(key);
      snapshot.push({
        name,
        type: 'counter',
        value,
        labels,
        timestamp,
      });
    }

    // Gauges
    for (const [key, value] of this.gauges.entries()) {
      const { name, labels } = this._parseKey(key);
      snapshot.push({
        name,
        type: 'gauge',
        value,
        labels,
        timestamp,
      });
    }

    // Histograms
    for (const key of this.histograms.keys()) {
      const { name, labels } = this._parseKey(key);
      const stats = this.getHistogramStats(name, labels);
      if (stats) {
        snapshot.push({
          name: `${name}_count`,
          type: 'histogram',
          value: stats.count,
          labels,
          timestamp,
        });
        snapshot.push({
          name: `${name}_sum`,
          type: 'histogram',
          value: stats.sum,
          labels,
          timestamp,
        });
        snapshot.push({
          name: `${name}_p50`,
          type: 'histogram',
          value: stats.p50,
          labels,
          timestamp,
        });
        snapshot.push({
          name: `${name}_p95`,
          type: 'histogram',
          value: stats.p95,
          labels,
          timestamp,
        });
        snapshot.push({
          name: `${name}_p99`,
          type: 'histogram',
          value: stats.p99,
          labels,
          timestamp,
        });
      }
    }

    // Summaries
    for (const key of this.summaries.keys()) {
      const { name, labels } = this._parseKey(key);
      const stats = this.getSummaryStats(name, labels);
      if (stats) {
        snapshot.push({
          name: `${name}_count`,
          type: 'summary',
          value: stats.count,
          labels,
          timestamp,
        });
        snapshot.push({
          name: `${name}_sum`,
          type: 'summary',
          value: stats.sum,
          labels,
          timestamp,
        });
      }
    }

    return snapshot;
  }

  /**
   * Clears all metrics and emits reset event.
   */
  reset(): void {
    this.counters.clear();
    this.gauges.clear();
    this.histograms.clear();
    this.summaries.clear();
    this.labels.clear();
    this.emit('reset');
  }

  /**
   * Removes specific metric series (all instrument types).
   */
  resetMetric(name: string, labels?: Record<string, string>): void {
    const key = this._getKey(name, labels);
    this.counters.delete(key);
    this.gauges.delete(key);
    this.histograms.delete(key);
    this.summaries.delete(key);
    this.labels.delete(key);
  }

  /**
   * Builds unique key from name + sorted label set.
   */
  private _getKey(name: string, labels?: Record<string, string>): string {
    if (!labels || Object.keys(labels).length === 0) {
      return name;
    }

    const labelStr = Object.entries(labels)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([k, v]) => `${k}="${v}"`)
      .join(',');

    return `${name}{${labelStr}}`;
  }

  /**
   * Parses key into name + labels object.
   */
  private _parseKey(key: string): { name: string; labels: Record<string, string> } {
    const match = key.match(/^([^{]+)(?:\{([^}]+)\})?$/);
    
    if (!match) {
      return { name: key, labels: {} };
    }

    const name = match[1];
    const labelsStr = match[2];
    
    if (!labelsStr) {
      return { name, labels: {} };
    }

    const labels: Record<string, string> = {};
    const pairs = labelsStr.split(',');
    
    for (const pair of pairs) {
      const [k, v] = pair.split('=');
      if (k && v) {
        labels[k] = v.replace(/^"|"$/g, '');
      }
    }

    return { name, labels };
  }

  /**
   * Calculates percentile using linear interpolation.
   */
  private _percentile(sorted: number[], percentile: number): number {
    const index = (percentile / 100) * (sorted.length - 1);
    const lower = Math.floor(index);
    const upper = Math.ceil(index);
    const weight = index - lower;

    if (lower === upper) {
      return sorted[lower];
    }

    return sorted[lower] * (1 - weight) + sorted[upper] * weight;
  }
}
