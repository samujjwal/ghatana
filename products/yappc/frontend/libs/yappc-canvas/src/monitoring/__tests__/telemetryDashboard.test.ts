/**
 * Tests for Performance Metrics Dashboard (Feature 2.29)
 * 
 * Tests comprehensive telemetry system including:
 * - FPS/memory monitoring
 * - Performance trace collection
 * - OTLP export
 * - Threshold alerting
 * - Dev mode overlay
 * - Web Vitals tracking
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import {
  createTelemetryDashboard,
  startMonitoring,
  stopMonitoring,
  collectMetrics,
  startTrace,
  endTrace,
  addSpan,
  exportTracesJSON,
  convertToOTLP,
  checkThresholds,
  acknowledgeAlert,
  getTelemetryStatistics,
  toggleDevMode,
  clearTelemetryData,
  type TelemetryDashboard,
  type PerformanceTrace,
  type AlertThreshold,
  type PerformanceAlert
} from '../telemetryDashboard';

describe('telemetryDashboard', () => {
  let dashboard: TelemetryDashboard;

  beforeEach(() => {
    vi.useFakeTimers();
    dashboard = createTelemetryDashboard();
  });

  afterEach(() => {
    stopMonitoring(dashboard);
    vi.restoreAllMocks();
  });

  // ============================================================================
  // Dashboard Creation & Configuration
  // ============================================================================

  describe('createTelemetryDashboard', () => {
    it('should create dashboard with default configuration', () => {
      expect(dashboard.config.enabled).toBe(true);
      expect(dashboard.config.sampleInterval).toBe(1000);
      expect(dashboard.config.maxTraces).toBe(100);
      expect(dashboard.config.maxAlerts).toBe(50);
      expect(dashboard.config.devModeEnabled).toBe(false);
      expect(dashboard.config.devModePosition).toBe('bottom-right');
      expect(dashboard.config.thresholds.length).toBeGreaterThan(0);
    });

    it('should create dashboard with custom configuration', () => {
      const custom = createTelemetryDashboard({
        sampleInterval: 500,
        maxTraces: 50,
        devModeEnabled: true,
        devModePosition: 'top-left',
        otlpEndpoint: 'http://localhost:4318/v1/metrics'
      });

      expect(custom.config.sampleInterval).toBe(500);
      expect(custom.config.maxTraces).toBe(50);
      expect(custom.config.devModeEnabled).toBe(true);
      expect(custom.config.devModePosition).toBe('top-left');
      expect(custom.config.otlpEndpoint).toBe('http://localhost:4318/v1/metrics');
    });

    it('should initialize empty data arrays', () => {
      expect(dashboard.metrics).toEqual([]);
      expect(dashboard.traces).toEqual([]);
      expect(dashboard.alerts).toEqual([]);
      expect(dashboard.isMonitoring).toBe(false);
    });

    it('should have default thresholds configured', () => {
      const fpsThreshold = dashboard.config.thresholds.find(t => t.id === 'fps-low');
      expect(fpsThreshold).toBeDefined();
      expect(fpsThreshold?.metric).toBe('fps');
      expect(fpsThreshold?.operator).toBe('lt');
      expect(fpsThreshold?.value).toBe(30);

      const memThreshold = dashboard.config.thresholds.find(t => t.id === 'memory-high');
      expect(memThreshold).toBeDefined();
      expect(memThreshold?.metric).toBe('memory.percentUsed');
      expect(memThreshold?.operator).toBe('gt');
      expect(memThreshold?.value).toBe(80);
    });
  });

  // ============================================================================
  // Monitoring Control
  // ============================================================================

  describe('startMonitoring & stopMonitoring', () => {
    it('should start monitoring and collect metrics at intervals', () => {
      startMonitoring(dashboard);
      expect(dashboard.isMonitoring).toBe(true);
      expect(dashboard.intervalId).toBeDefined();

      // Initial state
      expect(dashboard.metrics.length).toBe(0);

      // After 1 second
      vi.advanceTimersByTime(1000);
      expect(dashboard.metrics.length).toBe(1);

      // After 3 seconds
      vi.advanceTimersByTime(2000);
      expect(dashboard.metrics.length).toBe(3);
    });

    it('should stop monitoring and clear intervals', () => {
      startMonitoring(dashboard);
      const intervalId = dashboard.intervalId;
      expect(dashboard.isMonitoring).toBe(true);

      stopMonitoring(dashboard);
      expect(dashboard.isMonitoring).toBe(false);
      expect(dashboard.intervalId).toBeUndefined();
    });

    it('should not start monitoring if already monitoring', () => {
      startMonitoring(dashboard);
      const firstIntervalId = dashboard.intervalId;

      startMonitoring(dashboard); // Try to start again
      expect(dashboard.intervalId).toBe(firstIntervalId);
    });

    it('should trim metrics to max 1000 samples', () => {
      dashboard.config.sampleInterval = 1; // Very fast for testing
      startMonitoring(dashboard);

      // Collect 1100 samples
      vi.advanceTimersByTime(1100);
      expect(dashboard.metrics.length).toBeLessThanOrEqual(1000);
    });

    it('should start OTLP export interval when endpoint configured', () => {
      dashboard.config.otlpEndpoint = 'http://localhost:4318/v1/metrics';
      dashboard.config.otlpExportInterval = 60000;

      startMonitoring(dashboard);
      expect(dashboard.otlpIntervalId).toBeDefined();
    });

    it('should stop OTLP export interval when stopping monitoring', () => {
      dashboard.config.otlpEndpoint = 'http://localhost:4318/v1/metrics';
      startMonitoring(dashboard);

      stopMonitoring(dashboard);
      expect(dashboard.otlpIntervalId).toBeUndefined();
    });
  });

  // ============================================================================
  // Metrics Collection
  // ============================================================================

  describe('collectMetrics', () => {
    it('should collect comprehensive performance metrics', () => {
      const metrics = collectMetrics();

      expect(metrics).toHaveProperty('timestamp');
      expect(metrics).toHaveProperty('fps');
      expect(metrics).toHaveProperty('memory');
      expect(metrics).toHaveProperty('rendering');
      expect(metrics).toHaveProperty('interaction');
      expect(metrics).toHaveProperty('network');
      expect(metrics).toHaveProperty('vitals');
    });

    it('should collect memory metrics', () => {
      const metrics = collectMetrics();

      expect(metrics.memory).toHaveProperty('usedJSHeapSize');
      expect(metrics.memory).toHaveProperty('totalJSHeapSize');
      expect(metrics.memory).toHaveProperty('jsHeapSizeLimit');
      expect(metrics.memory).toHaveProperty('percentUsed');
      expect(metrics.memory.percentUsed).toBeGreaterThanOrEqual(0);
      expect(metrics.memory.percentUsed).toBeLessThanOrEqual(100);
    });

    it('should collect rendering metrics', () => {
      const metrics = collectMetrics();

      expect(metrics.rendering).toHaveProperty('elementCount');
      expect(metrics.rendering).toHaveProperty('visibleElements');
      expect(metrics.rendering).toHaveProperty('renderTime');
      expect(metrics.rendering).toHaveProperty('paintTime');
      expect(metrics.rendering.elementCount).toBeGreaterThanOrEqual(0);
    });

    it('should collect network metrics', () => {
      const metrics = collectMetrics();

      expect(metrics.network).toHaveProperty('online');
      expect(typeof metrics.network.online).toBe('boolean');
    });

    it('should collect Web Vitals', () => {
      const metrics = collectMetrics();

      expect(metrics.vitals).toBeDefined();
      // Values may be undefined if not available yet
      if (metrics.vitals.lcp !== undefined) {
        expect(metrics.vitals.lcp).toBeGreaterThanOrEqual(0);
      }
    });

    it('should calculate FPS', () => {
      const metrics = collectMetrics();

      expect(metrics.fps).toBeGreaterThan(0);
      // In test mode, FPS calculation may return default value
      expect(typeof metrics.fps).toBe('number');
    });
  });

  // ============================================================================
  // Performance Tracing
  // ============================================================================

  describe('Performance Tracing', () => {
    it('should start a new trace', () => {
      const trace = startTrace(dashboard, 'canvas-render', {
        nodeCount: 100
      });

      expect(trace.id).toMatch(/^trace_/);
      expect(trace.name).toBe('canvas-render');
      expect(trace.metadata.nodeCount).toBe(100);
      expect(trace.startTime).toBeGreaterThan(0);
      expect(trace.spans).toEqual([]);
      expect(dashboard.activeTrace).toBe(trace);
    });

    it('should end a trace and calculate duration', () => {
      const trace = startTrace(dashboard, 'test-trace');
      const startTime = trace.startTime;

      vi.advanceTimersByTime(100);
      endTrace(dashboard, trace.id);

      expect(trace.endTime).toBeDefined();
      expect(trace.duration).toBeGreaterThanOrEqual(0);
      expect(dashboard.traces).toContain(trace);
      expect(dashboard.activeTrace).toBeUndefined();
    });

    it('should add spans to a trace', () => {
      const trace = startTrace(dashboard, 'render-pipeline');
      
      const span1 = addSpan(trace, 'layout-calculation', 0, 10, {
        elementCount: 50
      });
      const span2 = addSpan(trace, 'paint-nodes', 10, 25, {
        nodesRendered: 50
      }, span1.id);

      expect(trace.spans.length).toBe(2);
      expect(span1.name).toBe('layout-calculation');
      expect(span1.duration).toBe(10);
      expect(span2.parentSpanId).toBe(span1.id);
      expect(span2.duration).toBe(15);
    });

    it('should trim old traces when exceeding max', () => {
      dashboard.config.maxTraces = 5;

      for (let i = 0; i < 10; i++) {
        const trace = startTrace(dashboard, `trace-${i}`);
        endTrace(dashboard, trace.id);
      }

      expect(dashboard.traces.length).toBe(5);
      expect(dashboard.traces[0].name).toBe('trace-5');
      expect(dashboard.traces[4].name).toBe('trace-9');
    });

    it('should export traces to JSON', () => {
      const trace1 = startTrace(dashboard, 'trace-1');
      endTrace(dashboard, trace1.id);
      const trace2 = startTrace(dashboard, 'trace-2');
      endTrace(dashboard, trace2.id);

      const json = exportTracesJSON(dashboard);
      const data = JSON.parse(json);

      expect(data.traces.length).toBe(2);
      expect(data.metadata.totalTraces).toBe(2);
      expect(data.metadata.exportedAt).toBeDefined();
    });

    it('should track nested spans with parent-child relationships', () => {
      const trace = startTrace(dashboard, 'complex-operation');
      
      const parentSpan = addSpan(trace, 'parent-operation', 0, 100);
      const childSpan1 = addSpan(trace, 'child-op-1', 10, 40, {}, parentSpan.id);
      const childSpan2 = addSpan(trace, 'child-op-2', 50, 90, {}, parentSpan.id);

      expect(childSpan1.parentSpanId).toBe(parentSpan.id);
      expect(childSpan2.parentSpanId).toBe(parentSpan.id);
      expect(trace.spans.length).toBe(3);
    });
  });

  // ============================================================================
  // OTLP Export
  // ============================================================================

  describe('OTLP Export', () => {
    it('should convert metrics to OTLP format', () => {
      const metrics = [
        {
          timestamp: 1000,
          fps: 60,
          memory: { usedJSHeapSize: 0, totalJSHeapSize: 0, jsHeapSizeLimit: 0, percentUsed: 50 },
          rendering: { elementCount: 0, visibleElements: 0, renderTime: 16, paintTime: 0, layoutTime: 0, scriptTime: 0, idleTime: 0 },
          interaction: { inputDelay: 0, eventLatency: 0, interactionCount: 0, longTasks: 0, blockedTime: 0 },
          network: { online: true },
          vitals: { lcp: 1200 }
        }
      ];

      const otlp = convertToOTLP(metrics);

      expect(otlp.resourceMetrics).toBeDefined();
      expect(otlp.resourceMetrics[0].resource.attributes['service.name']).toBe('canvas-app');
      expect(otlp.resourceMetrics[0].scopeMetrics[0].metrics.length).toBeGreaterThan(0);

      const fpsMetric = otlp.resourceMetrics[0].scopeMetrics[0].metrics.find(
        m => m.name === 'canvas.performance.fps'
      );
      expect(fpsMetric).toBeDefined();
      expect(fpsMetric?.type).toBe('gauge');
      expect(fpsMetric?.unit).toBe('fps');
      expect(fpsMetric?.dataPoints[0].value).toBe(60);
    });

    it('should include multiple metric types in OTLP export', () => {
      const metrics = [
        {
          timestamp: 1000,
          fps: 55,
          memory: { usedJSHeapSize: 0, totalJSHeapSize: 0, jsHeapSizeLimit: 0, percentUsed: 75 },
          rendering: { elementCount: 0, visibleElements: 0, renderTime: 20, paintTime: 0, layoutTime: 0, scriptTime: 0, idleTime: 0 },
          interaction: { inputDelay: 0, eventLatency: 0, interactionCount: 0, longTasks: 0, blockedTime: 0 },
          network: { online: true },
          vitals: { lcp: 2500 }
        }
      ];

      const otlp = convertToOTLP(metrics);
      const exportedMetrics = otlp.resourceMetrics[0].scopeMetrics[0].metrics;

      expect(exportedMetrics.some(m => m.name === 'canvas.performance.fps')).toBe(true);
      expect(exportedMetrics.some(m => m.name === 'canvas.performance.memory.percent_used')).toBe(true);
      expect(exportedMetrics.some(m => m.name === 'canvas.performance.render_time')).toBe(true);
      expect(exportedMetrics.some(m => m.name === 'canvas.performance.lcp')).toBe(true);
    });

    it('should handle missing Web Vitals gracefully', () => {
      const metrics = [
        {
          timestamp: 1000,
          fps: 60,
          memory: { usedJSHeapSize: 0, totalJSHeapSize: 0, jsHeapSizeLimit: 0, percentUsed: 50 },
          rendering: { elementCount: 0, visibleElements: 0, renderTime: 16, paintTime: 0, layoutTime: 0, scriptTime: 0, idleTime: 0 },
          interaction: { inputDelay: 0, eventLatency: 0, interactionCount: 0, longTasks: 0, blockedTime: 0 },
          network: { online: true },
          vitals: {} // No vitals available
        }
      ];

      const otlp = convertToOTLP(metrics);
      const lcpMetric = otlp.resourceMetrics[0].scopeMetrics[0].metrics.find(
        m => m.name === 'canvas.performance.lcp'
      );

      expect(lcpMetric?.dataPoints.length).toBe(0);
    });
  });

  // ============================================================================
  // Threshold Alerting
  // ============================================================================

  describe('Threshold Alerting', () => {
    it('should trigger alert when FPS drops below threshold', () => {
      const metrics = {
        timestamp: Date.now(),
        fps: 20, // Below 30 threshold
        memory: { usedJSHeapSize: 0, totalJSHeapSize: 0, jsHeapSizeLimit: 0, percentUsed: 50 },
        rendering: { elementCount: 0, visibleElements: 0, renderTime: 16, paintTime: 0, layoutTime: 0, scriptTime: 0, idleTime: 0 },
        interaction: { inputDelay: 0, eventLatency: 0, interactionCount: 0, longTasks: 0, blockedTime: 0 },
        network: { online: true },
        vitals: {}
      };

      checkThresholds(dashboard, metrics);

      expect(dashboard.alerts.length).toBe(1);
      expect(dashboard.alerts[0].metric).toBe('fps');
      expect(dashboard.alerts[0].severity).toBe('warning');
      expect(dashboard.alerts[0].actualValue).toBe(20);
    });

    it('should trigger alert when memory usage exceeds threshold', () => {
      const metrics = {
        timestamp: Date.now(),
        fps: 60,
        memory: { usedJSHeapSize: 0, totalJSHeapSize: 0, jsHeapSizeLimit: 0, percentUsed: 85 }, // Above 80 threshold
        rendering: { elementCount: 0, visibleElements: 0, renderTime: 16, paintTime: 0, layoutTime: 0, scriptTime: 0, idleTime: 0 },
        interaction: { inputDelay: 0, eventLatency: 0, interactionCount: 0, longTasks: 0, blockedTime: 0 },
        network: { online: true },
        vitals: {}
      };

      checkThresholds(dashboard, metrics);

      expect(dashboard.alerts.length).toBe(1);
      expect(dashboard.alerts[0].metric).toBe('memory.percentUsed');
      expect(dashboard.alerts[0].severity).toBe('error');
      expect(dashboard.alerts[0].actualValue).toBe(85);
    });

    it('should respect cooldown period between alerts', () => {
      const threshold = dashboard.config.thresholds.find(t => t.id === 'fps-low')!;
      threshold.cooldownPeriod = 10000; // 10 seconds

      const lowFpsMetrics = {
        timestamp: Date.now(),
        fps: 20,
        memory: { usedJSHeapSize: 0, totalJSHeapSize: 0, jsHeapSizeLimit: 0, percentUsed: 50 },
        rendering: { elementCount: 0, visibleElements: 0, renderTime: 16, paintTime: 0, layoutTime: 0, scriptTime: 0, idleTime: 0 },
        interaction: { inputDelay: 0, eventLatency: 0, interactionCount: 0, longTasks: 0, blockedTime: 0 },
        network: { online: true },
        vitals: {}
      };

      checkThresholds(dashboard, lowFpsMetrics);
      expect(dashboard.alerts.length).toBe(1);

      // Try again immediately
      vi.advanceTimersByTime(5000); // 5 seconds later
      checkThresholds(dashboard, lowFpsMetrics);
      expect(dashboard.alerts.length).toBe(1); // No new alert

      // After cooldown period
      vi.advanceTimersByTime(6000); // 11 seconds total
      checkThresholds(dashboard, lowFpsMetrics);
      expect(dashboard.alerts.length).toBe(2); // New alert
    });

    it('should not trigger alert when threshold disabled', () => {
      const threshold = dashboard.config.thresholds.find(t => t.id === 'fps-low')!;
      threshold.enabled = false;

      const lowFpsMetrics = {
        timestamp: Date.now(),
        fps: 20,
        memory: { usedJSHeapSize: 0, totalJSHeapSize: 0, jsHeapSizeLimit: 0, percentUsed: 50 },
        rendering: { elementCount: 0, visibleElements: 0, renderTime: 16, paintTime: 0, layoutTime: 0, scriptTime: 0, idleTime: 0 },
        interaction: { inputDelay: 0, eventLatency: 0, interactionCount: 0, longTasks: 0, blockedTime: 0 },
        network: { online: true },
        vitals: {}
      };

      checkThresholds(dashboard, lowFpsMetrics);
      expect(dashboard.alerts.length).toBe(0);
    });

    it('should acknowledge alert', () => {
      const alert: PerformanceAlert = {
        id: 'alert-1',
        thresholdId: 'fps-low',
        timestamp: Date.now(),
        severity: 'warning',
        metric: 'fps',
        actualValue: 20,
        thresholdValue: 30,
        message: 'FPS below threshold',
        acknowledged: false
      };
      dashboard.alerts.push(alert);

      acknowledgeAlert(dashboard, 'alert-1', 'test-user');

      expect(alert.acknowledged).toBe(true);
      expect(alert.acknowledgedBy).toBe('test-user');
      expect(alert.acknowledgedAt).toBeDefined();
    });

    it('should trim alerts when exceeding max', () => {
      dashboard.config.maxAlerts = 5;

      const metrics = {
        timestamp: Date.now(),
        fps: 20,
        memory: { usedJSHeapSize: 0, totalJSHeapSize: 0, jsHeapSizeLimit: 0, percentUsed: 50 },
        rendering: { elementCount: 0, visibleElements: 0, renderTime: 16, paintTime: 0, layoutTime: 0, scriptTime: 0, idleTime: 0 },
        interaction: { inputDelay: 0, eventLatency: 0, interactionCount: 0, longTasks: 0, blockedTime: 0 },
        network: { online: true },
        vitals: {}
      };

      for (let i = 0; i < 10; i++) {
        vi.advanceTimersByTime(15000); // Wait for cooldown
        checkThresholds(dashboard, metrics);
      }

      expect(dashboard.alerts.length).toBe(5);
    });

    it('should emit custom event when alert is triggered', () => {
      const eventListener = vi.fn();
      window.addEventListener('telemetry-alert', eventListener);

      const metrics = {
        timestamp: Date.now(),
        fps: 20,
        memory: { usedJSHeapSize: 0, totalJSHeapSize: 0, jsHeapSizeLimit: 0, percentUsed: 50 },
        rendering: { elementCount: 0, visibleElements: 0, renderTime: 16, paintTime: 0, layoutTime: 0, scriptTime: 0, idleTime: 0 },
        interaction: { inputDelay: 0, eventLatency: 0, interactionCount: 0, longTasks: 0, blockedTime: 0 },
        network: { online: true },
        vitals: {}
      };

      checkThresholds(dashboard, metrics);

      expect(eventListener).toHaveBeenCalledOnce();
      window.removeEventListener('telemetry-alert', eventListener);
    });
  });

  // ============================================================================
  // Statistics & Reporting
  // ============================================================================

  describe('getTelemetryStatistics', () => {
    it('should calculate comprehensive statistics', () => {
      // Add some metrics
      dashboard.metrics = [
        {
          timestamp: 1000,
          fps: 60,
          memory: { usedJSHeapSize: 0, totalJSHeapSize: 0, jsHeapSizeLimit: 0, percentUsed: 50 },
          rendering: { elementCount: 0, visibleElements: 0, renderTime: 16, paintTime: 0, layoutTime: 0, scriptTime: 0, idleTime: 0 },
          interaction: { inputDelay: 0, eventLatency: 0, interactionCount: 0, longTasks: 2, blockedTime: 0 },
          network: { online: true },
          vitals: {}
        },
        {
          timestamp: 2000,
          fps: 55,
          memory: { usedJSHeapSize: 0, totalJSHeapSize: 0, jsHeapSizeLimit: 0, percentUsed: 60 },
          rendering: { elementCount: 0, visibleElements: 0, renderTime: 20, paintTime: 0, layoutTime: 0, scriptTime: 0, idleTime: 0 },
          interaction: { inputDelay: 0, eventLatency: 0, interactionCount: 0, longTasks: 1, blockedTime: 0 },
          network: { online: true },
          vitals: {}
        }
      ];

      // Add traces
      const trace = startTrace(dashboard, 'test-trace');
      endTrace(dashboard, trace.id);

      // Add alerts
      const alert: PerformanceAlert = {
        id: 'alert-1',
        thresholdId: 'test',
        timestamp: Date.now(),
        severity: 'warning',
        metric: 'fps',
        actualValue: 20,
        thresholdValue: 30,
        message: 'Test alert',
        acknowledged: false
      };
      dashboard.alerts.push(alert);

      const stats = getTelemetryStatistics(dashboard);

      expect(stats.totalSamples).toBe(2);
      expect(stats.totalTraces).toBe(1);
      expect(stats.totalAlerts).toBe(1);
      expect(stats.averageFPS).toBe(57.5);
      expect(stats.averageMemoryUsage).toBe(55);
      expect(stats.peakMemoryUsage).toBe(60);
      expect(stats.totalLongTasks).toBe(3);
      expect(stats.averageRenderTime).toBe(18);
      expect(stats.alertsBySeverity.warning).toBe(1);
    });

    it('should handle empty dashboard', () => {
      const stats = getTelemetryStatistics(dashboard);

      expect(stats.totalSamples).toBe(0);
      expect(stats.totalTraces).toBe(0);
      expect(stats.totalAlerts).toBe(0);
      expect(stats.averageFPS).toBe(0);
      expect(stats.averageMemoryUsage).toBe(0);
      expect(stats.peakMemoryUsage).toBe(0);
    });

    it('should group alerts by severity', () => {
      dashboard.alerts = [
        {
          id: '1',
          thresholdId: 'test',
          timestamp: Date.now(),
          severity: 'warning',
          metric: 'fps',
          actualValue: 20,
          thresholdValue: 30,
          message: 'Warning',
          acknowledged: false
        },
        {
          id: '2',
          thresholdId: 'test',
          timestamp: Date.now(),
          severity: 'error',
          metric: 'memory',
          actualValue: 85,
          thresholdValue: 80,
          message: 'Error',
          acknowledged: false
        },
        {
          id: '3',
          thresholdId: 'test',
          timestamp: Date.now(),
          severity: 'warning',
          metric: 'fps',
          actualValue: 25,
          thresholdValue: 30,
          message: 'Warning 2',
          acknowledged: false
        }
      ];

      const stats = getTelemetryStatistics(dashboard);

      expect(stats.alertsBySeverity.warning).toBe(2);
      expect(stats.alertsBySeverity.error).toBe(1);
      expect(stats.alertsBySeverity.critical).toBe(0);
    });
  });

  // ============================================================================
  // Dev Mode & Utilities
  // ============================================================================

  describe('Utilities', () => {
    it('should toggle dev mode', () => {
      expect(dashboard.config.devModeEnabled).toBe(false);
      
      toggleDevMode(dashboard);
      expect(dashboard.config.devModeEnabled).toBe(true);
      
      toggleDevMode(dashboard);
      expect(dashboard.config.devModeEnabled).toBe(false);
    });

    it('should clear all telemetry data', () => {
      dashboard.metrics = [collectMetrics()];
      const trace = startTrace(dashboard, 'test');
      endTrace(dashboard, trace.id);
      dashboard.alerts = [{
        id: '1',
        thresholdId: 'test',
        timestamp: Date.now(),
        severity: 'warning',
        metric: 'fps',
        actualValue: 20,
        thresholdValue: 30,
        message: 'Test',
        acknowledged: false
      }];

      clearTelemetryData(dashboard);

      expect(dashboard.metrics).toEqual([]);
      expect(dashboard.traces).toEqual([]);
      expect(dashboard.alerts).toEqual([]);
      expect(dashboard.activeTrace).toBeUndefined();
    });
  });
});
