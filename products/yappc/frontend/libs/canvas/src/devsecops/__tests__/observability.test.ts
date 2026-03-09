/**
 * Tests for Observability & Metrics Overlay System
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  createObservabilityConfig,
  createObservabilityOverlay,
  parsePrometheusMetrics,
  addMetrics,
  queryMetrics,
  createTimeSeries,
  addTimeSeries,
  createGrafanaDashboard,
  addDashboard,
  createSLO,
  addSLO,
  updateSLOCompliance,
  createAlert,
  addAlert,
  resolveAlert,
  silenceAlert,
  getActiveAlerts,
  getAlertsBySeverity,
  createHealthCheck,
  addHealthCheck,
  updateHealthCheck,
  getUnhealthyServices,
  overlayMetricsOnCanvas,
  overlayAlertsOnCanvas,
  getObservabilitySummary,
  cleanupOldMetrics,
  type ObservabilityOverlay,
  type PrometheusMetric,
  type ServiceLevelIndicator,
  type GrafanaPanel,
} from '../observability';

import type { CanvasDocument } from '../../types/canvas-document';

describe.skip('observability', () => {
  describe('Configuration', () => {
    it('should create observability config with defaults', () => {
      const config = createObservabilityConfig();

      expect(config.enableMetrics).toBe(true);
      expect(config.enableAlerts).toBe(true);
      expect(config.enableHealthChecks).toBe(true);
      expect(config.refreshInterval).toBe(15000);
      expect(config.retentionPeriod).toBe(3600000);
      expect(config.alertThresholds).toEqual({
        warning: 0.8,
        critical: 0.95,
      });
    });

    it('should create observability config with overrides', () => {
      const config = createObservabilityConfig({
        refreshInterval: 5000,
        enableAlerts: false,
        alertThresholds: {
          warning: 0.7,
          critical: 0.9,
        },
      });

      expect(config.refreshInterval).toBe(5000);
      expect(config.enableAlerts).toBe(false);
      expect(config.alertThresholds.warning).toBe(0.7);
    });
  });

  describe('Prometheus Metrics', () => {
    it('should parse Prometheus metrics text format', () => {
      const metricsText = `
# HELP http_requests_total Total HTTP requests
# TYPE http_requests_total counter
http_requests_total{method="GET",status="200"} 1234
http_requests_total{method="POST",status="201"} 56

# HELP memory_usage_bytes Memory usage in bytes
# TYPE memory_usage_bytes gauge
memory_usage_bytes{service="api"} 524288000
      `.trim();

      const metrics = parsePrometheusMetrics(metricsText);

      expect(metrics).toHaveLength(3);

      expect(metrics[0].name).toBe('http_requests_total');
      expect(metrics[0].type).toBe('counter');
      expect(metrics[0].value).toBe(1234);
      expect(metrics[0].labels).toEqual({
        method: 'GET',
        status: '200',
      });

      expect(metrics[2].name).toBe('memory_usage_bytes');
      expect(metrics[2].type).toBe('gauge');
      expect(metrics[2].value).toBe(524288000);
    });

    it('should add metrics to overlay', () => {
      const overlay = createObservabilityOverlay('doc-123');

      const metrics: PrometheusMetric[] = [
        {
          name: 'cpu_usage',
          type: 'gauge',
          help: 'CPU usage',
          labels: { service: 'api' },
          value: 45.5,
          timestamp: new Date(),
          metadata: { unit: 'percent' },
        },
      ];

      const updated = addMetrics(overlay, metrics);

      expect(updated.metrics.size).toBe(1);
      expect(updated.metrics.get('cpu_usage')).toHaveLength(1);
    });

    it('should query metrics by name', () => {
      let overlay = createObservabilityOverlay('doc-123');

      const metrics: PrometheusMetric[] = [
        {
          name: 'requests',
          type: 'counter',
          help: 'Requests',
          labels: { service: 'api', method: 'GET' },
          value: 100,
          timestamp: new Date(),
          metadata: {},
        },
        {
          name: 'requests',
          type: 'counter',
          help: 'Requests',
          labels: { service: 'api', method: 'POST' },
          value: 50,
          timestamp: new Date(),
          metadata: {},
        },
        {
          name: 'errors',
          type: 'counter',
          help: 'Errors',
          labels: { service: 'api' },
          value: 5,
          timestamp: new Date(),
          metadata: {},
        },
      ];

      overlay = addMetrics(overlay, metrics);

      const results = queryMetrics(overlay, 'requests');
      expect(results).toHaveLength(2);
    });

    it('should query metrics by name and labels', () => {
      let overlay = createObservabilityOverlay('doc-123');

      const metrics: PrometheusMetric[] = [
        {
          name: 'requests',
          type: 'counter',
          help: 'Requests',
          labels: { service: 'api', method: 'GET' },
          value: 100,
          timestamp: new Date(),
          metadata: {},
        },
        {
          name: 'requests',
          type: 'counter',
          help: 'Requests',
          labels: { service: 'api', method: 'POST' },
          value: 50,
          timestamp: new Date(),
          metadata: {},
        },
      ];

      overlay = addMetrics(overlay, metrics);

      const results = queryMetrics(overlay, 'requests', { method: 'GET' });
      expect(results).toHaveLength(1);
      expect(results[0].value).toBe(100);
    });

    it('should create time series from metrics', () => {
      const now = Date.now();
      const metrics: PrometheusMetric[] = [
        {
          name: 'cpu',
          type: 'gauge',
          help: 'CPU',
          labels: {},
          value: 50,
          timestamp: new Date(now - 2000),
          metadata: {},
        },
        {
          name: 'cpu',
          type: 'gauge',
          help: 'CPU',
          labels: {},
          value: 60,
          timestamp: new Date(now - 1000),
          metadata: {},
        },
        {
          name: 'cpu',
          type: 'gauge',
          help: 'CPU',
          labels: {},
          value: 70,
          timestamp: new Date(now),
          metadata: {},
        },
      ];

      const timeSeries = createTimeSeries(metrics, 'cpu');

      expect(timeSeries.metric).toBe('cpu');
      expect(timeSeries.dataPoints).toHaveLength(3);
      expect(timeSeries.dataPoints[0].value).toBe(50);
      expect(timeSeries.dataPoints[2].value).toBe(70);
    });

    it('should aggregate time series data', () => {
      const now = Date.now();
      const metrics: PrometheusMetric[] = [
        {
          name: 'requests',
          type: 'counter',
          help: 'Requests',
          labels: {},
          value: 10,
          timestamp: new Date(now - 1500),
          metadata: {},
        },
        {
          name: 'requests',
          type: 'counter',
          help: 'Requests',
          labels: {},
          value: 20,
          timestamp: new Date(now - 500),
          metadata: {},
        },
      ];

      const timeSeries = createTimeSeries(metrics, 'requests', {
        interval: 1, // 1 second
        aggregation: 'sum',
      });

      expect(timeSeries.metadata.aggregation).toBe('sum');
    });

    it('should add time series to overlay', () => {
      const overlay = createObservabilityOverlay('doc-123');

      const timeSeries = createTimeSeries([], 'cpu', { interval: 60 });
      const updated = addTimeSeries(overlay, timeSeries);

      expect(updated.timeSeries.size).toBe(1);
      expect(updated.timeSeries.get('cpu')).toEqual(timeSeries);
    });
  });

  describe('Grafana Dashboards', () => {
    it('should create Grafana dashboard', () => {
      const panels: GrafanaPanel[] = [
        {
          id: 'panel-1',
          title: 'CPU Usage',
          type: 'graph',
          targets: ['cpu_usage{service="api"}'],
          position: { x: 0, y: 0, width: 12, height: 8 },
        },
      ];

      const dashboard = createGrafanaDashboard('dash-1', 'System Metrics', panels);

      expect(dashboard.id).toBe('dash-1');
      expect(dashboard.title).toBe('System Metrics');
      expect(dashboard.panels).toHaveLength(1);
      expect(dashboard.url).toContain('/d/dash-1');
    });

    it('should add dashboard to overlay', () => {
      const overlay = createObservabilityOverlay('doc-123');

      const panels: GrafanaPanel[] = [
        {
          id: 'panel-1',
          title: 'Metrics',
          type: 'graph',
          targets: ['metric'],
          position: { x: 0, y: 0, width: 12, height: 8 },
        },
      ];

      const dashboard = createGrafanaDashboard('dash-1', 'Dashboard', panels);
      const updated = addDashboard(overlay, dashboard);

      expect(updated.dashboards).toHaveLength(1);
      expect(updated.dashboards[0].id).toBe('dash-1');
    });
  });

  describe('Service Level Objectives', () => {
    it('should create SLO with compliant SLI', () => {
      const sli: ServiceLevelIndicator = {
        id: 'sli-1',
        name: 'Availability',
        metric: 'availability_percent',
        query: 'up{service="api"}',
        threshold: 99,
        comparison: '>=',
        current: 99.5,
        metadata: { unit: 'percent', category: 'availability' },
      };

      const slo = createSLO('High Availability', 'Keep service available', 99, 2592000000, sli);

      expect(slo.name).toBe('High Availability');
      expect(slo.target).toBe(99);
      expect(slo.compliance).toBe(100); // 99.5 >= 99, so compliant
      expect(slo.metadata.priority).toBe('medium'); // Compliant
    });

    it('should create SLO with non-compliant SLI', () => {
      const sli: ServiceLevelIndicator = {
        id: 'sli-1',
        name: 'Latency',
        metric: 'response_time_p99',
        query: 'http_latency_p99{service="api"}',
        threshold: 200,
        comparison: '<=',
        current: 250,
        metadata: { unit: 'ms', category: 'latency' },
      };

      const slo = createSLO('Low Latency', 'Keep latency low', 95, 2592000000, sli);

      expect(slo.compliance).toBe(0); // 250 > 200, not compliant
      expect(slo.metadata.priority).toBe('critical'); // Non-compliant
    });

    it('should add SLO to overlay', () => {
      const overlay = createObservabilityOverlay('doc-123');

      const sli: ServiceLevelIndicator = {
        id: 'sli-1',
        name: 'Availability',
        metric: 'up',
        query: 'up',
        threshold: 99,
        comparison: '>=',
        current: 99.5,
        metadata: {},
      };

      const slo = createSLO('SLO', 'Desc', 99, 2592000000, sli);
      const updated = addSLO(overlay, slo);

      expect(updated.slos).toHaveLength(1);
    });

    it('should update SLO compliance', () => {
      let overlay = createObservabilityOverlay('doc-123');

      const sli: ServiceLevelIndicator = {
        id: 'sli-1',
        name: 'Availability',
        metric: 'up',
        query: 'up',
        threshold: 99,
        comparison: '>=',
        current: 99.5,
        metadata: {},
      };

      const slo = createSLO('SLO', 'Desc', 99, 2592000000, sli);
      overlay = addSLO(overlay, slo);

      // Update with non-compliant value
      const updated = updateSLOCompliance(overlay, slo.id, 98);

      expect(updated.slos[0].sli.current).toBe(98);
      expect(updated.slos[0].compliance).toBe(0); // Not compliant
      expect(updated.slos[0].metadata.priority).toBe('critical');
    });

    it('should calculate error budget', () => {
      const sli: ServiceLevelIndicator = {
        id: 'sli-1',
        name: 'Availability',
        metric: 'up',
        query: 'up',
        threshold: 99,
        comparison: '>=',
        current: 99.5,
        metadata: {},
      };

      const slo = createSLO('SLO', 'Desc', 99.9, 2592000000, sli);

      // Target: 99.9%, Current: 100% (compliant)
      // Error budget = 100 - 99.9 = 0.1%
      expect(slo.metadata.errorBudget).toBeGreaterThanOrEqual(0);
    });
  });

  describe('Alerts', () => {
    it('should create alert', () => {
      const alert = createAlert(
        'High CPU',
        'warning',
        'CPU usage is high',
        'prometheus',
        { service: 'api' },
        'node-1'
      );

      expect(alert.name).toBe('High CPU');
      expect(alert.severity).toBe('warning');
      expect(alert.status).toBe('firing');
      expect(alert.elementId).toBe('node-1');
    });

    it('should add alert to overlay', () => {
      const overlay = createObservabilityOverlay('doc-123');

      const alert = createAlert('Alert', 'critical', 'Message', 'source');
      const updated = addAlert(overlay, alert);

      expect(updated.alerts).toHaveLength(1);
    });

    it('should resolve alert', () => {
      let overlay = createObservabilityOverlay('doc-123');

      const alert = createAlert('Alert', 'critical', 'Message', 'source');
      overlay = addAlert(overlay, alert);

      const updated = resolveAlert(overlay, alert.id);

      expect(updated.alerts[0].status).toBe('resolved');
    });

    it('should silence alert', () => {
      let overlay = createObservabilityOverlay('doc-123');

      const alert = createAlert('Alert', 'warning', 'Message', 'source');
      overlay = addAlert(overlay, alert);

      const until = new Date(Date.now() + 3600000);
      const updated = silenceAlert(overlay, alert.id, until);

      expect(updated.alerts[0].status).toBe('silenced');
      expect(updated.alerts[0].metadata.silenceUntil).toEqual(until);
    });

    it('should get active alerts only', async () => {
      let overlay = createObservabilityOverlay('doc-123');

      const alert1 = createAlert('Alert 1', 'critical', 'Message', 'source');
      
      // Small delay to ensure unique ID
      await new Promise(resolve => setTimeout(resolve, 2));
      
      const alert2 = createAlert('Alert 2', 'warning', 'Message', 'source');

      overlay = addAlert(overlay, alert1);
      overlay = addAlert(overlay, alert2);
      
      // Verify both alerts are added with different IDs
      expect(overlay.alerts).toHaveLength(2);
      expect(alert1.id).not.toBe(alert2.id);
      
      // Reassign the resolved overlay
      const resolvedOverlay = resolveAlert(overlay, alert1.id);
      
      // Verify alert1 is resolved
      const resolvedAlert1 = resolvedOverlay.alerts.find(a => a.id === alert1.id);
      expect(resolvedAlert1?.status).toBe('resolved');
      
      // Verify alert2 is still firing
      const firingAlert2 = resolvedOverlay.alerts.find(a => a.id === alert2.id);
      expect(firingAlert2?.status).toBe('firing');
      
      const activeAlerts = getActiveAlerts(resolvedOverlay);

      expect(activeAlerts).toHaveLength(1);
      expect(activeAlerts[0].id).toBe(alert2.id);
    });

    it('should get alerts by severity', () => {
      let overlay = createObservabilityOverlay('doc-123');

      const alert1 = createAlert('Alert 1', 'critical', 'Message', 'source');
      const alert2 = createAlert('Alert 2', 'warning', 'Message', 'source');
      const alert3 = createAlert('Alert 3', 'critical', 'Message', 'source');

      overlay = addAlert(overlay, alert1);
      overlay = addAlert(overlay, alert2);
      overlay = addAlert(overlay, alert3);

      const criticalAlerts = getAlertsBySeverity(overlay, 'critical');

      expect(criticalAlerts).toHaveLength(2);
    });
  });

  describe('Health Checks', () => {
    it('should create health check with all healthy checks', () => {
      const healthCheck = createHealthCheck('API Service', [
        { name: 'Database', status: 'healthy' },
        { name: 'Cache', status: 'healthy' },
        { name: 'Queue', status: 'healthy' },
      ]);

      expect(healthCheck.name).toBe('API Service');
      expect(healthCheck.status).toBe('healthy');
      expect(healthCheck.checks).toHaveLength(3);
    });

    it('should create health check with degraded status', () => {
      const healthCheck = createHealthCheck('API Service', [
        { name: 'Database', status: 'healthy' },
        { name: 'Cache', status: 'degraded', message: 'High latency' },
        { name: 'Queue', status: 'healthy' },
      ]);

      expect(healthCheck.status).toBe('degraded');
    });

    it('should create health check with unhealthy status', () => {
      const healthCheck = createHealthCheck('API Service', [
        { name: 'Database', status: 'healthy' },
        { name: 'Cache', status: 'unhealthy', message: 'Connection failed' },
      ]);

      expect(healthCheck.status).toBe('unhealthy');
    });

    it('should add health check to overlay', () => {
      const overlay = createObservabilityOverlay('doc-123');

      const healthCheck = createHealthCheck('Service', [
        { name: 'Check', status: 'healthy' },
      ]);

      const updated = addHealthCheck(overlay, healthCheck);

      expect(updated.healthChecks.size).toBe(1);
    });

    it('should update health check status', () => {
      let overlay = createObservabilityOverlay('doc-123');

      const healthCheck = createHealthCheck('Service', [
        { name: 'Check', status: 'healthy' },
      ]);

      overlay = addHealthCheck(overlay, healthCheck);

      const updated = updateHealthCheck(overlay, healthCheck.id, 'degraded', 500);

      const updatedCheck = updated.healthChecks.get(healthCheck.id);
      expect(updatedCheck?.status).toBe('degraded');
      expect(updatedCheck?.responseTime).toBe(500);
    });

    it('should get unhealthy services', async () => {
      let overlay = createObservabilityOverlay('doc-123');

      const health1 = createHealthCheck('Service 1', [
        { name: 'Check', status: 'healthy' },
      ]);
      
      // Small delay to ensure unique IDs
      await new Promise(resolve => setTimeout(resolve, 2));
      
      const health2 = createHealthCheck('Service 2', [
        { name: 'Check', status: 'unhealthy' },
      ]);
      
      await new Promise(resolve => setTimeout(resolve, 2));
      
      const health3 = createHealthCheck('Service 3', [
        { name: 'Check', status: 'degraded' },
      ]);

      overlay = addHealthCheck(overlay, health1);
      overlay = addHealthCheck(overlay, health2);
      overlay = addHealthCheck(overlay, health3);

      const unhealthy = getUnhealthyServices(overlay);

      expect(unhealthy).toHaveLength(2);
    });
  });

  describe('Canvas Overlay', () => {
    let overlay: ObservabilityOverlay;

    beforeEach(() => {
      overlay = createObservabilityOverlay('canvas-1');

      const metrics: PrometheusMetric[] = [
        {
          name: 'cpu_usage',
          type: 'gauge',
          help: 'CPU',
          labels: { service: 'api-service' },
          value: 75,
          timestamp: new Date(),
          metadata: {},
        },
        {
          name: 'cpu_usage',
          type: 'gauge',
          help: 'CPU',
          labels: { service: 'db-service' },
          value: 45,
          timestamp: new Date(),
          metadata: {},
        },
      ];

      overlay = addMetrics(overlay, metrics);
    });

    it('should have metrics for services', () => {
      const apiMetrics = queryMetrics(overlay, 'cpu_usage', { service: 'api-service' });
      expect(apiMetrics).toHaveLength(1);
      expect(apiMetrics[0].value).toBe(75);

      const dbMetrics = queryMetrics(overlay, 'cpu_usage', { service: 'db-service' });
      expect(dbMetrics).toHaveLength(1);
      expect(dbMetrics[0].value).toBe(45);
    });

    it('should track alerts for elements', () => {
      const alert = createAlert(
        'High CPU',
        'critical',
        'CPU usage critical',
        'prometheus',
        {},
        'node-1'
      );

      overlay = addAlert(overlay, alert);

      const alerts = overlay.alerts.filter(a => a.elementId === 'node-1');
      expect(alerts).toHaveLength(1);
      expect(alerts[0].severity).toBe('critical');
    });
  });

  describe('Observability Summary', () => {
    it('should calculate observability summary', () => {
      let overlay = createObservabilityOverlay('doc-123');

      // Add metrics
      const metrics: PrometheusMetric[] = [
        {
          name: 'metric1',
          type: 'gauge',
          help: 'Metric',
          labels: {},
          value: 10,
          timestamp: new Date(),
          metadata: {},
        },
        {
          name: 'metric2',
          type: 'counter',
          help: 'Metric',
          labels: {},
          value: 20,
          timestamp: new Date(),
          metadata: {},
        },
      ];

      overlay = addMetrics(overlay, metrics);

      // Add SLO
      const sli: ServiceLevelIndicator = {
        id: 'sli-1',
        name: 'Availability',
        metric: 'up',
        query: 'up',
        threshold: 99,
        comparison: '>=',
        current: 99.5,
        metadata: {},
      };

      const slo = createSLO('SLO', 'Desc', 99, 2592000000, sli);
      overlay = addSLO(overlay, slo);

      // Add alerts
      const alert1 = createAlert('Alert 1', 'critical', 'Message', 'source');
      const alert2 = createAlert('Alert 2', 'warning', 'Message', 'source');

      overlay = addAlert(overlay, alert1);
      overlay = addAlert(overlay, alert2);

      // Add health check
      const healthCheck = createHealthCheck('Service', [
        { name: 'Check', status: 'unhealthy' },
      ]);

      overlay = addHealthCheck(overlay, healthCheck);

      const summary = getObservabilitySummary(overlay);

      expect(summary.totalMetrics).toBe(2);
      expect(summary.totalSLOs).toBe(1);
      expect(summary.compliantSLOs).toBe(1);
      expect(summary.activeAlerts).toBe(2);
      expect(summary.criticalAlerts).toBe(1);
      expect(summary.unhealthyServices).toBe(1);
      expect(summary.totalHealthChecks).toBe(1);
    });
  });

  describe('Cleanup', () => {
    it('should cleanup old metrics', () => {
      let overlay = createObservabilityOverlay('doc-123');

      const now = Date.now();
      const metrics: PrometheusMetric[] = [
        {
          name: 'metric',
          type: 'gauge',
          help: 'Metric',
          labels: {},
          value: 10,
          timestamp: new Date(now - 7200000), // 2 hours ago
          metadata: {},
        },
        {
          name: 'metric',
          type: 'gauge',
          help: 'Metric',
          labels: {},
          value: 20,
          timestamp: new Date(now - 1800000), // 30 minutes ago
          metadata: {},
        },
      ];

      overlay = addMetrics(overlay, metrics);
      overlay.metadata.dataRetention = 3600000; // 1 hour

      const cleaned = cleanupOldMetrics(overlay);

      const remainingMetrics = cleaned.metrics.get('metric') || [];
      expect(remainingMetrics).toHaveLength(1);
      expect(remainingMetrics[0].value).toBe(20);
    });

    it('should cleanup resolved alerts', () => {
      let overlay = createObservabilityOverlay('doc-123');

      const now = Date.now();

      const alert1 = createAlert('Alert 1', 'critical', 'Message', 'source');
      alert1.timestamp = new Date(now - 7200000); // 2 hours ago

      const alert2 = createAlert('Alert 2', 'warning', 'Message', 'source');
      alert2.timestamp = new Date(now - 1800000); // 30 minutes ago

      overlay = addAlert(overlay, alert1);
      overlay = addAlert(overlay, alert2);
      overlay = resolveAlert(overlay, alert1.id);

      overlay.metadata.dataRetention = 3600000; // 1 hour

      const cleaned = cleanupOldMetrics(overlay);

      // Only alert2 should remain (alert1 is old and resolved)
      expect(cleaned.alerts).toHaveLength(1);
      expect(cleaned.alerts[0].id).toBe(alert2.id);
    });
  });
});
