/**
 * Canvas Monitoring System
 *
 * Comprehensive monitoring infrastructure with Prometheus exporters,
 * Grafana dashboards, and alerting with runbooks.
 *
 * @module operations/monitoring
 */

/**
 * Metric types for monitoring
 */
export type MetricType = 'counter' | 'gauge' | 'histogram' | 'summary';

/**
 * Alert severity levels
 */
export type AlertSeverity = 'info' | 'warning' | 'error' | 'critical';

/**
 * Alert status states
 */
export type AlertStatus = 'firing' | 'resolved' | 'acknowledged' | 'silenced';

/**
 * Dashboard panel types
 */
export type PanelType = 'graph' | 'stat' | 'table' | 'heatmap' | 'gauge';

/**
 * Metric definition
 */
export interface MetricDefinition {
  /** Metric name */
  name: string;
  /** Metric type */
  type: MetricType;
  /** Help text */
  help: string;
  /** Label names */
  labels: string[];
  /** Buckets for histogram (optional) */
  buckets?: number[];
  /** Quantiles for summary (optional) */
  quantiles?: number[];
}

/**
 * Metric sample with labels and value
 */
export interface MetricSample {
  /** Metric name */
  name: string;
  /** Label key-value pairs */
  labels: Record<string, string>;
  /** Metric value */
  value: number;
  /** Timestamp */
  timestamp: Date;
}

/**
 * Prometheus exporter configuration
 */
export interface PrometheusExporter {
  /** Exporter ID */
  id: string;
  /** Metric prefix */
  prefix: string;
  /** Registered metrics */
  metrics: MetricDefinition[];
  /** Current samples */
  samples: MetricSample[];
  /** Exporter endpoint */
  endpoint: string;
  /** Scrape interval (ms) */
  scrapeInterval: number;
  /** Created timestamp */
  createdAt: Date;
}

/**
 * Alert rule definition
 */
export interface AlertRule {
  /** Rule ID */
  id: string;
  /** Alert name */
  name: string;
  /** PromQL expression */
  expr: string;
  /** Evaluation duration */
  for: string;
  /** Alert severity */
  severity: AlertSeverity;
  /** Alert labels */
  labels: Record<string, string>;
  /** Alert annotations */
  annotations: Record<string, string>;
  /** Runbook URL */
  runbookUrl?: string;
  /** Created timestamp */
  createdAt: Date;
}

/**
 * Alert instance
 */
export interface AlertInstance {
  /** Instance ID */
  id: string;
  /** Rule ID that triggered this alert */
  ruleId: string;
  /** Alert status */
  status: AlertStatus;
  /** Alert labels */
  labels: Record<string, string>;
  /** Alert value that triggered */
  value: number;
  /** Fired timestamp */
  firedAt: Date;
  /** Resolved timestamp */
  resolvedAt?: Date;
  /** Acknowledged timestamp */
  acknowledgedAt?: Date;
  /** Acknowledged by */
  acknowledgedBy?: string;
  /** Silence duration (ms) */
  silenceDuration?: number;
}

/**
 * Dashboard definition
 */
export interface Dashboard {
  /** Dashboard ID */
  id: string;
  /** Dashboard title */
  title: string;
  /** Dashboard description */
  description: string;
  /** Dashboard tags */
  tags: string[];
  /** Dashboard panels */
  panels: DashboardPanel[];
  /** Refresh interval (ms) */
  refreshInterval: number;
  /** Time range */
  timeRange: string;
  /** Created timestamp */
  createdAt: Date;
  /** Updated timestamp */
  updatedAt: Date;
}

/**
 * Dashboard panel configuration
 */
export interface DashboardPanel {
  /** Panel ID */
  id: string;
  /** Panel title */
  title: string;
  /** Panel type */
  type: PanelType;
  /** Panel position */
  gridPos: { x: number; y: number; w: number; h: number };
  /** Data source query */
  query: string;
  /** Visualization options */
  options: Record<string, unknown>;
  /** Thresholds */
  thresholds?: Array<{ value: number; color: string }>;
}

