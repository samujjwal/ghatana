/**
 * AI Health Check Service Tests
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { AIHealthCheckService } from '../AIHealthCheckService.js';

describe('AIHealthCheckService', () => {
  let service: AIHealthCheckService;

  beforeEach(() => {
    service = new AIHealthCheckService();
  });

  describe('recordHealthCheck', () => {
    it('should record successful health check', () => {
      service.recordHealthCheck('ollama', true, 100);
      const status = service.getHealthStatus();
      expect(status).toHaveLength(1);
      expect(status[0].service).toBe('ollama');
      expect(status[0].healthy).toBe(true);
      expect(status[0].latency).toBe(100);
    });

    it('should record failed health check with error', () => {
      service.recordHealthCheck('ollama', false, 5000, 'Connection timeout');
      const status = service.getHealthStatus();
      expect(status[0].healthy).toBe(false);
      expect(status[0].error).toBe('Connection timeout');
    });
  });

  describe('recordRequest', () => {
    it('should record successful request', () => {
      service.recordRequest('ollama', true, 150);
      const metrics = service.getMetrics('ollama');
      expect(metrics).toBeDefined();
      expect(metrics?.totalRequests).toBe(1);
      expect(metrics?.successfulRequests).toBe(1);
      expect(metrics?.failedRequests).toBe(0);
    });

    it('should record failed request', () => {
      service.recordRequest('ollama', false, 200);
      const metrics = service.getMetrics('ollama');
      expect(metrics?.failedRequests).toBe(1);
    });

    it('should calculate average latency', () => {
      service.recordRequest('ollama', true, 100);
      service.recordRequest('ollama', true, 200);
      const metrics = service.getMetrics('ollama');
      expect(metrics?.averageLatency).toBe(150);
    });
  });

  describe('isHealthy', () => {
    it('should return true for healthy recent check', () => {
      service.recordHealthCheck('ollama', true, 100);
      expect(service.isHealthy('ollama')).toBe(true);
    });

    it('should return false for failed check', () => {
      service.recordHealthCheck('ollama', false, 5000);
      expect(service.isHealthy('ollama')).toBe(false);
    });

    it('should return false for stale check', () => {
      service.recordHealthCheck('ollama', true, 100);
      // Simulate stale check by modifying timestamp
      const status = service.getHealthStatus();
      status[0].lastCheck = new Date(Date.now() - 10 * 60 * 1000);
      expect(service.isHealthy('ollama')).toBe(false);
    });

    it('should return false for high latency', () => {
      service.recordHealthCheck('ollama', true, 6000);
      expect(service.isHealthy('ollama')).toBe(false);
    });
  });

  describe('getErrorRate', () => {
    it('should calculate error rate', () => {
      service.recordRequest('ollama', true, 100);
      service.recordRequest('ollama', true, 100);
      service.recordRequest('ollama', false, 200);
      const errorRate = service.getErrorRate('ollama');
      expect(errorRate).toBeCloseTo(0.333, 2);
    });

    it('should return 0 for no requests', () => {
      const errorRate = service.getErrorRate('ollama');
      expect(errorRate).toBe(0);
    });
  });
});
