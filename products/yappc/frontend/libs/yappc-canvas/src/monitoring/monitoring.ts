/**
 * Monitoring Infrastructure for Canvas Application
 * 
 * Provides Prometheus-compatible metrics exporters, performance tracking,
 * and alerting utilities for canvas rendering, collaboration, and exports.
 * 
 * Features:
 * - FPS and render latency tracking
 * - Collaboration latency metrics
 * - Export operation success/failure tracking
 * - Prometheus metric exposition format
 * - Alert threshold checking
 * - Dashboard data structures for Grafana
 * 
 * @module monitoring
 */

// ============================================================================
// Types
// ============================================================================

/**
 * Metric types supported
 */
export type MetricType = 'counter' | 'gauge' | 'histogram' | 'summary';

/**
 * Metric label
 */
export interface MetricLabel {
  name: string;
  value: string;
}

/**
 * Base metric interface
 */
export interface Metric {
  name: string;
  type: MetricType;
  help: string;
  labels: MetricLabel[];
  value: number;
  timestamp: number;
}

/**
 * Counter metric (monotonically increasing)
 */
export interface CounterMetric extends Metric {
  type: 'counter';
}

/**
 * Gauge metric (can go up or down)
 */
export interface GaugeMetric extends Metric {
  type: 'gauge';
}

/**
 * Histogram bucket
 */
export interface HistogramBucket {
  le: number; // less than or equal
  count: number;
}

/**
 * Histogram metric
 */
export interface HistogramMetric extends Omit<Metric, 'value'> {
  type: 'histogram';
  buckets: HistogramBucket[];
  sum: number;
  count: number;
}

/**
 * Summary quantile
 */
export interface SummaryQuantile {
  quantile: number; // 0.5, 0.9, 0.95, 0.99
  value: number;
}

/**
 * Summary metric
 */
export interface SummaryMetric extends Omit<Metric, 'value'> {
  type: 'summary';
  quantiles: SummaryQuantile[];
  sum: number;
  count: number;
}

/**
 * Performance metrics for canvas rendering
 */
export interface RenderMetrics {
  fps: number;
  frameTime: number; // milliseconds
  renderTime: number; // milliseconds
  nodeCount: number;
  edgeCount: number;
  droppedFrames: number;
}

/**
 * Collaboration latency metrics
 */
export interface CollaborationMetrics {
  messageLatency: number; // milliseconds
  presenceUpdateLatency: number; // milliseconds
  conflictCount: number;
  connectionCount: number;
}

/**
 * Export operation metrics
 */
export interface ExportMetrics {
  successCount: number;
  failureCount: number;
  averageDuration: number; // milliseconds
  format: string; // 'png', 'svg', 'pdf', etc.
}

/**
 * Alert severity levels
 */
export type AlertSeverity = 'info' | 'warning' | 'critical';

/**
 * Alert threshold configuration
 */
export interface AlertThreshold {
  metric: string;
  operator: '>' | '<' | '>=' | '<=' | '==' | '!=';
  value: number;
  severity: AlertSeverity;
  message: string;
}

/**
 * Alert event
 */
export interface AlertEvent {
  id: string;
  threshold: AlertThreshold;
  currentValue: number;
  timestamp: Date;
  resolved: boolean;
  resolvedAt?: Date;
}

/**
 * Dashboard panel configuration
 */
export interface DashboardPanel {
  id: string;
  title: string;
  type: 'graph' | 'stat' | 'table' | 'heatmap';
  metrics: string[]; // metric names
  refreshInterval: number; // milliseconds
}

/**
 * Grafana-compatible dashboard
 */
export interface Dashboard {
  uid: string;
  title: string;
  panels: DashboardPanel[];
  tags: string[];
  refresh: string; // '5s', '10s', '30s', '1m', etc.
}

/**
 * Monitoring configuration
 */