/**
 * Runbook definition
 */
export interface Runbook {
  /** Runbook ID */
  id: string;
  /** Runbook title */
  title: string;
  /** Alert rule IDs this runbook covers */
  alertRuleIds: string[];
  /** Problem description */
  problem: string;
  /** Impact assessment */
  impact: string;
  /** Diagnosis steps */
  diagnosis: string[];
  /** Resolution steps */
  resolution: string[];
  /** Prevention measures */
  prevention: string[];
  /** Related documentation links */
  relatedDocs: string[];
  /** Created timestamp */
  createdAt: Date;
  /** Updated timestamp */
  updatedAt: Date;
}

/**
 * Monitoring manager configuration
 */
export interface MonitoringManagerConfig {
  /** Default scrape interval (ms) */
  defaultScrapeInterval?: number;
  /** Default alert evaluation interval (ms) */
  defaultAlertEvalInterval?: number;
  /** Alert retention period (days) */
  alertRetentionDays?: number;
  /** Enable synthetic checks */
  enableSyntheticChecks?: boolean;
}

/**
 * Monitoring Manager
 *
 * Manages monitoring infrastructure including Prometheus exporters,
 * Grafana dashboards, and alerting with runbooks.
 *
 * @example
 * ```typescript
 * const manager = new MonitoringManager();
 *
 * // Create Prometheus exporter
 * const exporter = manager.createExporter({
 *   prefix: 'canvas',
 *   endpoint: '/metrics',
 *   scrapeInterval: 15000
 * });
 *
 * // Register metric
 * manager.registerMetric(exporter.id, {
 *   name: 'render_fps',
 *   type: 'gauge',
 *   help: 'Canvas render FPS',
 *   labels: ['canvas_id']
 * });
 *
 * // Record metric sample
 * manager.recordMetric(exporter.id, 'render_fps', 60, { canvas_id: 'main' });
 *
 * // Create alert rule
 * const rule = manager.createAlertRule({
 *   name: 'LowFPS',
 *   expr: 'canvas_render_fps < 30',
 *   for: '5m',
 *   severity: 'warning',
 *   runbookUrl: 'https://docs.example.com/runbooks/low-fps'
 * });
 *
 * // Create dashboard
 * const dashboard = manager.createDashboard({
 *   title: 'Canvas Performance',
 *   panels: [
 *     {
 *       title: 'Render FPS',
 *       type: 'graph',
 *       query: 'canvas_render_fps'
 *     }
 *   ]
 * });
 * ```
 */
export class MonitoringManager {
  private exporters: Map<string, PrometheusExporter> = new Map();
  private alertRules: Map<string, AlertRule> = new Map();
  private alertInstances: Map<string, AlertInstance> = new Map();
  private dashboards: Map<string, Dashboard> = new Map();
  private runbooks: Map<string, Runbook> = new Map();
  private config: MonitoringConfig;
  private ruleCounter = 0;
  private instanceCounter = 0;
  private dashboardCounter = 0;

  /**
   *
   */
  constructor(config: MonitoringManagerConfig = {}) {
    this.config = {
      defaultScrapeInterval: config.defaultScrapeInterval ?? 15000,
      defaultAlertEvalInterval: config.defaultAlertEvalInterval ?? 30000,
      alertRetentionDays: config.alertRetentionDays ?? 90,
      enableSyntheticChecks: config.enableSyntheticChecks ?? true,
    };
  }

  /**
   * Create Prometheus exporter
   */
  createExporter(
    params: Omit<PrometheusExporter, 'id' | 'metrics' | 'samples' | 'createdAt'>
  ): PrometheusExporter {
    const exporter: PrometheusExporter = {
      ...params,
      id: this.generateExporterId(params.prefix),
      metrics: [],
      samples: [],
      scrapeInterval: params.scrapeInterval || this.config.defaultScrapeInterval,
      createdAt: new Date(),
    };

    this.exporters.set(exporter.id, exporter);
    return exporter;
  }

