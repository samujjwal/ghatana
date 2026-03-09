/**
 * Performance Metrics Dashboard (Feature 2.29)
 * 
 * Provides in-app telemetry with FPS/memory monitoring, trace export,
 * OTLP (OpenTelemetry Protocol) threshold alerting, and dev mode toggle
 * for performance analysis and observability.
 * 
 * Features:
 * - Real-time FPS and memory monitoring
 * - Performance trace collection and export
 * - OTLP metric publishing
 * - Threshold-based alerting
 * - Dev mode overlay toggle
 * - Web Vitals tracking (LCP, FID, CLS, TTFB, INP)
 * 
 * @module monitoring/telemetryDashboard
 */

// ============================================================================
// Types & Interfaces
// ============================================================================

/**
 * Performance metrics snapshot
 */
export interface PerformanceMetrics {
  timestamp: number;
  fps: number;
  memory: MemoryMetrics;
  rendering: RenderingMetrics;
  interaction: InteractionMetrics;
  network: NetworkMetrics;
  vitals: WebVitals;
}

/**
 * Memory usage metrics
 */
export interface MemoryMetrics {
  usedJSHeapSize: number;      // bytes
  totalJSHeapSize: number;     // bytes
  jsHeapSizeLimit: number;     // bytes
  percentUsed: number;         // 0-100
  collections?: number;        // GC count
}

/**
 * Rendering performance metrics
 */
export interface RenderingMetrics {
  elementCount: number;
  visibleElements: number;
  renderTime: number;           // ms
  paintTime: number;            // ms
  layoutTime: number;           // ms
  scriptTime: number;           // ms
  idleTime: number;             // ms
}

/**
 * Interaction metrics
 */
export interface InteractionMetrics {
  inputDelay: number;           // ms
  eventLatency: number;         // ms
  interactionCount: number;
  longTasks: number;            // tasks > 50ms
  blockedTime: number;          // ms
}

/**
 * Network performance metrics
 */
export interface NetworkMetrics {
  online: boolean;
  effectiveType?: string;       // 'slow-2g', '2g', '3g', '4g'
  downlink?: number;            // Mbps
  rtt?: number;                 // ms - round trip time
  saveData?: boolean;
}

/**
 * Web Vitals metrics (Core Web Vitals + additional)
 */
export interface WebVitals {
  lcp?: number;                 // Largest Contentful Paint (ms)
  fid?: number;                 // First Input Delay (ms) - deprecated
  inp?: number;                 // Interaction to Next Paint (ms)
  cls?: number;                 // Cumulative Layout Shift (score)
  ttfb?: number;                // Time to First Byte (ms)
  fcp?: number;                 // First Contentful Paint (ms)
}

/**
 * Performance trace for detailed analysis
 */
export interface PerformanceTrace {
  id: string;
  name: string;
  startTime: number;
  endTime?: number;
  duration?: number;
  metadata: Record<string, unknown>;
  spans: PerformanceSpan[];
  tags: string[];
}

/**
 * Individual span within a trace
 */
export interface PerformanceSpan {
  id: string;
  traceId: string;
  name: string;
  startTime: number;
  endTime: number;
  duration: number;
  parentSpanId?: string;
  attributes: Record<string, string | number | boolean>;
  events: Array<{
    timestamp: number;
    name: string;
    attributes?: Record<string, unknown>;
  }>;
}

/**
 * Alert threshold configuration
 */
export interface AlertThreshold {
  id: string;
  metric: string;               // e.g., 'fps', 'memory.percentUsed', 'vitals.lcp'
  operator: 'gt' | 'lt' | 'gte' | 'lte' | 'eq';
  value: number;
  duration?: number;            // ms - sustained breach duration
  severity: 'info' | 'warning' | 'error' | 'critical';
  enabled: boolean;
  lastTriggered?: number;
  cooldownPeriod?: number;      // ms - minimum time between alerts
}

/**
 * Performance alert
 */
export interface PerformanceAlert {
  id: string;
  thresholdId: string;
  timestamp: number;
  severity: 'info' | 'warning' | 'error' | 'critical';
  metric: string;
  actualValue: number;
  thresholdValue: number;
  message: string;
  acknowledged: boolean;
  acknowledgedAt?: number;
  acknowledgedBy?: string;
}

/**
 * OTLP metric for export
 */