export interface MonitoringConfig {
  /** Enable metrics collection */
  enabled: boolean;
  /** Metrics collection interval (ms) */
  collectionInterval: number;
  /** Max metrics to store in memory */
  maxMetrics: number;
  /** Enable alert checking */
  enableAlerts: boolean;
  /** Alert check interval (ms) */
  alertCheckInterval: number;
  /** Prometheus endpoint path */
  prometheusEndpoint: string;
}

/**
 * Monitoring manager state
 */
export interface MonitoringState {
  config: MonitoringConfig;
  metrics: Map<string, Metric | HistogramMetric | SummaryMetric>;
  alerts: Map<string, AlertEvent>;
  thresholds: Map<string, AlertThreshold>;
  collectionTimer?: NodeJS.Timeout;
  alertCheckTimer?: NodeJS.Timeout;
  alertListeners: Array<(event: AlertEvent) => void>;
}

// ============================================================================
// Manager
// ============================================================================

/**
 * Create monitoring manager
 */
export function createMonitoringManager(
  config: Partial<MonitoringConfig> = {}
): MonitoringState {
  const defaultConfig: MonitoringConfig = {
    enabled: true,
    collectionInterval: 5000, // 5 seconds
    maxMetrics: 10000,
    enableAlerts: true,
    alertCheckInterval: 10000, // 10 seconds
    prometheusEndpoint: '/metrics',
  };

  return {
    config: { ...defaultConfig, ...config },
    metrics: new Map(),
    alerts: new Map(),
    thresholds: new Map(),
    alertListeners: [],
  };
}

// ============================================================================
// Metric Recording
// ============================================================================

/**
 * Record counter metric
 */
export function recordCounter(
  state: MonitoringState,
  name: string,
  value: number,
  labels: MetricLabel[] = [],
  help = ''
): CounterMetric {
  const existing = state.metrics.get(name) as CounterMetric | undefined;
  const newValue = existing ? existing.value + value : value;

  const metric: CounterMetric = {
    name,
    type: 'counter',
    help: help || existing?.help || '',
    labels,
    value: newValue,
    timestamp: Date.now(),
  };

  state.metrics.set(name, metric);
  enforceMaxMetrics(state);

  return metric;
}

/**
 * Record gauge metric
 */
export function recordGauge(
  state: MonitoringState,
  name: string,
  value: number,
  labels: MetricLabel[] = [],
  help = ''
): GaugeMetric {
  const existing = state.metrics.get(name) as GaugeMetric | undefined;

  const metric: GaugeMetric = {
    name,
    type: 'gauge',
    help: help || existing?.help || '',
    labels,
    value,
    timestamp: Date.now(),
  };

  state.metrics.set(name, metric);
  enforceMaxMetrics(state);

  return metric;
}

/**
 * Record histogram observation
 */
export function recordHistogram(
  state: MonitoringState,
  name: string,
  value: number,
  buckets: number[] = [0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10],
  labels: MetricLabel[] = [],
  help = ''
): HistogramMetric {
  const existing = state.metrics.get(name) as HistogramMetric | undefined;

  // Initialize or update buckets
  const histogramBuckets: HistogramBucket[] = buckets.map((le) => {
    const existingBucket = existing?.buckets.find((b) => b.le === le);
    const count = existingBucket ? existingBucket.count : 0;
    return {
      le,
      count: value <= le ? count + 1 : count,
    };
  });

  // Add +Inf bucket
  histogramBuckets.push({
    le: Infinity,
    count: (existing?.count || 0) + 1,
  });

  const metric: HistogramMetric = {
    name,
    type: 'histogram',
    help: help || existing?.help || '',
    labels,
    buckets: histogramBuckets,
    sum: (existing?.sum || 0) + value,
    count: (existing?.count || 0) + 1,
    timestamp: Date.now(),
  };

  state.metrics.set(name, metric);
  enforceMaxMetrics(state);

  return metric;
}

/**
 * Record summary observation
 */
