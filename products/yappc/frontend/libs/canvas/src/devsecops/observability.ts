/**
 * Observability & Metrics Overlay System
 * 
 * Supports:
 * - Prometheus metrics integration
 * - Grafana dashboard embedding
 * - SLO/SLI tracking and visualization
 * - Alert visualization on canvas
 * - Time-series data overlay
 * - Health status indicators
 */

// Types

/**
 *
 */
export type MetricType = 'counter' | 'gauge' | 'histogram' | 'summary';
/**
 *
 */
export type AlertSeverity = 'info' | 'warning' | 'critical';
/**
 *
 */
export type HealthStatus = 'healthy' | 'degraded' | 'unhealthy' | 'unknown';

/**
 *
 */
export interface ObservabilityConfig {
  enableMetrics: boolean;
  enableAlerts: boolean;
  enableHealthChecks: boolean;
  refreshInterval: number; // milliseconds
  retentionPeriod: number; // milliseconds
  alertThresholds: {
    warning: number;
    critical: number;
  };
}

/**
 *
 */
export interface PrometheusMetric {
  name: string;
  type: MetricType;
  help: string;
  labels: Record<string, string>;
  value: number;
  timestamp: Date;
  metadata: {
    unit?: string;
    aggregation?: 'sum' | 'avg' | 'min' | 'max' | 'count';
  };
}

/**
 *
 */
export interface TimeSeriesDataPoint {
  timestamp: Date;
  value: number;
  labels?: Record<string, string>;
}

/**
 *
 */
export interface TimeSeries {
  metric: string;
  dataPoints: TimeSeriesDataPoint[];
  metadata: {
    unit?: string;
    interval?: number; // seconds
    aggregation?: string;
  };
}

/**
 *
 */
export interface GrafanaDashboard {
  id: string;
  title: string;
  url: string;
  panels: GrafanaPanel[];
  metadata: {
    tags?: string[];
    refresh?: string;
    timeRange?: {
      from: string;
      to: string;
    };
  };
}

/**
 *
 */
export interface GrafanaPanel {
  id: string;
  title: string;
  type: 'graph' | 'gauge' | 'table' | 'heatmap' | 'stat';
  targets: string[]; // Prometheus queries
  position: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
}

/**
 *
 */
export interface ServiceLevelObjective {
  id: string;
  name: string;
  description: string;
  target: number; // percentage (0-100)
  window: number; // milliseconds
  sli: ServiceLevelIndicator;
  compliance: number; // actual percentage
  metadata: {
    priority?: 'low' | 'medium' | 'high' | 'critical';
    owner?: string;
    errorBudget?: number; // percentage remaining
  };
}

/**
 *
 */
export interface ServiceLevelIndicator {
  id: string;
  name: string;
  metric: string;
  query: string;
  threshold: number;
  comparison: '>' | '<' | '>=' | '<=' | '==' | '!=';
  current: number;
  metadata: {
    unit?: string;
    category?: 'availability' | 'latency' | 'throughput' | 'quality';
  };
}

/**
 *
 */
export interface Alert {
  id: string;
  name: string;
  severity: AlertSeverity;
  message: string;
  timestamp: Date;
  source: string;
  labels: Record<string, string>;
  annotations: Record<string, string>;
  status: 'firing' | 'resolved' | 'silenced';
  elementId?: string; // Canvas element this alert relates to
  metadata: {
    runbookUrl?: string;
    dashboardUrl?: string;
    silenceUntil?: Date;
  };
}

/**
 *
 */
export interface HealthCheck {
  id: string;
  name: string;
  status: HealthStatus;
  lastCheck: Date;
  responseTime?: number; // milliseconds
  details?: string;
  checks: {
    name: string;
    status: HealthStatus;
    message?: string;
  }[];
}

/**
 *
 */
export interface ObservabilityOverlay {
  documentId: string;
  metrics: Map<string, PrometheusMetric[]>;
  timeSeries: Map<string, TimeSeries>;
  dashboards: GrafanaDashboard[];
  slos: ServiceLevelObjective[];
  alerts: Alert[];
  healthChecks: Map<string, HealthCheck>;
  metadata: {
    lastUpdate: Date;
    dataRetention: number;
  };
}