  /**
   * Get exporter by ID
   */
  getExporter(id: string): PrometheusExporter | null {
    return this.exporters.get(id) ?? null;
  }

  /**
   * Get all exporters
   */
  getAllExporters(): PrometheusExporter[] {
    return Array.from(this.exporters.values());
  }

  /**
   * Register metric in exporter
   */
  registerMetric(exporterId: string, metric: MetricDefinition): void {
    const exporter = this.exporters.get(exporterId);
    if (!exporter) {
      throw new Error(`Exporter ${exporterId} not found`);
    }

    // Check if metric already exists
    if (exporter.metrics.some((m) => m.name === metric.name)) {
      throw new Error(`Metric ${metric.name} already registered`);
    }

    exporter.metrics.push(metric);
  }

  /**
   * Record metric sample
   */
  recordMetric(
    exporterId: string,
    metricName: string,
    value: number,
    labels: Record<string, string> = {}
  ): void {
    const exporter = this.exporters.get(exporterId);
    if (!exporter) {
      throw new Error(`Exporter ${exporterId} not found`);
    }

    const metric = exporter.metrics.find((m) => m.name === metricName);
    if (!metric) {
      throw new Error(`Metric ${metricName} not registered`);
    }

    // Validate labels
    const missingLabels = metric.labels.filter((l) => !(l in labels));
    if (missingLabels.length > 0) {
      throw new Error(`Missing required labels: ${missingLabels.join(', ')}`);
    }

    const sample: MetricSample = {
      name: metricName,
      labels,
      value,
      timestamp: new Date(),
    };

    exporter.samples.push(sample);

    // Limit sample history (keep last 10000)
    if (exporter.samples.length > 10000) {
      exporter.samples = exporter.samples.slice(-10000);
    }
  }

  /**
   * Export metrics in Prometheus format
   */
  exportPrometheusMetrics(exporterId: string): string {
    const exporter = this.exporters.get(exporterId);
    if (!exporter) {
      throw new Error(`Exporter ${exporterId} not found`);
    }

    const lines: string[] = [];

    // Group samples by metric
    const metricGroups = new Map<string, MetricSample[]>();
    for (const sample of exporter.samples) {
      const group = metricGroups.get(sample.name) || [];
      group.push(sample);
      metricGroups.set(sample.name, group);
    }

    // Export each metric
    for (const metric of exporter.metrics) {
      const samples = metricGroups.get(metric.name) || [];
      
      // Add help text
      lines.push(`# HELP ${exporter.prefix}_${metric.name} ${metric.help}`);
      lines.push(`# TYPE ${exporter.prefix}_${metric.name} ${metric.type}`);

      // Add samples
      for (const sample of samples) {
        const labelStr = Object.entries(sample.labels)
          .map(([k, v]) => `${k}="${v}"`)
          .join(',');
        
        const metricLine = labelStr
          ? `${exporter.prefix}_${metric.name}{${labelStr}} ${sample.value}`
          : `${exporter.prefix}_${metric.name} ${sample.value}`;
        
        lines.push(metricLine);
      }
    }

    return lines.join('\n');
  }

  /**
   * Create alert rule
   */
  createAlertRule(
    params: Omit<AlertRule, 'id' | 'createdAt' | 'labels' | 'annotations'> & {
      labels?: Record<string, string>;
      annotations?: Record<string, string>;
    }
  ): AlertRule {
    const rule: AlertRule = {
      ...params,
      id: this.generateAlertRuleId(params.name),
      labels: params.labels || {},
      annotations: params.annotations || {},
      createdAt: new Date(),
    };

    this.alertRules.set(rule.id, rule);
    return rule;
  }