export interface OTLPMetric {
  name: string;
  description?: string;
  unit?: string;
  type: 'gauge' | 'counter' | 'histogram';
  dataPoints: Array<{
    timestamp: number;
    value: number;
    attributes?: Record<string, string | number | boolean>;
  }>;
}

/**
 * OTLP export payload
 */
export interface OTLPExportPayload {
  resourceMetrics: Array<{
    resource: {
      attributes: Record<string, string>;
    };
    scopeMetrics: Array<{
      scope: {
        name: string;
        version: string;
      };
      metrics: OTLPMetric[];
    }>;
  }>;
}

/**
 * Telemetry dashboard configuration
 */
export interface TelemetryConfig {
  enabled: boolean;
  sampleInterval: number;       // ms - how often to collect metrics
  maxTraces: number;            // max traces to keep in memory
  maxAlerts: number;            // max alerts to keep in memory
  otlpEndpoint?: string;        // OTLP collector URL
  otlpExportInterval?: number;  // ms - how often to export to OTLP
  devModeEnabled: boolean;      // show dev overlay
  devModePosition: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';
  thresholds: AlertThreshold[];
}

/**
 * Telemetry dashboard state
 */
export interface TelemetryDashboard {
  config: TelemetryConfig;
  metrics: PerformanceMetrics[];
  traces: PerformanceTrace[];
  alerts: PerformanceAlert[];
  activeTrace?: PerformanceTrace;
  isMonitoring: boolean;
  intervalId?: number;
  otlpIntervalId?: number;
  startTime: number;
}

/**
 * Statistics about collected telemetry
 */
export interface TelemetryStatistics {
  totalSamples: number;
  totalTraces: number;
  totalAlerts: number;
  averageFPS: number;
  averageMemoryUsage: number;
  peakMemoryUsage: number;
  totalLongTasks: number;
  averageRenderTime: number;
  alertsBySeverity: Record<string, number>;
  uptime: number;               // ms
}

// ============================================================================
// Core Functions
// ============================================================================

/**
 * Create a new telemetry dashboard
 */
export function createTelemetryDashboard(config?: Partial<TelemetryConfig>): TelemetryDashboard {
  const defaultConfig: TelemetryConfig = {
    enabled: true,
    sampleInterval: 1000,
    maxTraces: 100,
    maxAlerts: 50,
    otlpExportInterval: 60000, // 1 minute
    devModeEnabled: false,
    devModePosition: 'bottom-right',
    thresholds: [
      {
        id: 'fps-low',
        metric: 'fps',
        operator: 'lt',
        value: 30,
        duration: 5000,
        severity: 'warning',
        enabled: true,
        cooldownPeriod: 10000
      },
      {
        id: 'memory-high',
        metric: 'memory.percentUsed',
        operator: 'gt',
        value: 80,
        duration: 10000,
        severity: 'error',
        enabled: true,
        cooldownPeriod: 30000
      },
      {
        id: 'lcp-poor',
        metric: 'vitals.lcp',
        operator: 'gt',
        value: 2500,
        severity: 'warning',
        enabled: true,
        cooldownPeriod: 60000
      }
    ],
    ...config
  };

  return {
    config: defaultConfig,
    metrics: [],
    traces: [],
    alerts: [],
    isMonitoring: false,
    startTime: Date.now()
  };
}

/**
 * Start monitoring performance metrics
 */
export function startMonitoring(dashboard: TelemetryDashboard): void {
  if (dashboard.isMonitoring) {
    return;
  }

  dashboard.isMonitoring = true;
  dashboard.startTime = Date.now();

  // Start metrics collection
  dashboard.intervalId = window.setInterval(() => {
    const metrics = collectMetrics();
    dashboard.metrics.push(metrics);

    // Trim old metrics
    if (dashboard.metrics.length > 1000) {
      dashboard.metrics = dashboard.metrics.slice(-1000);
    }

    // Check thresholds
    checkThresholds(dashboard, metrics);
  }, dashboard.config.sampleInterval);

  // Start OTLP export if configured
  if (dashboard.config.otlpEndpoint && dashboard.config.otlpExportInterval) {
    dashboard.otlpIntervalId = window.setInterval(() => {
      exportToOTLP(dashboard);
    }, dashboard.config.otlpExportInterval);
  }
}

/**
 * Stop monitoring performance metrics
 */