// Main Functions

/**
 * Create observability configuration
 */
export function createObservabilityConfig(
  overrides?: Partial<ObservabilityConfig>
): ObservabilityConfig {
  return {
    enableMetrics: true,
    enableAlerts: true,
    enableHealthChecks: true,
    refreshInterval: 15000, // 15 seconds
    retentionPeriod: 3600000, // 1 hour
    alertThresholds: {
      warning: 0.8,
      critical: 0.95,
    },
    ...overrides,
  };
}

/**
 * Create observability overlay
 */
export function createObservabilityOverlay(documentId: string): ObservabilityOverlay {
  return {
    documentId,
    metrics: new Map(),
    timeSeries: new Map(),
    dashboards: [],
    slos: [],
    alerts: [],
    healthChecks: new Map(),
    metadata: {
      lastUpdate: new Date(),
      dataRetention: 3600000, // 1 hour
    },
  };
}

/**
 * Parse Prometheus metrics from text format
 */
export function parsePrometheusMetrics(metricsText: string): PrometheusMetric[] {
  const metrics: PrometheusMetric[] = [];
  const lines = metricsText.split('\n');

  let currentHelp = '';
  let currentType: MetricType = 'gauge';

  for (const line of lines) {
    const trimmed = line.trim();

    // Skip empty lines and comments (except TYPE and HELP)
    if (!trimmed || (trimmed.startsWith('#') && !trimmed.includes('TYPE') && !trimmed.includes('HELP'))) {
      continue;
    }

    // Parse HELP
    if (trimmed.startsWith('# HELP')) {
      currentHelp = trimmed.replace(/# HELP \S+ /, '');
      continue;
    }

    // Parse TYPE
    if (trimmed.startsWith('# TYPE')) {
      const typeMatch = trimmed.match(/# TYPE \S+ (\w+)/);
      if (typeMatch) {
        currentType = typeMatch[1] as MetricType;
      }
      continue;
    }

    // Parse metric line
    const metricMatch = trimmed.match(/^([a-zA-Z_:][a-zA-Z0-9_:]*)((?:\{[^}]+\})?) (.+)$/);
    if (!metricMatch) continue;

    const [, name, labelsStr, valueStr] = metricMatch;

    // Parse labels
    const labels: Record<string, string> = {};
    if (labelsStr) {
      const labelsMatch = labelsStr.matchAll(/(\w+)="([^"]+)"/g);
      for (const [, key, value] of labelsMatch) {
        labels[key] = value;
      }
    }

    // Parse value and timestamp
    const valueParts = valueStr.split(' ');
    const value = parseFloat(valueParts[0]);
    const timestamp = valueParts[1] ? new Date(parseInt(valueParts[1])) : new Date();

    metrics.push({
      name,
      type: currentType,
      help: currentHelp,
      labels,
      value,
      timestamp,
      metadata: {},
    });
  }

  return metrics;
}

/**
 * Add metrics to overlay
 */
export function addMetrics(
  overlay: ObservabilityOverlay,
  metrics: PrometheusMetric[]
): ObservabilityOverlay {
  const updatedMetrics = new Map(overlay.metrics);

  for (const metric of metrics) {
    const existing = updatedMetrics.get(metric.name) || [];
    updatedMetrics.set(metric.name, [...existing, metric]);
  }

  return {
    ...overlay,
    metrics: updatedMetrics,
    metadata: {
      ...overlay.metadata,
      lastUpdate: new Date(),
    },
  };
}

/**
 * Query metrics by name and labels
 */
export function queryMetrics(
  overlay: ObservabilityOverlay,
  metricName: string,
  labelFilters?: Record<string, string>
): PrometheusMetric[] {
  const metrics = overlay.metrics.get(metricName) || [];

  if (!labelFilters) {
    return metrics;
  }

  return metrics.filter(metric => {
    return Object.entries(labelFilters).every(
      ([key, value]) => metric.labels[key] === value
    );
  });
}

/**
 * Create time series from metrics
 */