  /**
   * Get alert rule by ID
   */
  getAlertRule(id: string): AlertRule | null {
    return this.alertRules.get(id) ?? null;
  }

  /**
   * Get all alert rules
   */
  getAllAlertRules(): AlertRule[] {
    return Array.from(this.alertRules.values());
  }

  /**
   * Get alert rules by severity
   */
  getAlertRulesBySeverity(severity: AlertSeverity): AlertRule[] {
    return Array.from(this.alertRules.values()).filter(
      (r) => r.severity === severity
    );
  }

  /**
   * Fire alert
   */
  fireAlert(
    ruleId: string,
    value: number,
    labels: Record<string, string> = {}
  ): AlertInstance {
    const rule = this.alertRules.get(ruleId);
    if (!rule) {
      throw new Error(`Alert rule ${ruleId} not found`);
    }

    const instance: AlertInstance = {
      id: this.generateAlertInstanceId(ruleId),
      ruleId,
      status: 'firing',
      labels: { ...rule.labels, ...labels },
      value,
      firedAt: new Date(),
    };

    this.alertInstances.set(instance.id, instance);
    return instance;
  }

  /**
   * Resolve alert
   */
  resolveAlert(instanceId: string): void {
    const instance = this.alertInstances.get(instanceId);
    if (!instance) {
      throw new Error(`Alert instance ${instanceId} not found`);
    }

    instance.status = 'resolved';
    instance.resolvedAt = new Date();
  }

  /**
   * Acknowledge alert
   */
  acknowledgeAlert(instanceId: string, acknowledgedBy: string): void {
    const instance = this.alertInstances.get(instanceId);
    if (!instance) {
      throw new Error(`Alert instance ${instanceId} not found`);
    }

    instance.status = 'acknowledged';
    instance.acknowledgedAt = new Date();
    instance.acknowledgedBy = acknowledgedBy;
  }

  /**
   * Silence alert
   */
  silenceAlert(instanceId: string, durationMs: number): void {
    const instance = this.alertInstances.get(instanceId);
    if (!instance) {
      throw new Error(`Alert instance ${instanceId} not found`);
    }

    instance.status = 'silenced';
    instance.silenceDuration = durationMs;
  }

  /**
   * Get alert instance
   */
  getAlertInstance(id: string): AlertInstance | null {
    return this.alertInstances.get(id) ?? null;
  }

  /**
   * Get all alert instances
   */
  getAllAlertInstances(): AlertInstance[] {
    return Array.from(this.alertInstances.values());
  }

  /**
   * Get firing alerts
   */
  getFiringAlerts(): AlertInstance[] {
    return Array.from(this.alertInstances.values()).filter(
      (a) => a.status === 'firing'
    );
  }

  /**
   * Get alerts by rule
   */
  getAlertsByRule(ruleId: string): AlertInstance[] {
    return Array.from(this.alertInstances.values()).filter(
      (a) => a.ruleId === ruleId
    );
  }

