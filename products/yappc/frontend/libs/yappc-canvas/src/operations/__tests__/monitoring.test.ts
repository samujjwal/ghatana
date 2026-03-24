/**
 * Tests for Canvas Monitoring System
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  MonitoringManager,
  type MetricDefinition,
  type AlertSeverity,
  type PanelType,
  parsePromQL,
  formatMetricValue,
  calculateAlertDuration,
  validatePanelConfig,
} from '../monitoring';

describe('MonitoringManager', () => {
  let manager: MonitoringManager;

  beforeEach(() => {
    manager = new MonitoringManager();
  });

  describe('Initialization', () => {
    it('should create manager with default configuration', () => {
      expect(manager).toBeDefined();
      expect(manager.getMonitoringStats()).toEqual({
        exporters: 0,
        metrics: 0,
        samples: 0,
        alertRules: 0,
        firingAlerts: 0,
        dashboards: 0,
        runbooks: 0,
      });
    });

    it('should accept custom configuration', () => {
      const customManager = new MonitoringManager({
        defaultScrapeInterval: 30000,
        defaultAlertEvalInterval: 60000,
        alertRetentionDays: 30,
        enableSyntheticChecks: false,
      });

      expect(customManager).toBeDefined();
    });
  });

  describe('Prometheus Exporters', () => {
    it('should create exporter', () => {
      const exporter = manager.createExporter({
        prefix: 'canvas',
        endpoint: '/metrics',
        scrapeInterval: 15000,
      });

      expect(exporter.id).toContain('exporter-canvas');
      expect(exporter.prefix).toBe('canvas');
      expect(exporter.endpoint).toBe('/metrics');
      expect(exporter.scrapeInterval).toBe(15000);
      expect(exporter.metrics).toEqual([]);
      expect(exporter.samples).toEqual([]);
    });

    it('should get exporter by ID', () => {
      const exporter = manager.createExporter({
        prefix: 'test',
        endpoint: '/metrics',
        scrapeInterval: 10000,
      });

      const retrieved = manager.getExporter(exporter.id);
      expect(retrieved).toEqual(exporter);
    });

    it('should get all exporters', () => {
      manager.createExporter({ prefix: 'canvas', endpoint: '/metrics', scrapeInterval: 15000 });
      manager.createExporter({ prefix: 'api', endpoint: '/api/metrics', scrapeInterval: 10000 });

      const exporters = manager.getAllExporters();
      expect(exporters).toHaveLength(2);
    });

    it('should register metric', () => {
      const exporter = manager.createExporter({
        prefix: 'canvas',
        endpoint: '/metrics',
        scrapeInterval: 15000,
      });

      const metric: MetricDefinition = {
        name: 'render_fps',
        type: 'gauge',
        help: 'Canvas render FPS',
        labels: ['canvas_id'],
      };

      manager.registerMetric(exporter.id, metric);

      const retrieved = manager.getExporter(exporter.id);
      expect(retrieved?.metrics).toHaveLength(1);
      expect(retrieved?.metrics[0]).toEqual(metric);
    });

    it('should prevent duplicate metric registration', () => {
      const exporter = manager.createExporter({
        prefix: 'canvas',
        endpoint: '/metrics',
        scrapeInterval: 15000,
      });

      const metric: MetricDefinition = {
        name: 'fps',
        type: 'gauge',
        help: 'FPS',
        labels: [],
      };

      manager.registerMetric(exporter.id, metric);

      expect(() => manager.registerMetric(exporter.id, metric)).toThrow(
        'already registered'
      );
    });

    it('should record metric sample', () => {
      const exporter = manager.createExporter({
        prefix: 'canvas',
        endpoint: '/metrics',
        scrapeInterval: 15000,
      });

      manager.registerMetric(exporter.id, {
        name: 'render_fps',
        type: 'gauge',
        help: 'FPS',
        labels: ['canvas_id'],
      });

      manager.recordMetric(exporter.id, 'render_fps', 60, { canvas_id: 'main' });

      const retrieved = manager.getExporter(exporter.id);
      expect(retrieved?.samples).toHaveLength(1);
      expect(retrieved?.samples[0].name).toBe('render_fps');
      expect(retrieved?.samples[0].value).toBe(60);
      expect(retrieved?.samples[0].labels).toEqual({ canvas_id: 'main' });
    });

    it('should validate required labels', () => {
      const exporter = manager.createExporter({
        prefix: 'canvas',
        endpoint: '/metrics',
        scrapeInterval: 15000,
      });

      manager.registerMetric(exporter.id, {
        name: 'test_metric',
        type: 'counter',
        help: 'Test',
        labels: ['required_label'],
      });

      expect(() =>
        manager.recordMetric(exporter.id, 'test_metric', 1, {})
      ).toThrow('Missing required labels');
    });

    it('should export metrics in Prometheus format', () => {
      const exporter = manager.createExporter({
        prefix: 'canvas',
        endpoint: '/metrics',
        scrapeInterval: 15000,
      });

      manager.registerMetric(exporter.id, {
        name: 'render_fps',
        type: 'gauge',
        help: 'Canvas render FPS',
        labels: ['canvas_id'],
      });

      manager.recordMetric(exporter.id, 'render_fps', 60, { canvas_id: 'main' });

      const output = manager.exportPrometheusMetrics(exporter.id);

      expect(output).toContain('# HELP canvas_render_fps Canvas render FPS');
      expect(output).toContain('# TYPE canvas_render_fps gauge');
      expect(output).toContain('canvas_render_fps{canvas_id="main"} 60');
    });

    it('should limit sample history', () => {
      const exporter = manager.createExporter({
        prefix: 'test',
        endpoint: '/metrics',
        scrapeInterval: 15000,
      });

      manager.registerMetric(exporter.id, {
        name: 'counter',
        type: 'counter',
        help: 'Counter',
        labels: [],
      });

      // Record 11000 samples (exceeds 10000 limit)
      for (let i = 0; i < 11000; i++) {
        manager.recordMetric(exporter.id, 'counter', i, {});
      }

      const retrieved = manager.getExporter(exporter.id);
      expect(retrieved?.samples.length).toBe(10000);
    });
  });

  describe('Alert Rules', () => {
    it('should create alert rule', () => {
      const rule = manager.createAlertRule({
        name: 'LowFPS',
        expr: 'canvas_render_fps < 30',
        for: '5m',
        severity: 'warning',
      });

      expect(rule.id).toContain('alert-lowfps');
      expect(rule.name).toBe('LowFPS');
      expect(rule.expr).toBe('canvas_render_fps < 30');
      expect(rule.for).toBe('5m');
      expect(rule.severity).toBe('warning');
    });

    it('should get alert rule by ID', () => {
      const rule = manager.createAlertRule({
        name: 'Test',
        expr: 'test > 0',
        for: '1m',
        severity: 'info',
      });

      const retrieved = manager.getAlertRule(rule.id);
      expect(retrieved).toEqual(rule);
    });

    it('should get all alert rules', () => {
      manager.createAlertRule({
        name: 'Rule1',
        expr: 'metric1 > 0',
        for: '1m',
        severity: 'warning',
      });
      manager.createAlertRule({
        name: 'Rule2',
        expr: 'metric2 > 0',
        for: '1m',
        severity: 'critical',
      });

      const rules = manager.getAllAlertRules();
      expect(rules).toHaveLength(2);
    });

    it('should get rules by severity', () => {
      manager.createAlertRule({
        name: 'Warning',
        expr: 'test > 0',
        for: '1m',
        severity: 'warning',
      });
      manager.createAlertRule({
        name: 'Critical',
        expr: 'test > 0',
        for: '1m',
        severity: 'critical',
      });

      const warnings = manager.getAlertRulesBySeverity('warning');
      expect(warnings).toHaveLength(1);
      expect(warnings[0].severity).toBe('warning');
    });

    it('should include runbook URL', () => {
      const rule = manager.createAlertRule({
        name: 'LowFPS',
        expr: 'fps < 30',
        for: '5m',
        severity: 'warning',
        runbookUrl: 'https://docs.example.com/runbooks/low-fps',
      });

      expect(rule.runbookUrl).toBe('https://docs.example.com/runbooks/low-fps');
    });
  });

  describe('Alert Instances', () => {
    it('should fire alert', () => {
      const rule = manager.createAlertRule({
        name: 'HighLatency',
        expr: 'latency > 1000',
        for: '5m',
        severity: 'warning',
      });

      const instance = manager.fireAlert(rule.id, 1500);

      expect(instance.ruleId).toBe(rule.id);
      expect(instance.status).toBe('firing');
      expect(instance.value).toBe(1500);
      expect(instance.firedAt).toBeInstanceOf(Date);
    });

    it('should resolve alert', () => {
      const rule = manager.createAlertRule({
        name: 'Test',
        expr: 'test > 0',
        for: '1m',
        severity: 'info',
      });

      const instance = manager.fireAlert(rule.id, 1);
      manager.resolveAlert(instance.id);

      const retrieved = manager.getAlertInstance(instance.id);
      expect(retrieved?.status).toBe('resolved');
      expect(retrieved?.resolvedAt).toBeInstanceOf(Date);
    });

    it('should acknowledge alert', () => {
      const rule = manager.createAlertRule({
        name: 'Test',
        expr: 'test > 0',
        for: '1m',
        severity: 'warning',
      });

      const instance = manager.fireAlert(rule.id, 1);
      manager.acknowledgeAlert(instance.id, 'john@example.com');

      const retrieved = manager.getAlertInstance(instance.id);
      expect(retrieved?.status).toBe('acknowledged');
      expect(retrieved?.acknowledgedBy).toBe('john@example.com');
      expect(retrieved?.acknowledgedAt).toBeInstanceOf(Date);
    });

    it('should silence alert', () => {
      const rule = manager.createAlertRule({
        name: 'Test',
        expr: 'test > 0',
        for: '1m',
        severity: 'info',
      });

      const instance = manager.fireAlert(rule.id, 1);
      manager.silenceAlert(instance.id, 3600000); // 1 hour

      const retrieved = manager.getAlertInstance(instance.id);
      expect(retrieved?.status).toBe('silenced');
      expect(retrieved?.silenceDuration).toBe(3600000);
    });

    it('should get firing alerts', () => {
      const rule = manager.createAlertRule({
        name: 'Test',
        expr: 'test > 0',
        for: '1m',
        severity: 'warning',
      });

      const instance1 = manager.fireAlert(rule.id, 1);
      const instance2 = manager.fireAlert(rule.id, 2);
      
      // Resolve second alert before checking
      manager.resolveAlert(instance2.id);

      const firing = manager.getFiringAlerts();
      expect(firing).toHaveLength(1);
      expect(firing[0].id).toBe(instance1.id);
      expect(firing[0].status).toBe('firing');
    });

    it('should get alerts by rule', () => {
      const rule1 = manager.createAlertRule({
        name: 'Rule1',
        expr: 'test > 0',
        for: '1m',
        severity: 'warning',
      });
      const rule2 = manager.createAlertRule({
        name: 'Rule2',
        expr: 'test > 0',
        for: '1m',
        severity: 'warning',
      });

      const alert1 = manager.fireAlert(rule1.id, 1);
      const alert2 = manager.fireAlert(rule1.id, 2);
      manager.fireAlert(rule2.id, 3);

      const rule1Alerts = manager.getAlertsByRule(rule1.id);
      expect(rule1Alerts).toHaveLength(2);
      expect(rule1Alerts[0].id).toBe(alert1.id);
      expect(rule1Alerts[1].id).toBe(alert2.id);
    });
  });

  describe('Dashboards', () => {
    it('should create dashboard', () => {
      const dashboard = manager.createDashboard({
        title: 'Canvas Performance',
        description: 'Performance metrics',
        tags: ['performance', 'canvas'],
        panels: [],
      });

      expect(dashboard.id).toContain('dashboard-canvas-performance');
      expect(dashboard.title).toBe('Canvas Performance');
      expect(dashboard.tags).toEqual(['performance', 'canvas']);
      expect(dashboard.refreshInterval).toBe(30000);
      expect(dashboard.timeRange).toBe('1h');
    });

    it('should create dashboard with panels', () => {
      const dashboard = manager.createDashboard({
        title: 'Metrics',
        description: 'Metrics dashboard',
        tags: [],
        panels: [
          {
            id: 'panel-1',
            title: 'FPS',
            type: 'graph',
            gridPos: { x: 0, y: 0, w: 12, h: 8 },
            query: 'canvas_render_fps',
            options: {},
          },
        ],
      });

      expect(dashboard.panels).toHaveLength(1);
      expect(dashboard.panels[0].title).toBe('FPS');
    });

    it('should update dashboard', () => {
      const dashboard = manager.createDashboard({
        title: 'Original',
        description: 'Original description',
        tags: [],
        panels: [],
      });

      const updated = manager.updateDashboard(dashboard.id, {
        title: 'Updated',
        description: 'Updated description',
      });

      expect(updated.title).toBe('Updated');
      expect(updated.description).toBe('Updated description');
      expect(updated.updatedAt.getTime()).toBeGreaterThanOrEqual(
        dashboard.createdAt.getTime()
      );
    });

    it('should get dashboard by ID', () => {
      const dashboard = manager.createDashboard({
        title: 'Test',
        description: 'Test',
        tags: [],
        panels: [],
      });

      const retrieved = manager.getDashboard(dashboard.id);
      expect(retrieved).toEqual(dashboard);
    });

    it('should get dashboards by tag', () => {
      manager.createDashboard({
        title: 'Dashboard 1',
        description: 'Test',
        tags: ['performance'],
        panels: [],
      });
      manager.createDashboard({
        title: 'Dashboard 2',
        description: 'Test',
        tags: ['performance', 'monitoring'],
        panels: [],
      });
      manager.createDashboard({
        title: 'Dashboard 3',
        description: 'Test',
        tags: ['monitoring'],
        panels: [],
      });

      const perfDashboards = manager.getDashboardsByTag('performance');
      expect(perfDashboards).toHaveLength(2);
    });

    it('should export dashboard as JSON', () => {
      const dashboard = manager.createDashboard({
        title: 'Test',
        description: 'Test dashboard',
        tags: ['test'],
        panels: [],
      });

      const json = manager.exportDashboard(dashboard.id);
      const parsed = JSON.parse(json);

      expect(parsed.title).toBe('Test');
      expect(parsed.tags).toEqual(['test']);
    });
  });

  describe('Runbooks', () => {
    it('should create runbook', () => {
      const runbook = manager.createRunbook({
        title: 'Low FPS Resolution',
        alertRuleIds: ['alert-1'],
        problem: 'Canvas rendering is slow',
        impact: 'User experience degradation',
        diagnosis: ['Check FPS metrics', 'Review browser console'],
        resolution: ['Reduce node count', 'Optimize rendering'],
        prevention: ['Set performance budgets'],
        relatedDocs: ['https://docs.example.com/performance'],
      });

      expect(runbook.id).toContain('runbook-low-fps-resolution');
      expect(runbook.title).toBe('Low FPS Resolution');
      expect(runbook.diagnosis).toHaveLength(2);
      expect(runbook.resolution).toHaveLength(2);
    });

    it('should update runbook', () => {
      const runbook = manager.createRunbook({
        title: 'Original',
        alertRuleIds: [],
        problem: 'Original problem',
        impact: 'Original impact',
        diagnosis: [],
        resolution: [],
        prevention: [],
        relatedDocs: [],
      });

      const updated = manager.updateRunbook(runbook.id, {
        problem: 'Updated problem',
        diagnosis: ['Step 1', 'Step 2'],
      });

      expect(updated.problem).toBe('Updated problem');
      expect(updated.diagnosis).toHaveLength(2);
    });

    it('should get runbook by ID', () => {
      const runbook = manager.createRunbook({
        title: 'Test',
        alertRuleIds: [],
        problem: 'Test',
        impact: 'Test',
        diagnosis: [],
        resolution: [],
        prevention: [],
        relatedDocs: [],
      });

      const retrieved = manager.getRunbook(runbook.id);
      expect(retrieved).toEqual(runbook);
    });

    it('should get runbook by alert rule', () => {
      const rule = manager.createAlertRule({
        name: 'Test',
        expr: 'test > 0',
        for: '1m',
        severity: 'warning',
      });

      manager.createRunbook({
        title: 'Test Runbook',
        alertRuleIds: [rule.id],
        problem: 'Test',
        impact: 'Test',
        diagnosis: [],
        resolution: [],
        prevention: [],
        relatedDocs: [],
      });

      const runbook = manager.getRunbookByAlertRule(rule.id);
      expect(runbook).toBeDefined();
      expect(runbook?.title).toBe('Test Runbook');
    });

    it('should export runbook as markdown', () => {
      const runbook = manager.createRunbook({
        title: 'Test Runbook',
        alertRuleIds: [],
        problem: 'System is down',
        impact: 'No service available',
        diagnosis: ['Check logs', 'Verify connectivity'],
        resolution: ['Restart service', 'Scale up'],
        prevention: ['Add monitoring'],
        relatedDocs: ['https://docs.example.com'],
      });

      const markdown = manager.exportRunbookMarkdown(runbook.id);

      expect(markdown).toContain('# Test Runbook');
      expect(markdown).toContain('## Problem');
      expect(markdown).toContain('System is down');
      expect(markdown).toContain('## Diagnosis');
      expect(markdown).toContain('1. Check logs');
      expect(markdown).toContain('## Resolution');
      expect(markdown).toContain('1. Restart service');
    });
  });

  describe('Cleanup Operations', () => {
    it('should clean up old alerts', async () => {
      const customManager = new MonitoringManager({
        alertRetentionDays: 0,
      });

      const rule = customManager.createAlertRule({
        name: 'Test',
        expr: 'test > 0',
        for: '1m',
        severity: 'info',
      });

      const instance = customManager.fireAlert(rule.id, 1);
      customManager.resolveAlert(instance.id);

      // Wait a bit to ensure alert is old
      await new Promise((resolve) => setTimeout(resolve, 10));

      const removed = customManager.cleanupOldAlerts();
      expect(removed).toBe(1);
    });

    it('should not clean up firing alerts', () => {
      const customManager = new MonitoringManager({
        alertRetentionDays: 0,
      });

      const rule = customManager.createAlertRule({
        name: 'Test',
        expr: 'test > 0',
        for: '1m',
        severity: 'warning',
      });

      customManager.fireAlert(rule.id, 1);

      const removed = customManager.cleanupOldAlerts();
      expect(removed).toBe(0);
    });
  });

  describe('Monitoring Statistics', () => {
    it('should get comprehensive stats', () => {
      const exporter = manager.createExporter({
        prefix: 'test',
        endpoint: '/metrics',
        scrapeInterval: 15000,
      });

      manager.registerMetric(exporter.id, {
        name: 'metric1',
        type: 'counter',
        help: 'Test',
        labels: [],
      });

      manager.recordMetric(exporter.id, 'metric1', 1, {});

      const rule = manager.createAlertRule({
        name: 'Test',
        expr: 'test > 0',
        for: '1m',
        severity: 'warning',
      });

      manager.fireAlert(rule.id, 1);

      manager.createDashboard({
        title: 'Test',
        description: 'Test',
        tags: [],
        panels: [],
      });

      manager.createRunbook({
        title: 'Test',
        alertRuleIds: [],
        problem: 'Test',
        impact: 'Test',
        diagnosis: [],
        resolution: [],
        prevention: [],
        relatedDocs: [],
      });

      const stats = manager.getMonitoringStats();

      expect(stats.exporters).toBe(1);
      expect(stats.metrics).toBe(1);
      expect(stats.samples).toBe(1);
      expect(stats.alertRules).toBe(1);
      expect(stats.firingAlerts).toBe(1);
      expect(stats.dashboards).toBe(1);
      expect(stats.runbooks).toBe(1);
    });
  });

  describe('Reset Operations', () => {
    it('should reset manager state', () => {
      manager.createExporter({ prefix: 'test', endpoint: '/metrics', scrapeInterval: 15000 });
      manager.createAlertRule({
        name: 'Test',
        expr: 'test > 0',
        for: '1m',
        severity: 'info',
      });
      manager.createDashboard({
        title: 'Test',
        description: 'Test',
        tags: [],
        panels: [],
      });

      manager.reset();

      const stats = manager.getMonitoringStats();
      expect(stats.exporters).toBe(0);
      expect(stats.alertRules).toBe(0);
      expect(stats.dashboards).toBe(0);
    });
  });
});

describe('Monitoring Helper Functions', () => {
  describe('parsePromQL', () => {
    it('should parse simple expression', () => {
      const result = parsePromQL('metric_name > 100');

      expect(result.metric).toBe('metric_name');
      expect(result.operator).toBe('>');
      expect(result.threshold).toBe(100);
    });

    it('should parse metric without operator', () => {
      const result = parsePromQL('metric_name');

      expect(result.metric).toBe('metric_name');
      expect(result.operator).toBeUndefined();
      expect(result.threshold).toBeUndefined();
    });
  });

  describe('formatMetricValue', () => {
    it('should format bytes', () => {
      expect(formatMetricValue(1024, 'bytes')).toBe('1.00 KB');
      expect(formatMetricValue(1048576, 'bytes')).toBe('1.00 MB');
      expect(formatMetricValue(1073741824, 'bytes')).toBe('1.00 GB');
    });

    it('should format percent', () => {
      expect(formatMetricValue(75.5, 'percent')).toBe('75.50%');
    });

    it('should format milliseconds', () => {
      expect(formatMetricValue(500, 'ms')).toBe('500.00ms');
      expect(formatMetricValue(1500, 'ms')).toBe('1.50s');
    });

    it('should format plain numbers', () => {
      expect(formatMetricValue(42.5)).toBe('42.50');
    });
  });

  describe('calculateAlertDuration', () => {
    it('should calculate duration for resolved alert', () => {
      const firedAt = new Date('2024-01-01T10:00:00Z');
      const resolvedAt = new Date('2024-01-01T10:05:00Z');

      const duration = calculateAlertDuration({
        id: 'test',
        ruleId: 'rule-1',
        status: 'resolved',
        labels: {},
        value: 1,
        firedAt,
        resolvedAt,
      });

      expect(duration).toBe(5 * 60 * 1000); // 5 minutes
    });

    it('should calculate duration for firing alert', () => {
      const firedAt = new Date(Date.now() - 60000); // 1 minute ago

      const duration = calculateAlertDuration({
        id: 'test',
        ruleId: 'rule-1',
        status: 'firing',
        labels: {},
        value: 1,
        firedAt,
      });

      expect(duration).toBeGreaterThanOrEqual(60000);
    });
  });

  describe('validatePanelConfig', () => {
    it('should validate correct panel', () => {
      const result = validatePanelConfig({
        id: 'panel-1',
        title: 'Test Panel',
        type: 'graph',
        gridPos: { x: 0, y: 0, w: 12, h: 8 },
        query: 'metric_name',
        options: {},
      });

      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should detect missing title', () => {
      const result = validatePanelConfig({
        id: 'panel-1',
        title: '',
        type: 'graph',
        gridPos: { x: 0, y: 0, w: 12, h: 8 },
        query: 'metric_name',
        options: {},
      });

      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('title'))).toBe(true);
    });

    it('should detect missing query', () => {
      const result = validatePanelConfig({
        id: 'panel-1',
        title: 'Test',
        type: 'graph',
        gridPos: { x: 0, y: 0, w: 12, h: 8 },
        query: '',
        options: {},
      });

      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('query'))).toBe(true);
    });

    it('should detect invalid dimensions', () => {
      const result = validatePanelConfig({
        id: 'panel-1',
        title: 'Test',
        type: 'graph',
        gridPos: { x: 0, y: 0, w: 0, h: 0 },
        query: 'metric',
        options: {},
      });

      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('dimensions'))).toBe(true);
    });
  });
});
