/**
 * Queue Monitoring Service Tests
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { QueueMonitoringService } from '../queue-monitoring';
import { Queue } from 'bullmq';
import Redis from 'ioredis';

describe('QueueMonitoringService', () => {
  let service: QueueMonitoringService;
  let mockRedis: Redis;
  let mockQueue: Queue;

  beforeEach(() => {
    mockRedis = {} as Redis;
    service = new QueueMonitoringService(mockRedis);

    mockQueue = {
      name: 'test-queue',
      getWaitingCount: vi.fn(),
      getActiveCount: vi.fn(),
      getCompletedCount: vi.fn(),
      getFailedCount: vi.fn(),
      getDelayedCount: vi.fn(),
      isPaused: vi.fn(),
      on: vi.fn(),
    } as unknown as Queue;

    service.registerQueue(mockQueue);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('registerQueue', () => {
    it('should register a queue for monitoring', () => {
      expect(service['queues'].has('test-queue')).toBe(true);
    });
  });

  describe('getQueueMetrics', () => {
    it('should return queue metrics', async () => {
      (mockQueue.getWaitingCount as any).mockResolvedValue(10);
      (mockQueue.getActiveCount as any).mockResolvedValue(5);
      (mockQueue.getCompletedCount as any).mockResolvedValue(100);
      (mockQueue.getFailedCount as any).mockResolvedValue(2);
      (mockQueue.getDelayedCount as any).mockResolvedValue(3);
      (mockQueue.isPaused as any).mockResolvedValue(false);

      const metrics = await service.getQueueMetrics(mockQueue);

      expect(metrics).toEqual({
        name: 'test-queue',
        waiting: 10,
        active: 5,
        completed: 100,
        failed: 2,
        delayed: 3,
        paused: false,
        isPaused: false,
      });
    });
  });

  describe('getHealthStatus', () => {
    it('should return healthy status for good metrics', () => {
      service['metrics'].set('test-queue', {
        name: 'test-queue',
        waiting: 100,
        active: 5,
        completed: 1000,
        failed: 10,
        delayed: 0,
        paused: false,
        isPaused: false,
      });

      const health = service.getHealthStatus('test-queue');
      expect(health).toBe('healthy');
    });

    it('should return critical status for high failures', () => {
      service['metrics'].set('test-queue', {
        name: 'test-queue',
        waiting: 100,
        active: 5,
        completed: 1000,
        failed: 150,
        delayed: 0,
        paused: false,
        isPaused: false,
      });

      const health = service.getHealthStatus('test-queue');
      expect(health).toBe('critical');
    });
  });

  describe('pauseQueue', () => {
    it('should pause a queue', async () => {
      (mockQueue.pause as any).mockResolvedValue(undefined);

      await service.pauseQueue('test-queue');

      expect(mockQueue.pause).toHaveBeenCalled();
    });

    it('should throw error for unknown queue', async () => {
      await expect(service.pauseQueue('unknown')).rejects.toThrow('Queue unknown not found');
    });
  });
});