export function recordSummary(
  state: MonitoringState,
  name: string,
  value: number,
  labels: MetricLabel[] = [],
  help = ''
): SummaryMetric {
  const existing = state.metrics.get(name) as SummaryMetric | undefined;

  // For simplicity, we'll just track sum and count
  // In production, use a proper quantile calculation (t-digest, etc.)
  const metric: SummaryMetric = {
    name,
    type: 'summary',
    help: help || existing?.help || '',
    labels,
    quantiles: [], // Would calculate from sliding window of values
    sum: (existing?.sum || 0) + value,
    count: (existing?.count || 0) + 1,
    timestamp: Date.now(),
  };

  state.metrics.set(name, metric);
  enforceMaxMetrics(state);

  return metric;
}

/**
 * Enforce max metrics limit (LRU eviction)
 */
function enforceMaxMetrics(state: MonitoringState): void {
  if (state.metrics.size <= state.config.maxMetrics) {
    return;
  }

  // Find oldest metric
  let oldestName: string | null = null;
  let oldestTimestamp = Infinity;

  for (const [name, metric] of state.metrics.entries()) {
    if (metric.timestamp < oldestTimestamp) {
      oldestTimestamp = metric.timestamp;
      oldestName = name;
    }
  }

  if (oldestName) {
    state.metrics.delete(oldestName);
  }
}

// ============================================================================
// Canvas-Specific Metrics
// ============================================================================

/**
 * Record render performance metrics
 */
export function recordRenderMetrics(
  state: MonitoringState,
  metrics: RenderMetrics
): void {
  recordGauge(state, 'canvas_fps', metrics.fps, [], 'Frames per second');
  recordHistogram(
    state,
    'canvas_frame_time_ms',
    metrics.frameTime,
    undefined,
    [],
    'Frame time in milliseconds'
  );
  recordHistogram(
    state,
    'canvas_render_time_ms',
    metrics.renderTime,
    undefined,
    [],
    'Render time in milliseconds'
  );
  recordGauge(state, 'canvas_node_count', metrics.nodeCount, [], 'Number of nodes');
  recordGauge(state, 'canvas_edge_count', metrics.edgeCount, [], 'Number of edges');
  recordCounter(
    state,
    'canvas_dropped_frames_total',
    metrics.droppedFrames,
    [],
    'Total dropped frames'
  );
}

/**
 * Record collaboration metrics
 */
export function recordCollaborationMetrics(
  state: MonitoringState,
  metrics: CollaborationMetrics
): void {
  recordHistogram(
    state,
    'collab_message_latency_ms',
    metrics.messageLatency,
    undefined,
    [],
    'Collaboration message latency in milliseconds'
  );
  recordHistogram(
    state,
    'collab_presence_latency_ms',
    metrics.presenceUpdateLatency,
    undefined,
    [],
    'Presence update latency in milliseconds'
  );
  recordCounter(
    state,
    'collab_conflict_total',
    metrics.conflictCount,
    [],
    'Total collaboration conflicts'
  );
  recordGauge(
    state,
    'collab_connections',
    metrics.connectionCount,
    [],
    'Active collaboration connections'
  );
}

/**
 * Record export operation
 */
export function recordExportMetrics(
  state: MonitoringState,
  metrics: ExportMetrics
): void {
  const labels: MetricLabel[] = [{ name: 'format', value: metrics.format }];

  recordCounter(
    state,
    'export_success_total',
    metrics.successCount,
    labels,
    'Total successful exports'
  );
  recordCounter(
    state,
    'export_failure_total',
    metrics.failureCount,
    labels,
    'Total failed exports'
  );
  recordHistogram(
    state,
    'export_duration_ms',
    metrics.averageDuration,
    undefined,
    labels,
    'Export duration in milliseconds'
  );
}

// ============================================================================
// Prometheus Export
// ============================================================================

/**
 * Format metric labels for Prometheus
 */
function formatLabels(labels: MetricLabel[]): string {
  if (labels.length === 0) return '';
  const pairs = labels.map((l) => `${l.name}="${l.value}"`);
  return `{${pairs.join(',')}}`;
}

/**
 * Export counter metric to Prometheus format
 */