export function createTimeSeries(
  metrics: PrometheusMetric[],
  metricName: string,
  options?: {
    interval?: number;
    aggregation?: 'sum' | 'avg' | 'min' | 'max' | 'count';
  }
): TimeSeries {
  const dataPoints: TimeSeriesDataPoint[] = metrics
    .filter(m => m.name === metricName)
    .map(m => ({
      timestamp: m.timestamp,
      value: m.value,
      labels: m.labels,
    }))
    .sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime());

  // Apply aggregation if specified
  let aggregatedPoints = dataPoints;
  if (options?.aggregation && options?.interval) {
    aggregatedPoints = aggregateDataPoints(dataPoints, options.interval, options.aggregation);
  }

  return {
    metric: metricName,
    dataPoints: aggregatedPoints,
    metadata: {
      unit: metrics[0]?.metadata.unit,
      interval: options?.interval,
      aggregation: options?.aggregation,
    },
  };
}

/**
 * Aggregate data points by interval
 */
function aggregateDataPoints(
  dataPoints: TimeSeriesDataPoint[],
  intervalSeconds: number,
  aggregation: 'sum' | 'avg' | 'min' | 'max' | 'count'
): TimeSeriesDataPoint[] {
  if (dataPoints.length === 0) return [];

  const intervalMs = intervalSeconds * 1000;
  const buckets = new Map<number, number[]>();

  // Group data points into time buckets
  for (const point of dataPoints) {
    const bucket = Math.floor(point.timestamp.getTime() / intervalMs) * intervalMs;
    if (!buckets.has(bucket)) {
      buckets.set(bucket, []);
    }
    buckets.get(bucket)!.push(point.value);
  }

  // Aggregate each bucket
  const aggregated: TimeSeriesDataPoint[] = [];
  for (const [bucket, values] of buckets) {
    let value: number;

    switch (aggregation) {
      case 'sum':
        value = values.reduce((sum, v) => sum + v, 0);
        break;
      case 'avg':
        value = values.reduce((sum, v) => sum + v, 0) / values.length;
        break;
      case 'min':
        value = Math.min(...values);
        break;
      case 'max':
        value = Math.max(...values);
        break;
      case 'count':
        value = values.length;
        break;
    }

    aggregated.push({
      timestamp: new Date(bucket),
      value,
    });
  }

  return aggregated;
}

/**
 * Add time series to overlay
 */
export function addTimeSeries(
  overlay: ObservabilityOverlay,
  timeSeries: TimeSeries
): ObservabilityOverlay {
  const updatedTimeSeries = new Map(overlay.timeSeries);
  updatedTimeSeries.set(timeSeries.metric, timeSeries);

  return {
    ...overlay,
    timeSeries: updatedTimeSeries,
  };
}

/**
 * Create Grafana dashboard
 */
export function createGrafanaDashboard(
  id: string,
  title: string,
  panels: GrafanaPanel[]
): GrafanaDashboard {
  return {
    id,
    title,
    url: `https://grafana.example.com/d/${id}`,
    panels,
    metadata: {
      tags: ['devSecOps'],
      refresh: '5s',
      timeRange: {
        from: 'now-1h',
        to: 'now',
      },
    },
  };
}

/**
 * Add dashboard to overlay
 */
export function addDashboard(
  overlay: ObservabilityOverlay,
  dashboard: GrafanaDashboard
): ObservabilityOverlay {
  return {
    ...overlay,
    dashboards: [...overlay.dashboards, dashboard],
  };
}

/**
 * Create Service Level Objective
 */
export function createSLO(
  name: string,
  description: string,
  target: number,
  window: number,
  sli: ServiceLevelIndicator
): ServiceLevelObjective {
  // Calculate compliance
  const compliance = calculateSLICompliance(sli);

  // Calculate error budget
  const errorBudget = Math.max(0, 100 - target - (100 - compliance));

  return {
    id: `slo-${Date.now()}`,
    name,
    description,
    target,
    window,
    sli,
    compliance,
    metadata: {
      priority: compliance < target ? 'critical' : 'medium',
      errorBudget,
    },
  };
}

