/**
 * Dead Letter Queue Service
 *
 * Handles failed jobs with retry logic and dead letter queue management.
 * Provides exponential backoff and maximum retry limits.
 *
 * @doc.type service
 * @doc.purpose Failed job management with retry logic
 * @doc.layer platform
 * @doc.pattern Service
 */

import { Queue, Worker, Job } from 'bullmq';
import { createStandaloneLogger } from '@tutorputor/core/logger';
import type Redis from 'ioredis';

const logger = createStandaloneLogger({ component: 'DeadLetterQueueService' });

export interface DeadLetterQueueOptions {
  maxRetries?: number;
  initialBackoff?: number;
  maxBackoff?: number;
  backoffMultiplier?: number;
  deadLetterTTL?: number; // Time to keep dead jobs in seconds
}

export class DeadLetterQueueService {
  private deadLetterQueue: Queue;
  private options: Required<DeadLetterQueueOptions>;
  private retryWorker: Worker | null = null;

  constructor(
    redis: Redis,
    options: DeadLetterQueueOptions = {}
  ) {
    this.options = {
      maxRetries: options.maxRetries || 3,
      initialBackoff: options.initialBackoff || 1000,
      maxBackoff: options.maxBackoff || 60000,
      backoffMultiplier: options.backoffMultiplier || 2,
      deadLetterTTL: options.deadLetterTTL || 86400, // 24 hours
    };

    this.deadLetterQueue = new Queue('dead-letter', {
      connection: redis,
      defaultJobOptions: {
        removeOnComplete: {
          count: 1000,
          age: 3600, // 1 hour
        },
        removeOnFail: {
          count: 5000,
          age: 86400, // 24 hours
        },
      },
    });
  }

  /**
   * Calculate backoff delay with exponential backoff
   */
  private calculateBackoff(attempt: number): number {
    const delay = Math.min(
      this.options.initialBackoff * Math.pow(this.options.backoffMultiplier, attempt),
      this.options.maxBackoff
    );
    
    // Add some jitter to prevent thundering herd
    const jitter = Math.random() * 0.1 * delay;
    return Math.floor(delay + jitter);
  }

  /**
   * Configure retry options for a job
   */
  getRetryOptions(attemptsMade: number): {
    attempts: number;
    backoff: {
      type: 'exponential';
      delay: number;
    };
  } {
    return {
      attempts: this.options.maxRetries,
      backoff: {
        type: 'exponential' as const,
        delay: this.calculateBackoff(attemptsMade),
      },
    };
  }

  /**
   * Move failed job to dead letter queue
   */
  async moveToDeadLetter(job: Job, error: Error): Promise<void> {
    const deadLetterData = {
      originalJobId: job.id,
      originalQueue: job.queueName,
      name: job.name,
      data: job.data,
      opts: job.opts,
      failedReason: error.message,
      stack: error.stack,
      attemptsMade: job.attemptsMade,
      timestamp: new Date().toISOString(),
    };

    await this.deadLetterQueue.add('dead-letter', deadLetterData, {
      jobId: `dead:${job.queueName}:${job.id}`,
      delay: 0,
    });

    logger.warn({
      message: 'Job moved to dead letter queue',
      originalJobId: job.id,
      originalQueue: job.queueName,
      failedReason: error.message,
    });
  }

  /**
   * Retry a dead letter job
   */
  async retryDeadLetterJob(deadLetterJobId: string, originalQueue: Queue): Promise<void> {
    const deadLetterJob = await this.deadLetterQueue.getJob(deadLetterJobId);
    
    if (!deadLetterJob) {
      throw new Error(`Dead letter job ${deadLetterJobId} not found`);
    }

    const data = deadLetterJob.data as {
      name: string;
      data: unknown;
      opts: Record<string, unknown>;
    };

    // Remove the job from dead letter queue
    await deadLetterJob.remove();

    // Re-add to original queue with fresh retry options
    await originalQueue.add(data.name, data.data, {
      ...data.opts,
      ...this.getRetryOptions(0),
    });

    logger.info({
      message: 'Dead letter job retried',
      deadLetterJobId,
      originalQueue: originalQueue.name,
    });
  }

  /**
   * Get dead letter job count
   */
  async getDeadLetterCount(): Promise<number> {
    return await this.deadLetterQueue.getWaitingCount();
  }

  /**
   * Get dead letter jobs
   */
  async getDeadLetterJobs(start = 0, end = 10): Promise<Job[]> {
    return await this.deadLetterQueue.getJobs(['waiting'], start, end);
  }

  /**
   * Start retry worker for dead letter queue
   */
  async startRetryWorker(originalQueues: Map<string, Queue>): Promise<void> {
    if (this.retryWorker) {
      logger.warn({ message: 'Retry worker already started' });
      return;
    }

    this.retryWorker = new Worker(
      'dead-letter',
      async (job) => {
        const data = job.data as {
          originalQueue: string;
          attemptsMade: number;
        };

        // Check if we've exceeded max retries
        if (data.attemptsMade >= this.options.maxRetries) {
          logger.warn({
            message: 'Job exceeded max retries, keeping in dead letter queue',
            jobId: job.id,
            attemptsMade: data.attemptsMade,
          });
          return;
        }

        // Get original queue
        const originalQueue = originalQueues.get(data.originalQueue);
        
        if (!originalQueue) {
          logger.error({
            message: 'Original queue not found for retry',
            originalQueue: data.originalQueue,
          });
          return;
        }

        // Retry the job
        await this.retryDeadLetterJob(job.id!, originalQueue);
      },
      {
        connection: this.deadLetterQueue.opts.connection as Redis,
        concurrency: 5,
      }
    );

    this.retryWorker.on('completed', (job) => {
      logger.info({ message: 'Dead letter job retry completed', jobId: job.id });
    });

    this.retryWorker.on('failed', (job, error) => {
      logger.error({
        message: 'Dead letter job retry failed',
        jobId: job?.id,
        error: error.message,
      });
    });

    logger.info({ message: 'Dead letter retry worker started' });
  }

  /**
   * Stop retry worker
   */
  async stopRetryWorker(): Promise<void> {
    if (this.retryWorker) {
      await this.retryWorker.close();
      this.retryWorker = null;
      logger.info({ message: 'Dead letter retry worker stopped' });
    }
  }

  /**
   * Clean up old dead letter jobs
   */
  async cleanupOldDeadLetterJobs(maxAgeMs = 86400000): Promise<number> {
    const jobs = await this.getDeadLetterJobs(0, 1000);
    const now = Date.now();
    let cleaned = 0;

    for (const job of jobs) {
      const data = job.data as { timestamp: string };
      const jobTime = new Date(data.timestamp).getTime();
      
      if (now - jobTime > maxAgeMs) {
        await job.remove();
        cleaned++;
      }
    }

    logger.info({ message: 'Old dead letter jobs cleaned', count: cleaned });
    return cleaned;
  }

  /**
   * Close dead letter queue
   */
  async close(): Promise<void> {
    await this.stopRetryWorker();
    await this.deadLetterQueue.close();
    logger.info({ message: 'Dead letter queue service closed' });
  }
}

export function createDeadLetterQueueService(
  redis: Redis,
  options?: DeadLetterQueueOptions
): DeadLetterQueueService {
  return new DeadLetterQueueService(redis, options);
}