function exportCounter(metric: CounterMetric): string {
  const labels = formatLabels(metric.labels);
  return `${metric.name}${labels} ${metric.value} ${metric.timestamp}`;
}

/**
 * Export gauge metric to Prometheus format
 */
function exportGauge(metric: GaugeMetric): string {
  const labels = formatLabels(metric.labels);
  return `${metric.name}${labels} ${metric.value} ${metric.timestamp}`;
}

/**
 * Export histogram metric to Prometheus format
 */
function exportHistogram(metric: HistogramMetric): string {
  const labels = formatLabels(metric.labels);
  const lines: string[] = [];

  // Bucket counts
  for (const bucket of metric.buckets) {
    const le = bucket.le === Infinity ? '+Inf' : bucket.le.toString();
    const bucketLabels = labels
      ? `${labels.slice(0, -1)  },le="${le}"}`
      : `{le="${le}"}`;
    lines.push(`${metric.name}_bucket${bucketLabels} ${bucket.count} ${metric.timestamp}`);
  }

  // Sum and count
  lines.push(`${metric.name}_sum${labels} ${metric.sum} ${metric.timestamp}`);
  lines.push(`${metric.name}_count${labels} ${metric.count} ${metric.timestamp}`);

  return lines.join('\n');
}

/**
 * Export summary metric to Prometheus format
 */
function exportSummary(metric: SummaryMetric): string {
  const labels = formatLabels(metric.labels);
  const lines: string[] = [];

  // Quantiles
  for (const q of metric.quantiles) {
    const quantileLabels = labels
      ? `${labels.slice(0, -1)  },quantile="${q.quantile}"}`
      : `{quantile="${q.quantile}"}`;
    lines.push(`${metric.name}${quantileLabels} ${q.value} ${metric.timestamp}`);
  }

  // Sum and count
  lines.push(`${metric.name}_sum${labels} ${metric.sum} ${metric.timestamp}`);
  lines.push(`${metric.name}_count${labels} ${metric.count} ${metric.timestamp}`);

  return lines.join('\n');
}

/**
 * Export all metrics to Prometheus format
 */
export function exportPrometheusMetrics(state: MonitoringState): string {
  const lines: string[] = [];

  for (const metric of state.metrics.values()) {
    // Add HELP and TYPE lines
    lines.push(`# HELP ${metric.name} ${metric.help}`);
    lines.push(`# TYPE ${metric.name} ${metric.type}`);

    // Add metric values
    switch (metric.type) {
      case 'counter':
        lines.push(exportCounter(metric as CounterMetric));
        break;
      case 'gauge':
        lines.push(exportGauge(metric as GaugeMetric));
        break;
      case 'histogram':
        lines.push(exportHistogram(metric as HistogramMetric));
        break;
      case 'summary':
        lines.push(exportSummary(metric as SummaryMetric));
        break;
    }

    lines.push(''); // Empty line between metrics
  }

  return lines.join('\n');
}

// ============================================================================
// Alerting
// ============================================================================

/**
 * Add alert threshold
 */
export function addAlertThreshold(
  state: MonitoringState,
  threshold: AlertThreshold
): void {
  state.thresholds.set(threshold.metric, threshold);
}

/**
 * Remove alert threshold
 */
export function removeAlertThreshold(state: MonitoringState, metricName: string): boolean {
  return state.thresholds.delete(metricName);
}

/**
 * Check if metric exceeds threshold
 */
function checkThreshold(metric: Metric, threshold: AlertThreshold): boolean {
  const value = metric.value;

  switch (threshold.operator) {
    case '>':
      return value > threshold.value;
    case '<':
      return value < threshold.value;
    case '>=':
      return value >= threshold.value;
    case '<=':
      return value <= threshold.value;
    case '==':
      return value === threshold.value;
    case '!=':
      return value !== threshold.value;
    default:
      return false;
  }
}

/**
 * Check all alert thresholds
 */