/**
 * Calculate SLI compliance
 */
function calculateSLICompliance(sli: ServiceLevelIndicator): number {
  let compliance = 0;

  switch (sli.comparison) {
    case '>':
      compliance = sli.current > sli.threshold ? 100 : 0;
      break;
    case '<':
      compliance = sli.current < sli.threshold ? 100 : 0;
      break;
    case '>=':
      compliance = sli.current >= sli.threshold ? 100 : 0;
      break;
    case '<=':
      compliance = sli.current <= sli.threshold ? 100 : 0;
      break;
    case '==':
      compliance = sli.current === sli.threshold ? 100 : 0;
      break;
    case '!=':
      compliance = sli.current !== sli.threshold ? 100 : 0;
      break;
  }

  return compliance;
}

/**
 * Add SLO to overlay
 */
export function addSLO(
  overlay: ObservabilityOverlay,
  slo: ServiceLevelObjective
): ObservabilityOverlay {
  return {
    ...overlay,
    slos: [...overlay.slos, slo],
  };
}

/**
 * Update SLO compliance
 */
export function updateSLOCompliance(
  overlay: ObservabilityOverlay,
  sloId: string,
  currentValue: number
): ObservabilityOverlay {
  const updatedSLOs = overlay.slos.map(slo => {
    if (slo.id !== sloId) return slo;

    const updatedSLI = {
      ...slo.sli,
      current: currentValue,
    };

    const compliance = calculateSLICompliance(updatedSLI);
    const errorBudget = Math.max(0, 100 - slo.target - (100 - compliance));

    return {
      ...slo,
      sli: updatedSLI,
      compliance,
      metadata: {
        ...slo.metadata,
        priority: (compliance < slo.target ? 'critical' : 'medium') as 'low' | 'medium' | 'high' | 'critical',
        errorBudget,
      },
    };
  });

  return {
    ...overlay,
    slos: updatedSLOs,
  };
}

/**
 * Create alert
 */
export function createAlert(
  name: string,
  severity: AlertSeverity,
  message: string,
  source: string,
  labels?: Record<string, string>,
  elementId?: string
): Alert {
  return {
    id: `alert-${Date.now()}`,
    name,
    severity,
    message,
    timestamp: new Date(),
    source,
    labels: labels || {},
    annotations: {},
    status: 'firing',
    elementId,
    metadata: {},
  };
}

/**
 * Add alert to overlay
 */
export function addAlert(
  overlay: ObservabilityOverlay,
  alert: Alert
): ObservabilityOverlay {
  return {
    ...overlay,
    alerts: [...overlay.alerts, alert],
  };
}

/**
 * Resolve alert
 */
export function resolveAlert(
  overlay: ObservabilityOverlay,
  alertId: string
): ObservabilityOverlay {
  const updatedAlerts = overlay.alerts.map(alert =>
    alert.id === alertId
      ? { ...alert, status: 'resolved' as const }
      : alert
  );

  return {
    ...overlay,
    alerts: updatedAlerts,
  };
}

/**
 * Silence alert
 */
export function silenceAlert(
  overlay: ObservabilityOverlay,
  alertId: string,
  until: Date
): ObservabilityOverlay {
  const updatedAlerts = overlay.alerts.map(alert =>
    alert.id === alertId
      ? {
          ...alert,
          status: 'silenced' as const,
          metadata: {
            ...alert.metadata,
            silenceUntil: until,
          },
        }
      : alert
  );

  return {
    ...overlay,
    alerts: updatedAlerts,
  };
}

/**
 * Get active alerts
 */
export function getActiveAlerts(overlay: ObservabilityOverlay): Alert[] {
  return overlay.alerts.filter(alert => alert.status === 'firing');
}

/**
 * Get alerts by severity
 */
export function getAlertsBySeverity(
  overlay: ObservabilityOverlay,
  severity: AlertSeverity
): Alert[] {
  return overlay.alerts.filter(alert => alert.severity === severity);
}

/**
 * Create health check
 */
