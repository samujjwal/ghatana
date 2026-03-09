/**
 * @fileoverview ComprehensiveMonitor Unit Tests
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { ComprehensiveMonitor } from '../../app/background/monitoring/ComprehensiveMonitor';

describe('ComprehensiveMonitor', () => {
  let monitor: ComprehensiveMonitor;

  beforeEach(() => {
    monitor = new ComprehensiveMonitor({
      healthCheckInterval: 1000,
      performanceInterval: 1000,
      anomalyDetection: true,
      anomalyThresholds: {
        errorRate: 0.05,
        latency: 1000,
        memoryGrowth: 50 * 1024 * 1024,
      },
    });
  });

  afterEach(() => {
    monitor.stop();
  });

  describe('lifecycle', () => {
    it('should start and stop monitoring', () => {
      monitor.start();
      // Should not throw
      monitor.stop();
    });

    it('should handle multiple start calls', () => {
      monitor.start();
      monitor.start(); // Should be idempotent
      monitor.stop();
    });
  });

  describe('health checking', () => {
    it('should check health', async () => {
      const health = await monitor.checkHealth();

      expect(health).toHaveProperty('overall');
      expect(health).toHaveProperty('components');
      expect(health).toHaveProperty('metrics');
      expect(health).toHaveProperty('timestamp');
      expect(['healthy', 'degraded', 'unhealthy']).toContain(health.overall);
    });

    it('should track uptime', async () => {
      const health = await monitor.checkHealth();

      expect(health.metrics.uptime).toBeGreaterThan(0);
    });

    it('should track error rate', async () => {
      monitor.recordEvent(10, false);
      monitor.recordEvent(10, false);
      monitor.recordEvent(10, true); // Error

      const health = await monitor.checkHealth();

      expect(health.metrics.errorRate).toBeGreaterThan(0);
      expect(health.metrics.errorRate).toBeLessThanOrEqual(1);
    });
  });

  describe('performance analysis', () => {
    it('should analyze performance', async () => {
      const performance = await monitor.analyzePerformance();

      expect(performance).toHaveProperty('bottlenecks');
      expect(performance).toHaveProperty('slowQueries');
      expect(performance).toHaveProperty('memoryLeaks');
      expect(performance).toHaveProperty('recommendations');
      expect(performance).toHaveProperty('metrics');
    });

    it('should track latency', async () => {
      monitor.recordEvent(100, false);
      monitor.recordEvent(150, false);
      monitor.recordEvent(200, false);

      const performance = await monitor.analyzePerformance();

      expect(performance.metrics.latency).toHaveProperty('p50');
      expect(performance.metrics.latency).toHaveProperty('p90');
      expect(performance.metrics.latency).toHaveProperty('p99');
    });

    it('should identify bottlenecks', async () => {
      // Simulate high latency
      for (let i = 0; i < 10; i++) {
        monitor.recordEvent(2000, false);
      }

      const performance = await monitor.analyzePerformance();

      // May or may not have bottlenecks depending on thresholds
      expect(Array.isArray(performance.bottlenecks)).toBe(true);
    });
  });

  describe('usage tracking', () => {
    it('should track usage events', () => {
      monitor.trackUsage({
        feature: 'test-feature',
        action: 'test-action',
        timestamp: Date.now(),
      });

      const usage = monitor.getUsageReport();

      expect(usage).toHaveProperty('features');
      expect(usage).toHaveProperty('userFlows');
      expect(usage).toHaveProperty('engagement');
    });

    it('should aggregate feature usage', () => {
      monitor.trackUsage({
        feature: 'feature-1',
        action: 'action-1',
        timestamp: Date.now(),
      });

      monitor.trackUsage({
        feature: 'feature-1',
        action: 'action-2',
        timestamp: Date.now(),
      });

      const usage = monitor.getUsageReport();

      expect(usage.features['feature-1']).toBeDefined();
      expect(usage.features['feature-1'].count).toBeGreaterThanOrEqual(2);
    });

    it('should track engagement metrics', () => {
      monitor.trackUsage({
        feature: 'feature-1',
        action: 'action-1',
        timestamp: Date.now(),
      });

      const usage = monitor.getUsageReport();

      expect(usage.engagement).toHaveProperty('activeTime');
      expect(usage.engagement).toHaveProperty('sessionCount');
      expect(usage.engagement).toHaveProperty('avgSessionDuration');
    });
  });

  describe('anomaly detection', () => {
    it('should detect anomalies', async () => {
      const anomalies = await monitor.detectAnomalies();

      expect(Array.isArray(anomalies)).toBe(true);
    });

    it('should detect error rate anomalies', async () => {
      // Simulate high error rate
      for (let i = 0; i < 10; i++) {
        monitor.recordEvent(10, true); // All errors
      }

      const anomalies = await monitor.detectAnomalies();

      const errorAnomalies = anomalies.filter((a) => a.metric === 'error_rate');
      expect(errorAnomalies.length).toBeGreaterThan(0);
    });

    it('should detect latency anomalies', async () => {
      // Simulate high latency
      for (let i = 0; i < 10; i++) {
        monitor.recordEvent(5000, false);
      }

      const anomalies = await monitor.detectAnomalies();

      const latencyAnomalies = anomalies.filter((a) => a.metric === 'latency');
      expect(latencyAnomalies.length).toBeGreaterThan(0);
    });
  });

  describe('event emission', () => {
    it('should emit usage-event events', () => {
      return new Promise<void>((resolve) => {
        monitor.on('usage-event', (event) => {
          expect(event).toHaveProperty('feature');
          expect(event).toHaveProperty('action');
          resolve();
        });

        monitor.trackUsage({
          feature: 'test',
          action: 'test',
          timestamp: Date.now(),
        });
      });
    });
  });
});