export function stopMonitoring(dashboard: TelemetryDashboard): void {
  if (!dashboard.isMonitoring) {
    return;
  }

  dashboard.isMonitoring = false;

  if (dashboard.intervalId) {
    window.clearInterval(dashboard.intervalId);
    dashboard.intervalId = undefined;
  }

  if (dashboard.otlpIntervalId) {
    window.clearInterval(dashboard.otlpIntervalId);
    dashboard.otlpIntervalId = undefined;
  }
}

/**
 * Collect current performance metrics
 */
export function collectMetrics(): PerformanceMetrics {
  const memory = collectMemoryMetrics();
  const rendering = collectRenderingMetrics();
  const interaction = collectInteractionMetrics();
  const network = collectNetworkMetrics();
  const vitals = collectWebVitals();
  const fps = calculateFPS();

  return {
    timestamp: Date.now(),
    fps,
    memory,
    rendering,
    interaction,
    network,
    vitals
  };
}

/**
 * Start a new performance trace
 */
export function startTrace(
  dashboard: TelemetryDashboard,
  name: string,
  metadata?: Record<string, unknown>
): PerformanceTrace {
  const trace: PerformanceTrace = {
    id: `trace_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    name,
    startTime: performance.now(),
    metadata: metadata || {},
    spans: [],
    tags: []
  };

  dashboard.activeTrace = trace;
  return trace;
}

/**
 * End a performance trace
 */
export function endTrace(dashboard: TelemetryDashboard, traceId: string): void {
  const trace = dashboard.traces.find(t => t.id === traceId) || dashboard.activeTrace;
  
  if (!trace || trace.id !== traceId) {
    return;
  }

  trace.endTime = performance.now();
  trace.duration = trace.endTime - trace.startTime;

  // Add to traces array
  if (!dashboard.traces.some(t => t.id === traceId)) {
    dashboard.traces.push(trace);

    // Trim old traces
    if (dashboard.traces.length > dashboard.config.maxTraces) {
      dashboard.traces = dashboard.traces.slice(-dashboard.config.maxTraces);
    }
  }

  if (dashboard.activeTrace?.id === traceId) {
    dashboard.activeTrace = undefined;
  }
}

/**
 * Add a span to a trace
 */
export function addSpan(
  trace: PerformanceTrace,
  name: string,
  startTime: number,
  endTime: number,
  attributes?: Record<string, string | number | boolean>,
  parentSpanId?: string
): PerformanceSpan {
  const span: PerformanceSpan = {
    id: `span_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    traceId: trace.id,
    name,
    startTime,
    endTime,
    duration: endTime - startTime,
    parentSpanId,
    attributes: attributes || {},
    events: []
  };

  trace.spans.push(span);
  return span;
}

/**
 * Export traces to JSON
 */
export function exportTracesJSON(dashboard: TelemetryDashboard): string {
  return JSON.stringify({
    traces: dashboard.traces,
    metadata: {
      exportedAt: new Date().toISOString(),
      totalTraces: dashboard.traces.length,
      startTime: dashboard.startTime,
      uptime: Date.now() - dashboard.startTime
    }
  }, null, 2);
}

/**
 * Export metrics to OTLP format
 */
export function exportToOTLP(dashboard: TelemetryDashboard): void {
  if (!dashboard.config.otlpEndpoint) {
    return;
  }

  const recentMetrics = dashboard.metrics.slice(-10); // Last 10 samples
  const payload = convertToOTLP(recentMetrics);

  // Send to OTLP endpoint
  fetch(dashboard.config.otlpEndpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload)
  }).catch(error => {
    console.error('Failed to export to OTLP:', error);
  });
}

/**
 * Convert metrics to OTLP format
 */
export function convertToOTLP(metrics: PerformanceMetrics[]): OTLPExportPayload {
  const otlpMetrics: OTLPMetric[] = [];

  // FPS metric
  otlpMetrics.push({
    name: 'canvas.performance.fps',
    description: 'Frames per second',
    unit: 'fps',
    type: 'gauge',
    dataPoints: metrics.map(m => ({
      timestamp: m.timestamp,
      value: m.fps
    }))
  });

  // Memory metric
  otlpMetrics.push({
    name: 'canvas.performance.memory.percent_used',
    description: 'Memory usage percentage',
    unit: '%',
    type: 'gauge',
    dataPoints: metrics.map(m => ({
      timestamp: m.timestamp,
      value: m.memory.percentUsed
    }))
  });

  // Render time metric
  otlpMetrics.push({
    name: 'canvas.performance.render_time',
    description: 'Rendering time',
    unit: 'ms',
    type: 'gauge',
    dataPoints: metrics.map(m => ({
      timestamp: m.timestamp,
      value: m.rendering.renderTime
    }))
  });

  // LCP metric
  otlpMetrics.push({
    name: 'canvas.performance.lcp',
    description: 'Largest Contentful Paint',
    unit: 'ms',
    type: 'gauge',
    dataPoints: metrics
      .filter(m => m.vitals.lcp)
      .map(m => ({
        timestamp: m.timestamp,
        value: m.vitals.lcp!
      }))
  });

  return {
    resourceMetrics: [{
      resource: {
        attributes: {
          'service.name': 'canvas-app',
          'service.version': '1.0.0'
        }
      },
      scopeMetrics: [{
        scope: {
          name: 'canvas-telemetry',
          version: '1.0.0'
        },
        metrics: otlpMetrics
      }]
    }]
  };
}

