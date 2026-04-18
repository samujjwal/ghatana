/**
 * Queue Metrics Service Tests
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { QueueMetricsService } from '../queue-metrics';
import { Queue } from 'bullmq';

describe('QueueMetricsService', () => {
  let service: QueueMetricsService;
  let mockQueue: Queue;

  beforeEach(() => {
    service = new QueueMetricsService();

    mockQueue = {
      name: 'test-queue',
      getCompletedCount: vi.fn(),
      getFailedCount: vi.fn(),
      on: vi.fn(),
    } as unknown as Queue;
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('registerQueue', () => {
    it('should register a queue for metrics', () => {
      service.registerQueue(mockQueue);

      expect(service['queues'].has('test-queue')).toBe(true);
      expect(service['jobTimings'].has('test-queue')).toBe(true);
    });
  });

  describe('recordJobCompletion', () => {
    it('should record job timing', async () => {
      const mockJob = {
        id: 'job_123',
        queueName: 'test-queue',
        timestamp: Date.now() - 1000,
        processedOn: Date.now() - 500,
        finishedOn: Date.now(),
      };

      service.registerQueue(mockQueue);
      (mockQueue.getCompletedCount as any).mockResolvedValue(100);
      (mockQueue.getFailedCount as any).mockResolvedValue(5);

      await service['recordJobCompletion'](mockJob);

      const timings = service.getJobTimings('test-queue');
      expect(timings).toHaveLength(1);
      expect(timings[0].jobId).toBe('job_123');
    });
  });

  describe('calculateMetrics', () => {
    it('should calculate performance metrics', async () => {
      service.registerQueue(mockQueue);
      
      // Add some job timings
      service['jobTimings'].set('test-queue', [
        { jobId: 'job1', queuedAt: 0, startedAt: 100, finishedAt: 200, processingTime: 100, waitTime: 100, totalTime: 200 },
        { jobId: 'job2', queuedAt: 0, startedAt: 100, finishedAt: 300, processingTime: 200, waitTime: 100, totalTime: 300 },
      ]);

      (mockQueue.getCompletedCount as any).mockResolvedValue(100);
      (mockQueue.getFailedCount as any).mockResolvedValue(10);

      await service['calculateMetrics']('test-queue');

      const metrics = service.getMetrics('test-queue');
      expect(metrics).toBeDefined();
      expect(metrics?.successRate).toBe(0.9090909090909091);
      expect(metrics?.errorRate).toBe(0.09090909090909091);
    });
  });

  describe('getHealthStatus', () => {
    it('should return healthy for good metrics', () => {
      service['metrics'].set('test-queue', {
        name: 'test-queue',
        throughput: 10,
        avgLatency: 100,
        p95Latency: 200,
        p99Latency: 300,
        successRate: 0.99,
        errorRate: 0.01,
        avgProcessingTime: 50,
      });

      const health = service.getHealthStatus('test-queue');
      expect(health).toBe('healthy');
    });

    it('should return critical for high error rate', () => {
      service['metrics'].set('test-queue', {
        name: 'test-queue',
        throughput: 10,
        avgLatency: 100,
        p95Latency: 200,
        p99Latency: 300,
        successRate: 0.85,
        errorRate: 0.15,
        avgProcessingTime: 50,
      });

      const health = service.getHealthStatus('test-queue');
      expect(health).toBe('critical');
    });

    it('should return critical for high latency', () => {
      service['metrics'].set('test-queue', {
        name: 'test-queue',
        throughput: 10,
        avgLatency: 6000,
        p95Latency: 8000,
        p99Latency: 10000,
        successRate: 0.99,
        errorRate: 0.01,
        avgProcessingTime: 50,
      });

      const health = service.getHealthStatus('test-queue');
      expect(health).toBe('critical');
    });
  });

  describe('getOverallHealth', () => {
    it('should return healthy when all queues healthy', () => {
      service['metrics'].set('queue1', {
        name: 'queue1',
        throughput: 10,
        avgLatency: 100,
        p95Latency: 200,
        p99Latency: 300,
        successRate: 0.99,
        errorRate: 0.01,
        avgProcessingTime: 50,
      });

      const health = service.getOverallHealth();
      expect(health.status).toBe('healthy');
    });

    it('should return critical when any queue critical', () => {
      service['metrics'].set('queue1', {
        name: 'queue1',
        throughput: 10,
        avgLatency: 6000,
        p95Latency: 8000,
        p99Latency: 10000,
        successRate: 0.85,
        errorRate: 0.15,
        avgProcessingTime: 50,
      });

      const health = service.getOverallHealth();
      expect(health.status).toBe('critical');
    });
  });

  describe('resetMetrics', () => {
    it('should reset metrics for specific queue', () => {
      service['metrics'].set('test-queue', {
        name: 'test-queue',
        throughput: 10,
        avgLatency: 100,
        p95Latency: 200,
        p99Latency: 300,
        successRate: 0.99,
        errorRate: 0.01,
        avgProcessingTime: 50,
      });

      service.resetMetrics('test-queue');

      expect(service['metrics'].has('test-queue')).toBe(false);
    });
  });
});
