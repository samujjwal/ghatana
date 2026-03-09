/**
 * Dead Letter Queue Configuration and Management
 * 
 * Configures BullMQ for dead-letter queue handling of failed jobs.
 * 
 * @doc.type utility
 * @doc.purpose Handle failed jobs via dead-letter queue
 * @doc.layer infrastructure
 */

import { Queue, Worker, Job, FlowProducer } from 'bullmq';
import type { Logger } from 'pino';

export interface DeadLetterQueueConfig {
  name: string;
  redis: {
    host: string;
    port: number;
    password?: string;
    db?: number;
  };
  maxRetries?: number;
  backoffDelayMs?: number;
  backoffType?: 'fixed' | 'exponential';
}

export interface DeadLetterJob {
  originalJobId: string;
  jobName: string;
  data: any;
  failedReason: string;
  failedAt: Date;
  attemptCount: number;
  stackTrace?: string;
}

export class DeadLetterQueueManager {
  private dlq: Queue;
  private logger: Logger;
  private config: DeadLetterQueueConfig;

  constructor(config: DeadLetterQueueConfig, logger: Logger) {
    this.config = {
      maxRetries: 3,
      backoffDelayMs: 5000,
      backoffType: 'exponential',
      ...config,
    };
    this.logger = logger;

    this.dlq = new Queue(config.name, {
      connection: config.redis,
      defaultJobOptions: {
        removeOnComplete: false, // Keep DLQ jobs for analysis
        removeOnFail: false,
      },
    });
  }

  /**
   * Move a failed job to the dead letter queue.
   */
  async moveToDLQ(job: Job, failedReason: string): Promise<void> {
    const dlqJob: DeadLetterJob = {
      originalJobId: job.id || 'unknown',
      jobName: job.name,
      data: job.data,
      failedReason,
      failedAt: new Date(),
      attemptCount: job.attemptsMade,
      stackTrace: job.stacktrace?.join('\n'),
    };

    await this.dlq.add('failed-job', dlqJob, {
      jobId: `dlq-${job.id}`,
    });

    this.logger.error(
      { 
        jobId: job.id, 
        jobName: job.name,
        failedReason,
        attemptCount: job.attemptsMade,
      },
      'Job moved to dead letter queue'
    );
  }

  /**
   * Get all jobs in the dead letter queue.
   */
  async getFailedJobs(
    start: number = 0,
    end: number = 100
  ): Promise<DeadLetterJob[]> {
    const jobs = await this.dlq.getJobs(['completed'], start, end, true);
    
    return jobs.map(job => job.data as DeadLetterJob);
  }

  /**
   * Retry a job from the dead letter queue.
   */
  async retryJob(
    targetQueue: Queue,
    originalJobId: string,
    dlqJobData: DeadLetterJob
  ): Promise<Job | null> {
    try {
      const newJob = await targetQueue.add(
        dlqJobData.jobName,
        dlqJobData.data,
        {
          jobId: `retry-${originalJobId}-${Date.now()}`,
        }
      );

      // Remove from DLQ after successful retry
      const dlqJob = await this.dlq.getJob(`dlq-${originalJobId}`);
      if (dlqJob) {
        await dlqJob.remove();
      }

      this.logger.info(
        { 
          originalJobId, 
          newJobId: newJob.id,
          jobName: dlqJobData.jobName,
        },
        'Job retried from dead letter queue'
      );

      return newJob;
    } catch (error) {
      this.logger.error(
        { originalJobId, error },
        'Failed to retry job from dead letter queue'
      );
      return null;
    }
  }

  /**
   * Configure a worker with automatic DLQ handling for failed jobs.
   */
  configureWorkerWithDLQ<T = any>(
    queueName: string,
    processor: (job: Job<T>) => Promise<any>,
    redis: DeadLetterQueueConfig['redis']
  ): Worker {
    const worker = new Worker(queueName, async (job) => {
      try {
        return await processor(job);
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : String(error);
        
        // Check if we've exceeded max retries
        if (job.attemptsMade >= (this.config.maxRetries || 3)) {
          await this.moveToDLQ(job, errorMessage);
        }
        
        throw error; // Re-throw for BullMQ to handle retry
      }
    }, {
      connection: redis,
      concurrency: 5,
    });

    // Listen for failed events as backup
    worker.on('failed', async (job, failedReason) => {
      if (job && job.attemptsMade >= (this.config.maxRetries || 3)) {
        await this.moveToDLQ(job, failedReason);
      }
    });

    return worker;
  }

  /**
   * Clean up old DLQ entries.
   */
  async cleanupOldEntries(olderThanDays: number = 30): Promise<number> {
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - olderThanDays);

    const oldJobs = await this.dlq.getJobs(['completed'], 0, -1, true);
    let removedCount = 0;

    for (const job of oldJobs) {
      const data = job.data as DeadLetterJob;
      if (new Date(data.failedAt) < cutoffDate) {
        await job.remove();
        removedCount++;
      }
    }

    this.logger.info(
      { removedCount, olderThanDays },
      'Cleaned up old dead letter queue entries'
    );

    return removedCount;
  }

  async close(): Promise<void> {
    await this.dlq.close();
  }
}

/**
 * Create queue options with DLQ configuration.
 */
export function createQueueOptionsWithDLQ(
  maxRetries: number = 3,
  backoffMs: number = 5000
) {
  return {
    attempts: maxRetries,
    backoff: {
      type: 'exponential',
      delay: backoffMs,
    },
    removeOnComplete: {
      count: 100, // Keep last 100 completed jobs
    },
    removeOnFail: {
      count: 50, // Keep last 50 failed jobs before DLQ
    },
  };
}