/**
 * Check thresholds and create alerts
 */
export function checkThresholds(
  dashboard: TelemetryDashboard,
  metrics: PerformanceMetrics
): void {
  const now = Date.now();

  dashboard.config.thresholds.forEach(threshold => {
    if (!threshold.enabled) {
      return;
    }

    // Check cooldown period
    if (threshold.lastTriggered && threshold.cooldownPeriod) {
      if (now - threshold.lastTriggered < threshold.cooldownPeriod) {
        return;
      }
    }

    const value = getMetricValue(metrics, threshold.metric);
    if (value === undefined) {
      return;
    }

    const breached = checkThresholdBreach(value, threshold.operator, threshold.value);

    if (breached) {
      const alert: PerformanceAlert = {
        id: `alert_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        thresholdId: threshold.id,
        timestamp: now,
        severity: threshold.severity,
        metric: threshold.metric,
        actualValue: value,
        thresholdValue: threshold.value,
        message: `${threshold.metric} ${threshold.operator} ${threshold.value} (actual: ${value.toFixed(2)})`,
        acknowledged: false
      };

      dashboard.alerts.push(alert);
      threshold.lastTriggered = now;

      // Trim old alerts
      if (dashboard.alerts.length > dashboard.config.maxAlerts) {
        dashboard.alerts = dashboard.alerts.slice(-dashboard.config.maxAlerts);
      }

      // Emit event (can be listened to by UI)
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('telemetry-alert', { detail: alert }));
      }
    }
  });
}

/**
 * Acknowledge an alert
 */
export function acknowledgeAlert(
  dashboard: TelemetryDashboard,
  alertId: string,
  acknowledgedBy?: string
): void {
  const alert = dashboard.alerts.find(a => a.id === alertId);
  if (alert) {
    alert.acknowledged = true;
    alert.acknowledgedAt = Date.now();
    alert.acknowledgedBy = acknowledgedBy;
  }
}

/**
 * Get telemetry statistics
 */
export function getTelemetryStatistics(dashboard: TelemetryDashboard): TelemetryStatistics {
  const metrics = dashboard.metrics;
  
  const alertsBySeverity: Record<string, number> = {
    info: 0,
    warning: 0,
    error: 0,
    critical: 0
  };

  dashboard.alerts.forEach(a => {
    alertsBySeverity[a.severity]++;
  });

  const fpsSamples = metrics.map(m => m.fps);
  const memorySamples = metrics.map(m => m.memory.percentUsed);
  const renderTimeSamples = metrics.map(m => m.rendering.renderTime);
  const longTasks = metrics.reduce((sum, m) => sum + m.interaction.longTasks, 0);

  return {
    totalSamples: metrics.length,
    totalTraces: dashboard.traces.length,
    totalAlerts: dashboard.alerts.length,
    averageFPS: fpsSamples.length > 0
      ? fpsSamples.reduce((a, b) => a + b, 0) / fpsSamples.length
      : 0,
    averageMemoryUsage: memorySamples.length > 0
      ? memorySamples.reduce((a, b) => a + b, 0) / memorySamples.length
      : 0,
    peakMemoryUsage: memorySamples.length > 0
      ? Math.max(...memorySamples)
      : 0,
    totalLongTasks: longTasks,
    averageRenderTime: renderTimeSamples.length > 0
      ? renderTimeSamples.reduce((a, b) => a + b, 0) / renderTimeSamples.length
      : 0,
    alertsBySeverity,
    uptime: Date.now() - dashboard.startTime
  };
}

/**
 * Toggle dev mode overlay
 */
export function toggleDevMode(dashboard: TelemetryDashboard): void {
  dashboard.config.devModeEnabled = !dashboard.config.devModeEnabled;
}

/**
 * Clear all collected data
 */
export function clearTelemetryData(dashboard: TelemetryDashboard): void {
  dashboard.metrics = [];
  dashboard.traces = [];
  dashboard.alerts = [];
  dashboard.activeTrace = undefined;
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 *
 */
function collectMemoryMetrics(): MemoryMetrics {
  if ('memory' in performance && (performance as unknown).memory) {
    const mem = (performance as unknown).memory;
    return {
      usedJSHeapSize: mem.usedJSHeapSize || 0,
      totalJSHeapSize: mem.totalJSHeapSize || 0,
      jsHeapSizeLimit: mem.jsHeapSizeLimit || 0,
      percentUsed: mem.jsHeapSizeLimit > 0
        ? (mem.usedJSHeapSize / mem.jsHeapSizeLimit) * 100
        : 0
    };
  }

  return {
    usedJSHeapSize: 0,
    totalJSHeapSize: 0,
    jsHeapSizeLimit: 0,
    percentUsed: 0
  };
}

/**
 *
 */
function collectRenderingMetrics(): RenderingMetrics {
  const elements = document.querySelectorAll('*').length;
  
  // Get paint/layout timing from Performance API
  const paintEntries = performance.getEntriesByType('paint');
  const paintTime = paintEntries.length > 0
    ? Math.max(...paintEntries.map(e => e.startTime))
    : 0;

  return {
    elementCount: elements,
    visibleElements: 0, // Would need intersection observer
    renderTime: 0, // Would be measured per render cycle
    paintTime,
    layoutTime: 0,
    scriptTime: 0,
    idleTime: 0
  };
}

/**
 *
 */
function collectInteractionMetrics(): InteractionMetrics {
  // Would integrate with PerformanceObserver for real metrics
  return {
    inputDelay: 0,
    eventLatency: 0,
    interactionCount: 0,
    longTasks: 0,
    blockedTime: 0
  };
}

/**
 *
 */
function collectNetworkMetrics(): NetworkMetrics {
  const connection = (navigator as unknown).connection || (navigator as unknown).mozConnection || (navigator as unknown).webkitConnection;
  
  return {
    online: navigator.onLine,
    effectiveType: connection?.effectiveType,
    downlink: connection?.downlink,
    rtt: connection?.rtt,
    saveData: connection?.saveData
  };
}

/**
 *
 */
function collectWebVitals(): WebVitals {
  const vitals: WebVitals = {};

  // LCP
  const lcpEntries = performance.getEntriesByType('largest-contentful-paint');
  if (lcpEntries.length > 0) {
    vitals.lcp = lcpEntries[lcpEntries.length - 1].startTime;
  }

  // FCP
  const fcpEntries = performance.getEntriesByType('paint')
    .filter(e => e.name === 'first-contentful-paint');
  if (fcpEntries.length > 0) {
    vitals.fcp = fcpEntries[0].startTime;
  }

  // TTFB
  const navigationEntries = performance.getEntriesByType('navigation');
  if (navigationEntries.length > 0) {
    const nav = navigationEntries[0] as PerformanceNavigationTiming;
    vitals.ttfb = nav.responseStart - nav.requestStart;
  }

  return vitals;
}

let lastFrameTime = performance.now();
let frameCount = 0;
let currentFPS = 60;

/**
 *
 */
function calculateFPS(): number {
  frameCount++;
  const now = performance.now();
  const elapsed = now - lastFrameTime;

  if (elapsed >= 1000) {
    currentFPS = Math.round((frameCount * 1000) / elapsed);
    frameCount = 0;
    lastFrameTime = now;
  }

  return currentFPS;
}

/**
 *
 */
function getMetricValue(metrics: PerformanceMetrics, path: string): number | undefined {
  const parts = path.split('.');
  let value: unknown = metrics;

  for (const part of parts) {
    value = (value as Record<string, unknown>)?.[part];
    if (value === undefined) {
      return undefined;
    }
  }

  return typeof value === 'number' ? value : undefined;
}

/**
 *
 */
function checkThresholdBreach(
  value: number,
  operator: string,
  threshold: number
): boolean {
  switch (operator) {
    case 'gt':
      return value > threshold;
    case 'lt':
      return value < threshold;
    case 'gte':
      return value >= threshold;
    case 'lte':
      return value <= threshold;
    case 'eq':
      return value === threshold;
    default:
      return false;
  }
}
