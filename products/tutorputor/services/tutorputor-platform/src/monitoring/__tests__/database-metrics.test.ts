/**
 * Database Metrics Service Tests
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { DatabaseMetricsService } from '../database-metrics';

describe('DatabaseMetricsService', () => {
  let service: DatabaseMetricsService;

  beforeEach(() => {
    service = new DatabaseMetricsService(1000, 100);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('recordQuery', () => {
    it('should record query metrics', () => {
      const params = { model: 'User', action: 'findMany' };
      service['recordQuery'](params, 100);

      expect(service['metrics'].queryCount).toBe(1);
      expect(service['metrics'].totalQueryTime).toBe(100);
      expect(service['metrics'].avgQueryTime).toBe(100);
    });

    it('should calculate average correctly', () => {
      service['recordQuery']({}, 100);
      service['recordQuery']({}, 200);

      expect(service['metrics'].avgQueryTime).toBe(150);
    });
  });

  describe('recordSlowQuery', () => {
    it('should record slow query', () => {
      const params = { model: 'User', action: 'findMany' };
      service['recordSlowQuery'](params, 2000);

      expect(service['metrics'].slowQueryCount).toBe(1);
      expect(service['slowQueries']).toHaveLength(1);
    });

    it('should limit slow queries to max', () => {
      const serviceWithLimit = new DatabaseMetricsService(1000, 5);
      
      for (let i = 0; i < 10; i++) {
        serviceWithLimit['recordSlowQuery']({}, 2000);
      }

      expect(serviceWithLimit['slowQueries']).toHaveLength(5);
    });
  });

  describe('recordError', () => {
    it('should record query error', () => {
      const params = { model: 'User', action: 'findMany' };
      service['recordError'](params, 100, new Error('DB error'));

      expect(service['metrics'].errors).toBe(1);
    });
  });

  describe('getHealthStatus', () => {
    it('should return healthy for good metrics', () => {
      service['metrics'].avgQueryTime = 100;
      service['metrics'].slowQueryCount = 5;
      service['metrics'].errors = 2;

      const status = service.getHealthStatus();
      expect(status).toBe('healthy');
    });

    it('should return critical for bad metrics', () => {
      service['metrics'].avgQueryTime = 600;
      service['metrics'].slowQueryCount = 60;
      service['metrics'].errors = 15;

      const status = service.getHealthStatus();
      expect(status).toBe('critical');
    });
  });

  describe('getPerformanceSummary', () => {
    it('should return performance summary with recommendations', () => {
      service['metrics'].avgQueryTime = 300;
      service['metrics'].slowQueryCount = 30;
      service['metrics'].errors = 10;

      const summary = service.getPerformanceSummary();

      expect(summary.status).toBe('critical');
      expect(summary.recommendations).toContain('Review and optimize slow queries');
      expect(summary.recommendations).toContain('Investigate query errors and optimize error handling');
    });
  });

  describe('resetMetrics', () => {
    it('should reset all metrics', () => {
      service['metrics'].queryCount = 100;
      service['metrics'].slowQueryCount = 10;

      service.resetMetrics();

      expect(service['metrics'].queryCount).toBe(0);
      expect(service['metrics'].slowQueryCount).toBe(0);
    });
  });
});