export function checkAlerts(state: MonitoringState): AlertEvent[] {
  const newAlerts: AlertEvent[] = [];

  for (const [metricName, threshold] of state.thresholds.entries()) {
    const metric = state.metrics.get(metricName);
    if (!metric || metric.type === 'histogram' || metric.type === 'summary') {
      continue;
    }

    const violated = checkThreshold(metric, threshold);
    const existingAlert = state.alerts.get(metricName);

    if (violated && !existingAlert) {
      // New alert
      const alert: AlertEvent = {
        id: `alert_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
        threshold,
        currentValue: metric.value,
        timestamp: new Date(),
        resolved: false,
      };
      state.alerts.set(metricName, alert);
      newAlerts.push(alert);

      // Notify listeners
      for (const listener of state.alertListeners) {
        try {
          listener(alert);
        } catch (error) {
          console.error('Alert listener error:', error);
        }
      }
    } else if (!violated && existingAlert && !existingAlert.resolved) {
      // Alert resolved
      existingAlert.resolved = true;
      existingAlert.resolvedAt = new Date();
    }
  }

  return newAlerts;
}

/**
 * Subscribe to alert events
 */
export function subscribeToAlerts(
  state: MonitoringState,
  listener: (event: AlertEvent) => void
): () => void {
  state.alertListeners.push(listener);

  return () => {
    const index = state.alertListeners.indexOf(listener);
    if (index !== -1) {
      state.alertListeners.splice(index, 1);
    }
  };
}

/**
 * Get all active alerts
 */
export function getActiveAlerts(state: MonitoringState): AlertEvent[] {
  return Array.from(state.alerts.values()).filter((alert) => !alert.resolved);
}

/**
 * Get all alerts (including resolved)
 */
export function getAllAlerts(state: MonitoringState): AlertEvent[] {
  return Array.from(state.alerts.values());
}

/**
 * Clear resolved alerts
 */
export function clearResolvedAlerts(state: MonitoringState): number {
  let count = 0;
  for (const [metricName, alert] of state.alerts.entries()) {
    if (alert.resolved) {
      state.alerts.delete(metricName);
      count++;
    }
  }
  return count;
}

// ============================================================================
// Dashboard Generation
// ============================================================================

/**
 * Create dashboard for Grafana
 */
export function createDashboard(
  uid: string,
  title: string,
  panels: DashboardPanel[],
  tags: string[] = [],
  refresh = '10s'
): Dashboard {
  return {
    uid,
    title,
    panels,
    tags,
    refresh,
  };
}

/**
 * Create canvas performance dashboard
 */
export function createCanvasPerformanceDashboard(): Dashboard {
  return createDashboard(
    'canvas-performance',
    'Canvas Performance',
    [
      {
        id: 'fps',
        title: 'Frames Per Second',
        type: 'graph',
        metrics: ['canvas_fps'],
        refreshInterval: 5000,
      },
      {
        id: 'frame-time',
        title: 'Frame Time',
        type: 'graph',
        metrics: ['canvas_frame_time_ms'],
        refreshInterval: 5000,
      },
      {
        id: 'render-time',
        title: 'Render Time',
        type: 'graph',
        metrics: ['canvas_render_time_ms'],
        refreshInterval: 5000,
      },
      {
        id: 'node-count',
        title: 'Node Count',
        type: 'stat',
        metrics: ['canvas_node_count'],
        refreshInterval: 10000,
      },
      {
        id: 'edge-count',
        title: 'Edge Count',
        type: 'stat',
        metrics: ['canvas_edge_count'],
        refreshInterval: 10000,
      },
      {
        id: 'dropped-frames',
        title: 'Dropped Frames',
        type: 'stat',
        metrics: ['canvas_dropped_frames_total'],
        refreshInterval: 10000,
      },
    ],
    ['canvas', 'performance'],
    '5s'
  );
}

/**
 * Create collaboration dashboard
 */
export function createCollaborationDashboard(): Dashboard {
  return createDashboard(
    'canvas-collaboration',
    'Canvas Collaboration',
    [
      {
        id: 'message-latency',
        title: 'Message Latency',
        type: 'graph',
        metrics: ['collab_message_latency_ms'],
        refreshInterval: 5000,
      },
      {
        id: 'presence-latency',
        title: 'Presence Update Latency',
        type: 'graph',
        metrics: ['collab_presence_latency_ms'],
        refreshInterval: 5000,
      },
      {
        id: 'conflicts',
        title: 'Conflicts',
        type: 'stat',
        metrics: ['collab_conflict_total'],
        refreshInterval: 10000,
      },
      {
        id: 'connections',
        title: 'Active Connections',
        type: 'stat',
        metrics: ['collab_connections'],
        refreshInterval: 5000,
      },
    ],
    ['canvas', 'collaboration'],
    '10s'
  );
}

/**
 * Create export operations dashboard
 */
export function createExportDashboard(): Dashboard {
  return createDashboard(
    'canvas-exports',
    'Canvas Export Operations',
    [
      {
        id: 'export-success',
        title: 'Successful Exports',
        type: 'stat',
        metrics: ['export_success_total'],
        refreshInterval: 10000,
      },
      {
        id: 'export-failure',
        title: 'Failed Exports',
        type: 'stat',
        metrics: ['export_failure_total'],
        refreshInterval: 10000,
      },
      {
        id: 'export-duration',
        title: 'Export Duration',
        type: 'graph',
        metrics: ['export_duration_ms'],
        refreshInterval: 10000,
      },
    ],
    ['canvas', 'exports'],
    '30s'
  );
}

// ============================================================================
// Lifecycle Management
// ============================================================================

/**
 * Start monitoring (collection and alerts)
 */
export function startMonitoring(state: MonitoringState): void {
  if (!state.config.enabled) {
    return;
  }

  // Start metrics collection timer
  if (!state.collectionTimer) {
    state.collectionTimer = setInterval(() => {
      // In production, this would trigger automatic metric collection
      // For now, metrics are recorded explicitly via record* functions
    }, state.config.collectionInterval);
  }

  // Start alert checking timer
  if (state.config.enableAlerts && !state.alertCheckTimer) {
    state.alertCheckTimer = setInterval(() => {
      checkAlerts(state);
    }, state.config.alertCheckInterval);
  }
}

/**
 * Stop monitoring
 */
export function stopMonitoring(state: MonitoringState): void {
  if (state.collectionTimer) {
    clearInterval(state.collectionTimer);
    state.collectionTimer = undefined;
  }

  if (state.alertCheckTimer) {
    clearInterval(state.alertCheckTimer);
    state.alertCheckTimer = undefined;
  }
}

// ============================================================================
// Metric Queries
// ============================================================================

/**
 * Get metric by name
 */
export function getMetric(
  state: MonitoringState,
  name: string
): Metric | HistogramMetric | SummaryMetric | null {
  return state.metrics.get(name) || null;
}

/**
 * Get all metrics
 */
export function getAllMetrics(
  state: MonitoringState
): Array<Metric | HistogramMetric | SummaryMetric> {
  return Array.from(state.metrics.values());
}

/**
 * Get metrics by type
 */
export function getMetricsByType(
  state: MonitoringState,
  type: MetricType
): Array<Metric | HistogramMetric | SummaryMetric> {
  return Array.from(state.metrics.values()).filter((m) => m.type === type);
}

/**
 * Clear all metrics
 */
export function clearMetrics(state: MonitoringState): void {
  state.metrics.clear();
}

// ============================================================================
// Configuration
// ============================================================================

/**
 * Get configuration
 */
export function getConfig(state: MonitoringState): MonitoringConfig {
  return { ...state.config };
}

/**
 * Update configuration
 */
export function updateConfig(
  state: MonitoringState,
  updates: Partial<MonitoringConfig>
): MonitoringConfig {
  state.config = { ...state.config, ...updates };

  // Restart monitoring if intervals changed
  if (updates.collectionInterval || updates.alertCheckInterval) {
    stopMonitoring(state);
    startMonitoring(state);
  }

  return { ...state.config };
}
