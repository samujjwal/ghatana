/**
 * Dead Letter Queue Service Tests
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { DeadLetterQueueService } from '../dead-letter-queue';
import Redis from 'ioredis';

describe('DeadLetterQueueService', () => {
  let service: DeadLetterQueueService;
  let mockRedis: Redis;
  let mockQueue: any;

  beforeEach(() => {
    mockRedis = {} as Redis;
    service = new DeadLetterQueueService(mockRedis, {
      maxRetries: 3,
      initialBackoff: 1000,
      maxBackoff: 60000,
      backoffMultiplier: 2,
    });

    mockQueue = {
      name: 'test-queue',
      add: vi.fn(),
    };
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('calculateBackoff', () => {
    it('should calculate exponential backoff', () => {
      const delay1 = service['calculateBackoff'](0);
      const delay2 = service['calculateBackoff'](1);
      const delay3 = service['calculateBackoff'](2);

      expect(delay1).toBe(1000);
      expect(delay2).toBe(2000);
      expect(delay3).toBe(4000);
    });

    it('should cap backoff at max', () => {
      const delay = service['calculateBackoff'](10);
      expect(delay).toBeLessThanOrEqual(60000);
    });
  });

  describe('getRetryOptions', () => {
    it('should return retry options', () => {
      const options = service.getRetryOptions(0);

      expect(options).toEqual({
        attempts: 3,
        backoff: {
          type: 'exponential',
          delay: 1000,
        },
      });
    });
  });

  describe('moveToDeadLetter', () => {
    it('should move failed job to dead letter queue', async () => {
      const mockJob = {
        id: 'job_123',
        queueName: 'test-queue',
        name: 'test-job',
        data: { test: 'data' },
        opts: {},
        attemptsMade: 3,
        timestamp: Date.now(),
        processedOn: Date.now() - 500,
        finishedOn: Date.now(),
      } as any;

      (service['deadLetterQueue'].add as any).mockResolvedValue({ id: 'dlq_123' });

      await service.moveToDeadLetter(mockJob, new Error('Test error'));

      expect(service['deadLetterQueue'].add).toHaveBeenCalledWith(
        'dead-letter',
        expect.objectContaining({
          originalJobId: 'job_123',
          originalQueue: 'test-queue',
          failedReason: 'Test error',
        }),
        expect.any(Object)
      );
    });
  });

  describe('getDeadLetterCount', () => {
    it('should return dead letter job count', async () => {
      (service['deadLetterQueue'].getWaitingCount as any).mockResolvedValue(10);

      const count = await service.getDeadLetterCount();
      expect(count).toBe(10);
    });
  });

  describe('cleanupOldDeadLetterJobs', () => {
    it('should cleanup old jobs', async () => {
      const oldJob = {
        id: 'old_job',
        data: { timestamp: new Date(Date.now() - 200000).toISOString() },
        remove: vi.fn().mockResolvedValue(undefined),
      } as any;

      const recentJob = {
        id: 'recent_job',
        data: { timestamp: new Date().toISOString() },
        remove: vi.fn().mockResolvedValue(undefined),
      } as any;

      (service['deadLetterQueue'].getJobs as any).mockResolvedValue([oldJob, recentJob]);

      const cleaned = await service.cleanupOldDeadLetterJobs(86400000);

      expect(cleaned).toBe(1);
      expect(oldJob.remove).toHaveBeenCalled();
      expect(recentJob.remove).not.toHaveBeenCalled();
    });
  });
});
