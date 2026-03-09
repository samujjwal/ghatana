/**
 * Tests for monitoring infrastructure
 */

import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

import {
  createMonitoringManager,
  recordCounter,
  recordGauge,
  recordHistogram,
  recordSummary,
  recordRenderMetrics,
  recordCollaborationMetrics,
  recordExportMetrics,
  exportPrometheusMetrics,
  addAlertThreshold,
  removeAlertThreshold,
  checkAlerts,
  subscribeToAlerts,
  getActiveAlerts,
  getAllAlerts,
  clearResolvedAlerts,
  createDashboard,
  createCanvasPerformanceDashboard,
  createCollaborationDashboard,
  createExportDashboard,
  startMonitoring,
  stopMonitoring,
  getMetric,
  getAllMetrics,
  getMetricsByType,
  clearMetrics,
  getConfig,
  updateConfig,
} from '../monitoring';

import type {
  MonitoringState,
  CounterMetric,
  GaugeMetric,
  HistogramMetric,
  SummaryMetric,
  AlertThreshold,
  AlertEvent,
} from '../monitoring';

describe('Monitoring Manager', () => {
  describe('Manager Creation', () => {
    it('should create manager with default config', () => {
      const manager = createMonitoringManager();

      expect(manager.config.enabled).toBe(true);
      expect(manager.config.collectionInterval).toBe(5000);
      expect(manager.config.maxMetrics).toBe(10000);
      expect(manager.config.enableAlerts).toBe(true);
      expect(manager.config.alertCheckInterval).toBe(10000);
      expect(manager.config.prometheusEndpoint).toBe('/metrics');
      expect(manager.metrics.size).toBe(0);
      expect(manager.alerts.size).toBe(0);
      expect(manager.thresholds.size).toBe(0);
    });

    it('should create manager with custom config', () => {
      const manager = createMonitoringManager({
        collectionInterval: 1000,
        maxMetrics: 5000,
        enableAlerts: false,
      });

      expect(manager.config.collectionInterval).toBe(1000);
      expect(manager.config.maxMetrics).toBe(5000);
      expect(manager.config.enableAlerts).toBe(false);
      expect(manager.config.enabled).toBe(true); // default preserved
    });
  });

  describe('Counter Metrics', () => {
    let manager: MonitoringState;

    beforeEach(() => {
      manager = createMonitoringManager();
    });

    it('should record counter metric', () => {
      const metric = recordCounter(manager, 'test_counter', 5, [], 'Test counter');

      expect(metric.name).toBe('test_counter');
      expect(metric.type).toBe('counter');
      expect(metric.value).toBe(5);
      expect(metric.help).toBe('Test counter');
      expect(manager.metrics.size).toBe(1);
    });

    it('should increment counter on subsequent records', () => {
      recordCounter(manager, 'test_counter', 5);
      const metric = recordCounter(manager, 'test_counter', 3);

      expect(metric.value).toBe(8);
      expect(manager.metrics.size).toBe(1);
    });

    it('should preserve help text on increment', () => {
      recordCounter(manager, 'test_counter', 5, [], 'Original help');
      const metric = recordCounter(manager, 'test_counter', 3);

      expect(metric.help).toBe('Original help');
    });

    it('should record counter with labels', () => {
      const metric = recordCounter(
        manager,
        'http_requests_total',
        1,
        [
          { name: 'method', value: 'GET' },
          { name: 'status', value: '200' },
        ],
        'HTTP requests'
      );

      expect(metric.labels).toHaveLength(2);
      expect(metric.labels[0]).toEqual({ name: 'method', value: 'GET' });
    });
  });

  describe('Gauge Metrics', () => {
    let manager: MonitoringState;

    beforeEach(() => {
      manager = createMonitoringManager();
    });

    it('should record gauge metric', () => {
      const metric = recordGauge(manager, 'test_gauge', 42.5, [], 'Test gauge');

      expect(metric.name).toBe('test_gauge');
      expect(metric.type).toBe('gauge');
      expect(metric.value).toBe(42.5);
      expect(metric.help).toBe('Test gauge');
    });

    it('should replace gauge value on subsequent records', () => {
      recordGauge(manager, 'test_gauge', 10);
      const metric = recordGauge(manager, 'test_gauge', 20);

      expect(metric.value).toBe(20);
      expect(manager.metrics.size).toBe(1);
    });

    it('should handle negative gauge values', () => {
      const metric = recordGauge(manager, 'temperature', -5.5);

      expect(metric.value).toBe(-5.5);
    });
  });

  describe('Histogram Metrics', () => {
    let manager: MonitoringState;

    beforeEach(() => {
      manager = createMonitoringManager();
    });

    it('should record histogram metric with default buckets', () => {
      const metric = recordHistogram(
        manager,
        'request_duration',
        0.15,
        undefined,
        [],
        'Request duration'
      );

      expect(metric.name).toBe('request_duration');
      expect(metric.type).toBe('histogram');
      expect(metric.sum).toBe(0.15);
      expect(metric.count).toBe(1);
      expect(metric.buckets.length).toBeGreaterThan(0);
    });

    it('should accumulate histogram observations', () => {
      recordHistogram(manager, 'request_duration', 0.1);
      recordHistogram(manager, 'request_duration', 0.2);
      const metric = recordHistogram(manager, 'request_duration', 0.3);

      expect(metric.sum).toBeCloseTo(0.6, 10); // Use toBeCloseTo for floating point
      expect(metric.count).toBe(3);
    });

    it('should update bucket counts correctly', () => {
      recordHistogram(manager, 'test_histogram', 0.01); // falls in 0.01 bucket
      recordHistogram(manager, 'test_histogram', 0.5); // falls in 0.5 bucket
      const metric = recordHistogram(manager, 'test_histogram', 2); // falls in 2.5 bucket

      const bucket01 = metric.buckets.find((b) => b.le === 0.01);
      const bucket05 = metric.buckets.find((b) => b.le === 0.5);
      const bucket25 = metric.buckets.find((b) => b.le === 2.5);

      expect(bucket01?.count).toBeGreaterThan(0);
      expect(bucket05?.count).toBeGreaterThan(0);
      expect(bucket25?.count).toBeGreaterThan(0);
    });

    it('should include +Inf bucket', () => {
      const metric = recordHistogram(manager, 'test_histogram', 100);

      const infBucket = metric.buckets.find((b) => b.le === Infinity);
      expect(infBucket).toBeDefined();
      expect(infBucket?.count).toBe(1);
    });

    it('should use custom buckets', () => {
      const customBuckets = [10, 50, 100, 500];
      const metric = recordHistogram(
        manager,
        'custom_histogram',
        75,
        customBuckets
      );

      expect(metric.buckets.some((b) => b.le === 10)).toBe(true);
      expect(metric.buckets.some((b) => b.le === 100)).toBe(true);
    });
  });

  describe('Summary Metrics', () => {
    let manager: MonitoringState;

    beforeEach(() => {
      manager = createMonitoringManager();
    });

    it('should record summary metric', () => {
      const metric = recordSummary(
        manager,
        'response_size',
        1024,
        [],
        'Response size'
      );

      expect(metric.name).toBe('response_size');
      expect(metric.type).toBe('summary');
      expect(metric.sum).toBe(1024);
      expect(metric.count).toBe(1);
    });

    it('should accumulate summary observations', () => {
      recordSummary(manager, 'response_size', 1000);
      recordSummary(manager, 'response_size', 2000);
      const metric = recordSummary(manager, 'response_size', 3000);

      expect(metric.sum).toBe(6000);
      expect(metric.count).toBe(3);
    });
  });

  describe('Canvas-Specific Metrics', () => {
    let manager: MonitoringState;

    beforeEach(() => {
      manager = createMonitoringManager();
    });

    it('should record render metrics', () => {
      recordRenderMetrics(manager, {
        fps: 60,
        frameTime: 16.67,
        renderTime: 10.5,
        nodeCount: 100,
        edgeCount: 50,
        droppedFrames: 2,
      });

      expect(manager.metrics.has('canvas_fps')).toBe(true);
      expect(manager.metrics.has('canvas_frame_time_ms')).toBe(true);
      expect(manager.metrics.has('canvas_render_time_ms')).toBe(true);
      expect(manager.metrics.has('canvas_node_count')).toBe(true);
      expect(manager.metrics.has('canvas_edge_count')).toBe(true);
      expect(manager.metrics.has('canvas_dropped_frames_total')).toBe(true);

      const fpsMetric = manager.metrics.get('canvas_fps') as GaugeMetric;
      expect(fpsMetric.value).toBe(60);
    });

    it('should record collaboration metrics', () => {
      recordCollaborationMetrics(manager, {
        messageLatency: 50,
        presenceUpdateLatency: 30,
        conflictCount: 1,
        connectionCount: 5,
      });

      expect(manager.metrics.has('collab_message_latency_ms')).toBe(true);
      expect(manager.metrics.has('collab_presence_latency_ms')).toBe(true);
      expect(manager.metrics.has('collab_conflict_total')).toBe(true);
      expect(manager.metrics.has('collab_connections')).toBe(true);

      const connectionsMetric = manager.metrics.get(
        'collab_connections'
      ) as GaugeMetric;
      expect(connectionsMetric.value).toBe(5);
    });

    it('should record export metrics', () => {
      recordExportMetrics(manager, {
        successCount: 10,
        failureCount: 1,
        averageDuration: 500,
        format: 'png',
      });

      expect(manager.metrics.has('export_success_total')).toBe(true);
      expect(manager.metrics.has('export_failure_total')).toBe(true);
      expect(manager.metrics.has('export_duration_ms')).toBe(true);

      const successMetric = manager.metrics.get(
        'export_success_total'
      ) as CounterMetric;
      expect(successMetric.value).toBe(10);
      expect(successMetric.labels).toEqual([{ name: 'format', value: 'png' }]);
    });
  });

  describe('Prometheus Export', () => {
    let manager: MonitoringState;

    beforeEach(() => {
      manager = createMonitoringManager();
    });

    it('should export counter in Prometheus format', () => {
      recordCounter(manager, 'test_counter', 42, [], 'Test counter');

      const output = exportPrometheusMetrics(manager);

      expect(output).toContain('# HELP test_counter Test counter');
      expect(output).toContain('# TYPE test_counter counter');
      expect(output).toContain('test_counter 42');
    });

    it('should export gauge in Prometheus format', () => {
      recordGauge(manager, 'test_gauge', 3.14, [], 'Test gauge');

      const output = exportPrometheusMetrics(manager);

      expect(output).toContain('# HELP test_gauge Test gauge');
      expect(output).toContain('# TYPE test_gauge gauge');
      expect(output).toContain('test_gauge 3.14');
    });

    it('should export histogram in Prometheus format', () => {
      recordHistogram(manager, 'test_histogram', 0.5, [0.1, 0.5, 1], [], 'Test histogram');

      const output = exportPrometheusMetrics(manager);

      expect(output).toContain('# HELP test_histogram Test histogram');
      expect(output).toContain('# TYPE test_histogram histogram');
      expect(output).toContain('test_histogram_bucket');
      expect(output).toContain('test_histogram_sum');
      expect(output).toContain('test_histogram_count');
    });

    it('should export metrics with labels', () => {
      recordCounter(
        manager,
        'http_requests',
        1,
        [
          { name: 'method', value: 'GET' },
          { name: 'status', value: '200' },
        ],
        'HTTP requests'
      );

      const output = exportPrometheusMetrics(manager);

      expect(output).toContain('http_requests{method="GET",status="200"}');
    });

    it('should export multiple metrics', () => {
      recordCounter(manager, 'counter1', 1, [], 'Counter 1');
      recordGauge(manager, 'gauge1', 2, [], 'Gauge 1');

      const output = exportPrometheusMetrics(manager);

      expect(output).toContain('counter1');
      expect(output).toContain('gauge1');
    });
  });

  describe('Alert Thresholds', () => {
    let manager: MonitoringState;

    beforeEach(() => {
      manager = createMonitoringManager();
    });

    it('should add alert threshold', () => {
      const threshold: AlertThreshold = {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU usage high',
      };

      addAlertThreshold(manager, threshold);

      expect(manager.thresholds.size).toBe(1);
      expect(manager.thresholds.get('cpu_usage')).toEqual(threshold);
    });

    it('should remove alert threshold', () => {
      const threshold: AlertThreshold = {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU usage high',
      };

      addAlertThreshold(manager, threshold);
      const removed = removeAlertThreshold(manager, 'cpu_usage');

      expect(removed).toBe(true);
      expect(manager.thresholds.size).toBe(0);
    });

    it('should return false when removing non-existent threshold', () => {
      const removed = removeAlertThreshold(manager, 'non_existent');

      expect(removed).toBe(false);
    });
  });

  describe('Alert Checking', () => {
    let manager: MonitoringState;

    beforeEach(() => {
      manager = createMonitoringManager();
    });

    it('should trigger alert when threshold exceeded (>)', () => {
      addAlertThreshold(manager, {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU high',
      });

      recordGauge(manager, 'cpu_usage', 90);
      const alerts = checkAlerts(manager);

      expect(alerts).toHaveLength(1);
      expect(alerts[0].threshold.message).toBe('CPU high');
      expect(alerts[0].currentValue).toBe(90);
      expect(alerts[0].resolved).toBe(false);
    });

    it('should trigger alert when threshold exceeded (<)', () => {
      addAlertThreshold(manager, {
        metric: 'disk_space',
        operator: '<',
        value: 10,
        severity: 'critical',
        message: 'Disk space low',
      });

      recordGauge(manager, 'disk_space', 5);
      const alerts = checkAlerts(manager);

      expect(alerts).toHaveLength(1);
      expect(alerts[0].threshold.severity).toBe('critical');
    });

    it('should trigger alert when threshold exceeded (>=)', () => {
      addAlertThreshold(manager, {
        metric: 'error_rate',
        operator: '>=',
        value: 5,
        severity: 'warning',
        message: 'Error rate high',
      });

      recordGauge(manager, 'error_rate', 5);
      const alerts = checkAlerts(manager);

      expect(alerts).toHaveLength(1);
    });

    it('should trigger alert when threshold exceeded (<=)', () => {
      addAlertThreshold(manager, {
        metric: 'free_memory',
        operator: '<=',
        value: 100,
        severity: 'warning',
        message: 'Low memory',
      });

      recordGauge(manager, 'free_memory', 100);
      const alerts = checkAlerts(manager);

      expect(alerts).toHaveLength(1);
    });

    it('should trigger alert when threshold exceeded (==)', () => {
      addAlertThreshold(manager, {
        metric: 'status_code',
        operator: '==',
        value: 500,
        severity: 'critical',
        message: 'Internal server error',
      });

      recordGauge(manager, 'status_code', 500);
      const alerts = checkAlerts(manager);

      expect(alerts).toHaveLength(1);
    });

    it('should trigger alert when threshold exceeded (!=)', () => {
      addAlertThreshold(manager, {
        metric: 'health_check',
        operator: '!=',
        value: 1,
        severity: 'critical',
        message: 'Service unhealthy',
      });

      recordGauge(manager, 'health_check', 0);
      const alerts = checkAlerts(manager);

      expect(alerts).toHaveLength(1);
    });

    it('should not trigger alert when threshold not exceeded', () => {
      addAlertThreshold(manager, {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU high',
      });

      recordGauge(manager, 'cpu_usage', 50);
      const alerts = checkAlerts(manager);

      expect(alerts).toHaveLength(0);
    });

    it('should not trigger duplicate alerts', () => {
      addAlertThreshold(manager, {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU high',
      });

      recordGauge(manager, 'cpu_usage', 90);
      checkAlerts(manager);
      const alerts2 = checkAlerts(manager);

      expect(alerts2).toHaveLength(0); // No new alerts
    });

    it('should resolve alert when metric returns to normal', () => {
      addAlertThreshold(manager, {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU high',
      });

      recordGauge(manager, 'cpu_usage', 90);
      checkAlerts(manager);

      recordGauge(manager, 'cpu_usage', 50);
      checkAlerts(manager);

      const alert = manager.alerts.get('cpu_usage');
      expect(alert?.resolved).toBe(true);
      expect(alert?.resolvedAt).toBeInstanceOf(Date);
    });

    it('should skip histogram and summary metrics', () => {
      addAlertThreshold(manager, {
        metric: 'test_histogram',
        operator: '>',
        value: 100,
        severity: 'warning',
        message: 'High value',
      });

      recordHistogram(manager, 'test_histogram', 200);
      const alerts = checkAlerts(manager);

      expect(alerts).toHaveLength(0);
    });
  });

  describe('Alert Subscriptions', () => {
    let manager: MonitoringState;

    beforeEach(() => {
      manager = createMonitoringManager();
    });

    it('should notify listeners when alert triggered', () => {
      const listener = vi.fn();
      subscribeToAlerts(manager, listener);

      addAlertThreshold(manager, {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU high',
      });

      recordGauge(manager, 'cpu_usage', 90);
      checkAlerts(manager);

      expect(listener).toHaveBeenCalledTimes(1);
      expect(listener).toHaveBeenCalledWith(
        expect.objectContaining({
          threshold: expect.objectContaining({ message: 'CPU high' }),
          currentValue: 90,
        })
      );
    });

    it('should support multiple listeners', () => {
      const listener1 = vi.fn();
      const listener2 = vi.fn();

      subscribeToAlerts(manager, listener1);
      subscribeToAlerts(manager, listener2);

      addAlertThreshold(manager, {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU high',
      });

      recordGauge(manager, 'cpu_usage', 90);
      checkAlerts(manager);

      expect(listener1).toHaveBeenCalledTimes(1);
      expect(listener2).toHaveBeenCalledTimes(1);
    });

    it('should unsubscribe listener', () => {
      const listener = vi.fn();
      const unsubscribe = subscribeToAlerts(manager, listener);

      unsubscribe();

      addAlertThreshold(manager, {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU high',
      });

      recordGauge(manager, 'cpu_usage', 90);
      checkAlerts(manager);

      expect(listener).not.toHaveBeenCalled();
    });

    it('should handle listener errors gracefully', () => {
      const errorListener = vi.fn(() => {
        throw new Error('Listener error');
      });
      const goodListener = vi.fn();

      subscribeToAlerts(manager, errorListener);
      subscribeToAlerts(manager, goodListener);

      addAlertThreshold(manager, {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU high',
      });

      recordGauge(manager, 'cpu_usage', 90);

      // Should not throw and should call other listeners
      expect(() => checkAlerts(manager)).not.toThrow();
      expect(goodListener).toHaveBeenCalled();
    });
  });

  describe('Alert Queries', () => {
    let manager: MonitoringState;

    beforeEach(() => {
      manager = createMonitoringManager();
    });

    it('should get active alerts', () => {
      addAlertThreshold(manager, {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU high',
      });

      recordGauge(manager, 'cpu_usage', 90);
      checkAlerts(manager);

      const activeAlerts = getActiveAlerts(manager);
      expect(activeAlerts).toHaveLength(1);
      expect(activeAlerts[0].resolved).toBe(false);
    });

    it('should get all alerts including resolved', () => {
      addAlertThreshold(manager, {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU high',
      });

      recordGauge(manager, 'cpu_usage', 90);
      checkAlerts(manager);

      recordGauge(manager, 'cpu_usage', 50);
      checkAlerts(manager);

      const allAlerts = getAllAlerts(manager);
      expect(allAlerts).toHaveLength(1);
      expect(allAlerts[0].resolved).toBe(true);
    });

    it('should clear resolved alerts', () => {
      addAlertThreshold(manager, {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU high',
      });

      recordGauge(manager, 'cpu_usage', 90);
      checkAlerts(manager);

      recordGauge(manager, 'cpu_usage', 50);
      checkAlerts(manager);

      const cleared = clearResolvedAlerts(manager);

      expect(cleared).toBe(1);
      expect(manager.alerts.size).toBe(0);
    });

    it('should not clear active alerts', () => {
      addAlertThreshold(manager, {
        metric: 'cpu_usage',
        operator: '>',
        value: 80,
        severity: 'warning',
        message: 'CPU high',
      });

      recordGauge(manager, 'cpu_usage', 90);
      checkAlerts(manager);

      const cleared = clearResolvedAlerts(manager);

      expect(cleared).toBe(0);
      expect(manager.alerts.size).toBe(1);
    });
  });

  describe('Dashboard Generation', () => {
    it('should create generic dashboard', () => {
      const dashboard = createDashboard(
        'test-dashboard',
        'Test Dashboard',
        [
          {
            id: 'panel1',
            title: 'Panel 1',
            type: 'graph',
            metrics: ['metric1'],
            refreshInterval: 5000,
          },
        ],
        ['tag1', 'tag2'],
        '10s'
      );

      expect(dashboard.uid).toBe('test-dashboard');
      expect(dashboard.title).toBe('Test Dashboard');
      expect(dashboard.panels).toHaveLength(1);
      expect(dashboard.tags).toEqual(['tag1', 'tag2']);
      expect(dashboard.refresh).toBe('10s');
    });

    it('should create canvas performance dashboard', () => {
      const dashboard = createCanvasPerformanceDashboard();

      expect(dashboard.uid).toBe('canvas-performance');
      expect(dashboard.title).toBe('Canvas Performance');
      expect(dashboard.panels.length).toBeGreaterThan(0);
      expect(dashboard.tags).toContain('canvas');
      expect(dashboard.tags).toContain('performance');

      const fpsPanel = dashboard.panels.find((p) => p.id === 'fps');
      expect(fpsPanel).toBeDefined();
      expect(fpsPanel?.metrics).toContain('canvas_fps');
    });

    it('should create collaboration dashboard', () => {
      const dashboard = createCollaborationDashboard();

      expect(dashboard.uid).toBe('canvas-collaboration');
      expect(dashboard.title).toBe('Canvas Collaboration');
      expect(dashboard.tags).toContain('collaboration');

      const latencyPanel = dashboard.panels.find((p) => p.id === 'message-latency');
      expect(latencyPanel).toBeDefined();
      expect(latencyPanel?.metrics).toContain('collab_message_latency_ms');
    });

    it('should create export dashboard', () => {
      const dashboard = createExportDashboard();

      expect(dashboard.uid).toBe('canvas-exports');
      expect(dashboard.title).toBe('Canvas Export Operations');
      expect(dashboard.tags).toContain('exports');

      const successPanel = dashboard.panels.find((p) => p.id === 'export-success');
      expect(successPanel).toBeDefined();
      expect(successPanel?.metrics).toContain('export_success_total');
    });
  });

  describe('Lifecycle Management', () => {
    let manager: MonitoringState;

    beforeEach(() => {
      manager = createMonitoringManager();
    });

    afterEach(() => {
      stopMonitoring(manager);
    });

    it('should start monitoring', () => {
      startMonitoring(manager);

      expect(manager.collectionTimer).toBeDefined();
      expect(manager.alertCheckTimer).toBeDefined();
    });

    it('should not start monitoring if disabled', () => {
      manager.config.enabled = false;
      startMonitoring(manager);

      expect(manager.collectionTimer).toBeUndefined();
    });

    it('should not start alert checking if disabled', () => {
      manager.config.enableAlerts = false;
      startMonitoring(manager);

      expect(manager.collectionTimer).toBeDefined();
      expect(manager.alertCheckTimer).toBeUndefined();
    });

    it('should stop monitoring', () => {
      startMonitoring(manager);
      stopMonitoring(manager);

      expect(manager.collectionTimer).toBeUndefined();
      expect(manager.alertCheckTimer).toBeUndefined();
    });
  });

  describe('Metric Queries', () => {
    let manager: MonitoringState;

    beforeEach(() => {
      manager = createMonitoringManager();
    });

    it('should get metric by name', () => {
      recordCounter(manager, 'test_counter', 42);

      const metric = getMetric(manager, 'test_counter');

      expect(metric).not.toBeNull();
      expect(metric?.name).toBe('test_counter');
      expect((metric as CounterMetric).value).toBe(42);
    });

    it('should return null for non-existent metric', () => {
      const metric = getMetric(manager, 'non_existent');

      expect(metric).toBeNull();
    });

    it('should get all metrics', () => {
      recordCounter(manager, 'counter1', 1);
      recordGauge(manager, 'gauge1', 2);
      recordHistogram(manager, 'histogram1', 3);

      const metrics = getAllMetrics(manager);

      expect(metrics).toHaveLength(3);
    });

    it('should get metrics by type', () => {
      recordCounter(manager, 'counter1', 1);
      recordCounter(manager, 'counter2', 2);
      recordGauge(manager, 'gauge1', 3);

      const counters = getMetricsByType(manager, 'counter');
      const gauges = getMetricsByType(manager, 'gauge');

      expect(counters).toHaveLength(2);
      expect(gauges).toHaveLength(1);
    });

    it('should clear all metrics', () => {
      recordCounter(manager, 'counter1', 1);
      recordGauge(manager, 'gauge1', 2);

      clearMetrics(manager);

      expect(manager.metrics.size).toBe(0);
    });
  });

  describe('Configuration Management', () => {
    let manager: MonitoringState;

    beforeEach(() => {
      manager = createMonitoringManager();
    });

    afterEach(() => {
      stopMonitoring(manager);
    });

    it('should get configuration', () => {
      const config = getConfig(manager);

      expect(config.enabled).toBe(true);
      expect(config.collectionInterval).toBe(5000);
    });

    it('should update configuration', () => {
      const updated = updateConfig(manager, {
        collectionInterval: 1000,
        maxMetrics: 5000,
      });

      expect(updated.collectionInterval).toBe(1000);
      expect(updated.maxMetrics).toBe(5000);
      expect(updated.enabled).toBe(true); // unchanged
    });

    it('should restart monitoring when intervals changed', () => {
      startMonitoring(manager);
      const oldTimer = manager.collectionTimer;

      updateConfig(manager, { collectionInterval: 1000 });

      expect(manager.collectionTimer).not.toBe(oldTimer);
    });
  });

  describe('Max Metrics Enforcement', () => {
    it('should enforce max metrics limit', () => {
      const manager = createMonitoringManager({ maxMetrics: 5 });

      // Add 10 metrics
      for (let i = 0; i < 10; i++) {
        recordCounter(manager, `metric_${i}`, 1);
      }

      // Should only keep 5 metrics
      expect(manager.metrics.size).toBe(5);
    });

    it('should evict oldest metrics first (LRU)', () => {
      const manager = createMonitoringManager({ maxMetrics: 3 });

      recordCounter(manager, 'metric_1', 1);
      recordCounter(manager, 'metric_2', 2);
      recordCounter(manager, 'metric_3', 3);
      recordCounter(manager, 'metric_4', 4); // Should evict metric_1

      expect(manager.metrics.has('metric_1')).toBe(false);
      expect(manager.metrics.has('metric_2')).toBe(true);
      expect(manager.metrics.has('metric_3')).toBe(true);
      expect(manager.metrics.has('metric_4')).toBe(true);
    });
  });
});