  /**
   * Create dashboard
   */
  createDashboard(
    params: Omit<Dashboard, 'id' | 'createdAt' | 'updatedAt' | 'refreshInterval' | 'timeRange'> & {
      refreshInterval?: number;
      timeRange?: string;
    }
  ): Dashboard {
    const dashboard: Dashboard = {
      ...params,
      id: this.generateDashboardId(params.title),
      refreshInterval: params.refreshInterval || 30000,
      timeRange: params.timeRange || '1h',
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    this.dashboards.set(dashboard.id, dashboard);
    return dashboard;
  }

  /**
   * Update dashboard
   */
  updateDashboard(
    id: string,
    updates: Partial<Omit<Dashboard, 'id' | 'createdAt' | 'updatedAt'>>
  ): Dashboard {
    const dashboard = this.dashboards.get(id);
    if (!dashboard) {
      throw new Error(`Dashboard ${id} not found`);
    }

    Object.assign(dashboard, updates);
    dashboard.updatedAt = new Date();

    return dashboard;
  }

  /**
   * Get dashboard by ID
   */
  getDashboard(id: string): Dashboard | null {
    return this.dashboards.get(id) ?? null;
  }

  /**
   * Get all dashboards
   */
  getAllDashboards(): Dashboard[] {
    return Array.from(this.dashboards.values());
  }

  /**
   * Get dashboards by tag
   */
  getDashboardsByTag(tag: string): Dashboard[] {
    return Array.from(this.dashboards.values()).filter((d) =>
      d.tags.includes(tag)
    );
  }

  /**
   * Export dashboard as JSON
   */
  exportDashboard(id: string): string {
    const dashboard = this.dashboards.get(id);
    if (!dashboard) {
      throw new Error(`Dashboard ${id} not found`);
    }

    return JSON.stringify(dashboard, null, 2);
  }

  /**
   * Create runbook
   */
  createRunbook(params: Omit<Runbook, 'id' | 'createdAt' | 'updatedAt'>): Runbook {
    const runbook: Runbook = {
      ...params,
      id: this.generateRunbookId(params.title),
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    this.runbooks.set(runbook.id, runbook);
    return runbook;
  }

  /**
   * Update runbook
   */
  updateRunbook(
    id: string,
    updates: Partial<Omit<Runbook, 'id' | 'createdAt' | 'updatedAt'>>
  ): Runbook {
    const runbook = this.runbooks.get(id);
    if (!runbook) {
      throw new Error(`Runbook ${id} not found`);
    }

    Object.assign(runbook, updates);
    runbook.updatedAt = new Date();

    return runbook;
  }

  /**
   * Get runbook by ID
   */
  getRunbook(id: string): Runbook | null {
    return this.runbooks.get(id) ?? null;
  }

  /**
   * Get runbook by alert rule
   */
  getRunbookByAlertRule(ruleId: string): Runbook | null {
    return (
      Array.from(this.runbooks.values()).find((r) =>
        r.alertRuleIds.includes(ruleId)
      ) ?? null
    );
  }

  /**
   * Get all runbooks
   */
  getAllRunbooks(): Runbook[] {
    return Array.from(this.runbooks.values());
  }

  /**
   * Export runbook as markdown
   */
  exportRunbookMarkdown(id: string): string {
    const runbook = this.runbooks.get(id);
    if (!runbook) {
      throw new Error(`Runbook ${id} not found`);
    }

    const lines: string[] = [
      `# ${runbook.title}`,
      '',
      '## Problem',
      runbook.problem,
      '',
      '## Impact',
      runbook.impact,
      '',
      '## Diagnosis',
      ...runbook.diagnosis.map((step, i) => `${i + 1}. ${step}`),
      '',
      '## Resolution',
      ...runbook.resolution.map((step, i) => `${i + 1}. ${step}`),
      '',
      '## Prevention',
      ...runbook.prevention.map((step, i) => `${i + 1}. ${step}`),
      '',
    ];

    if (runbook.relatedDocs.length > 0) {
      lines.push('## Related Documentation');
      lines.push(...runbook.relatedDocs.map((doc) => `- ${doc}`));
      lines.push('');
    }

    return lines.join('\n');
  }

  /**
   * Clean up old alerts
   */
  cleanupOldAlerts(): number {
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - this.config.alertRetentionDays);

    let removed = 0;

    for (const [id, instance] of this.alertInstances.entries()) {
      if (
        instance.status === 'resolved' &&
        instance.resolvedAt &&
        instance.resolvedAt < cutoffDate
      ) {
        this.alertInstances.delete(id);
        removed++;
      }
    }

    return removed;
  }

  /**
   * Get monitoring statistics
   */
  getMonitoringStats(): {
    exporters: number;
    metrics: number;
    samples: number;
    alertRules: number;
    firingAlerts: number;
    dashboards: number;
    runbooks: number;
  } {
    const exporters = this.exporters.size;
    const metrics = Array.from(this.exporters.values()).reduce(
      (sum, e) => sum + e.metrics.length,
      0
    );
    const samples = Array.from(this.exporters.values()).reduce(
      (sum, e) => sum + e.samples.length,
      0
    );
    const alertRules = this.alertRules.size;
    const firingAlerts = this.getFiringAlerts().length;
    const dashboards = this.dashboards.size;
    const runbooks = this.runbooks.size;

    return {
      exporters,
      metrics,
      samples,
      alertRules,
      firingAlerts,
      dashboards,
      runbooks,
    };
  }

  /**
   * Reset manager state
   */
  reset(): void {
    this.exporters.clear();
    this.alertRules.clear();
    this.alertInstances.clear();
    this.dashboards.clear();
    this.runbooks.clear();
  }

  // Private helper methods

  /**
   *
   */
  private generateExporterId(prefix: string): string {
    return `exporter-${prefix}-${Date.now()}`;
  }

  /**
   *
   */
  private generateAlertRuleId(name: string): string {
    return `alert-${name.toLowerCase().replace(/\s+/g, '-')}-${Date.now()}-${this.ruleCounter++}`;
  }

  /**
   *
   */
  private generateAlertInstanceId(ruleId: string): string {
    return `instance-${ruleId}-${Date.now()}-${this.instanceCounter++}`;
  }

  /**
   *
   */
  private generateDashboardId(title: string): string {
    return `dashboard-${title.toLowerCase().replace(/\s+/g, '-')}-${Date.now()}-${this.dashboardCounter++}`;
  }

  /**
   *
   */
  private generateRunbookId(title: string): string {
    return `runbook-${title.toLowerCase().replace(/\s+/g, '-')}-${Date.now()}`;
  }
}

/**
 * Monitoring helper functions
 */

/**
 * Parse PromQL expression
 */
export function parsePromQL(expr: string): {
  metric: string;
  labels: Record<string, string>;
  operator?: string;
  threshold?: number;
} {
  // Simple parser for basic PromQL expressions
  const parts = expr.split(/\s+/);
  const metric = parts[0] || '';
  const operator = parts.length > 1 ? parts[1] : undefined;
  const threshold = parts.length > 2 ? parseFloat(parts[2]) : undefined;

  return { metric, labels: {}, operator, threshold };
}

/**
 * Format metric value
 */
export function formatMetricValue(value: number, unit?: string): string {
  if (unit === 'bytes') {
    if (value >= 1024 * 1024 * 1024) {
      return `${(value / (1024 * 1024 * 1024)).toFixed(2)} GB`;
    }
    if (value >= 1024 * 1024) {
      return `${(value / (1024 * 1024)).toFixed(2)} MB`;
    }
    if (value >= 1024) {
      return `${(value / 1024).toFixed(2)} KB`;
    }
    return `${value} B`;
  }

  if (unit === 'percent') {
    return `${value.toFixed(2)}%`;
  }

  if (unit === 'ms') {
    if (value >= 1000) {
      return `${(value / 1000).toFixed(2)}s`;
    }
    return `${value.toFixed(2)}ms`;
  }

  return value.toFixed(2);
}

/**
 * Calculate alert duration
 */
export function calculateAlertDuration(alert: AlertInstance): number {
  const endTime = alert.resolvedAt || new Date();
  return endTime.getTime() - alert.firedAt.getTime();
}

/**
 * Validate dashboard panel configuration
 */
export function validatePanelConfig(panel: DashboardPanel): {
  valid: boolean;
  errors: string[];
} {
  const errors: string[] = [];

  if (!panel.title || panel.title.trim() === '') {
    errors.push('Panel title is required');
  }

  if (!panel.query || panel.query.trim() === '') {
    errors.push('Panel query is required');
  }

  if (panel.gridPos.w < 1 || panel.gridPos.h < 1) {
    errors.push('Panel dimensions must be at least 1x1');
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}