export function createHealthCheck(
  name: string,
  checks: { name: string; status: HealthStatus; message?: string }[]
): HealthCheck {
  // Overall status is worst individual check status
  const statusPriority: Record<HealthStatus, number> = {
    unhealthy: 0,
    degraded: 1,
    unknown: 2,
    healthy: 3,
  };

  const overallStatus = checks.reduce((worst, check) => {
    return statusPriority[check.status] < statusPriority[worst]
      ? check.status
      : worst;
  }, 'healthy' as HealthStatus);

  return {
    id: `health-${Date.now()}`,
    name,
    status: overallStatus,
    lastCheck: new Date(),
    checks,
  };
}

/**
 * Add health check to overlay
 */
export function addHealthCheck(
  overlay: ObservabilityOverlay,
  healthCheck: HealthCheck
): ObservabilityOverlay {
  const updatedHealthChecks = new Map(overlay.healthChecks);
  updatedHealthChecks.set(healthCheck.id, healthCheck);

  return {
    ...overlay,
    healthChecks: updatedHealthChecks,
  };
}

/**
 * Update health check
 */
export function updateHealthCheck(
  overlay: ObservabilityOverlay,
  healthCheckId: string,
  status: HealthStatus,
  responseTime?: number
): ObservabilityOverlay {
  const healthCheck = overlay.healthChecks.get(healthCheckId);
  if (!healthCheck) return overlay;

  const updatedHealthCheck: HealthCheck = {
    ...healthCheck,
    status,
    lastCheck: new Date(),
    responseTime,
  };

  const updatedHealthChecks = new Map(overlay.healthChecks);
  updatedHealthChecks.set(healthCheckId, updatedHealthCheck);

  return {
    ...overlay,
    healthChecks: updatedHealthChecks,
  };
}

/**
 * Get unhealthy services
 */
export function getUnhealthyServices(overlay: ObservabilityOverlay): HealthCheck[] {
  return Array.from(overlay.healthChecks.values()).filter(
    check => check.status === 'unhealthy' || check.status === 'degraded'
  );
}

/**
 * Get observability summary
 */
export function getObservabilitySummary(overlay: ObservabilityOverlay): {
  totalMetrics: number;
  totalTimeSeries: number;
  activeDashboards: number;
  totalSLOs: number;
  compliantSLOs: number;
  activeAlerts: number;
  criticalAlerts: number;
  unhealthyServices: number;
  totalHealthChecks: number;
} {
  const activeAlerts = getActiveAlerts(overlay);
  const criticalAlerts = getAlertsBySeverity(overlay, 'critical');
  const compliantSLOs = overlay.slos.filter(slo => slo.compliance >= slo.target);
  const unhealthyServices = getUnhealthyServices(overlay);

  return {
    totalMetrics: Array.from(overlay.metrics.values()).reduce((sum, metrics) => sum + metrics.length, 0),
    totalTimeSeries: overlay.timeSeries.size,
    activeDashboards: overlay.dashboards.length,
    totalSLOs: overlay.slos.length,
    compliantSLOs: compliantSLOs.length,
    activeAlerts: activeAlerts.length,
    criticalAlerts: criticalAlerts.length,
    unhealthyServices: unhealthyServices.length,
    totalHealthChecks: overlay.healthChecks.size,
  };
}

/**
 * Cleanup old metrics based on retention period
 */
export function cleanupOldMetrics(overlay: ObservabilityOverlay): ObservabilityOverlay {
  const cutoff = new Date(Date.now() - overlay.metadata.dataRetention);

  const updatedMetrics = new Map<string, PrometheusMetric[]>();
  overlay.metrics.forEach((metrics, name) => {
    const filtered = metrics.filter(m => m.timestamp >= cutoff);
    if (filtered.length > 0) {
      updatedMetrics.set(name, filtered);
    }
  });

  // Also cleanup resolved alerts older than retention
  const updatedAlerts = overlay.alerts.filter(
    alert => alert.status === 'firing' || alert.timestamp >= cutoff
  );

  return {
    ...overlay,
    metrics: updatedMetrics,
    alerts: updatedAlerts,
    metadata: {
      ...overlay.metadata,
      lastUpdate: new Date(),
    },
  };
}
