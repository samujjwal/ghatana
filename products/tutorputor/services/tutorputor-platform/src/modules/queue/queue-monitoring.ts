/**
 * Queue Monitoring Service
 *
 * Provides monitoring and metrics for BullMQ queues.
 * Tracks queue health, job statistics, and worker performance.
 *
 * @doc.type service
 * @doc.purpose Queue monitoring and metrics collection
 * @doc.layer platform
 * @doc.pattern Service
 */

import { Queue } from 'bullmq';
import { createStandaloneLogger } from '@tutorputor/core/logger';
import type Redis from 'ioredis';

const logger = createStandaloneLogger({ component: 'QueueMonitoringService' });

export interface QueueMetrics {
  name: string;
  waiting: number;
  active: number;
  completed: number;
  failed: number;
  delayed: number;
  paused: boolean;
  isPaused: boolean;
}

export interface JobMetrics {
  jobId: string;
  name: string;
  state: 'waiting' | 'active' | 'completed' | 'failed' | 'delayed';
  progress: number;
  attemptsMade: number;
  failedReason?: string;
  processedOn?: number;
  finishedOn?: number;
  duration?: number;
}

export class QueueMonitoringService {
  private queues: Map<string, Queue> = new Map();
  private metrics: Map<string, QueueMetrics> = new Map();
  private monitoringInterval: NodeJS.Timeout | null = null;
  private redis: Redis;

  constructor(redis: Redis) {
    this.redis = redis;
  }

  /**
   * Register a queue for monitoring
   */
  registerQueue(queue: Queue): void {
    this.queues.set(queue.name, queue);
    logger.info({ message: 'Queue registered for monitoring', queueName: queue.name });
  }

  /**
   * Start monitoring all registered queues
   */
  startMonitoring(intervalMs = 5000): void {
    if (this.monitoringInterval) {
      logger.warn({ message: 'Queue monitoring already started' });
      return;
    }

    this.monitoringInterval = setInterval(async () => {
      await this.collectMetrics();
    }, intervalMs);

    logger.info({ message: 'Queue monitoring started', intervalMs });
  }

  /**
   * Stop monitoring
   */
  stopMonitoring(): void {
    if (this.monitoringInterval) {
      clearInterval(this.monitoringInterval);
      this.monitoringInterval = null;
      logger.info({ message: 'Queue monitoring stopped' });
    }
  }

  /**
   * Collect metrics from all registered queues
   */
  private async collectMetrics(): Promise<void> {
    for (const [name, queue] of this.queues) {
      try {
        const metrics = await this.getQueueMetrics(queue);
        this.metrics.set(name, metrics);
        
        // Log warnings for unhealthy queues
        if (metrics.waiting > 1000) {
          logger.warn({ message: 'High queue backlog', queue: name, waiting: metrics.waiting });
        }
        
        if (metrics.failed > 100) {
          logger.warn({ message: 'High failure rate', queue: name, failed: metrics.failed });
        }
      } catch (error) {
        logger.error({ message: 'Failed to collect queue metrics', queue: name, error });
      }
    }
  }

  /**
   * Get metrics for a specific queue
   */
  async getQueueMetrics(queue: Queue): Promise<QueueMetrics> {
    const [waiting, active, completed, failed, delayed, isPaused] = await Promise.all([
      queue.getWaitingCount(),
      queue.getActiveCount(),
      queue.getCompletedCount(),
      queue.getFailedCount(),
      queue.getDelayedCount(),
      queue.isPaused(),
    ]);

    return {
      name: queue.name,
      waiting,
      active,
      completed,
      failed,
      delayed,
      paused: isPaused,
      isPaused,
    };
  }

  /**
   * Get current metrics for all queues
   */
  getAllMetrics(): QueueMetrics[] {
    return Array.from(this.metrics.values());
  }

  /**
   * Get metrics for a specific queue by name
   */
  getMetricsByName(queueName: string): QueueMetrics | undefined {
    return this.metrics.get(queueName);
  }

  /**
   * Get job metrics
   */
  async getJobMetrics(queue: Queue, jobId: string): Promise<JobMetrics | null> {
    try {
      const job = await queue.getJob(jobId);
      
      if (!job) {
        return null;
      }

      const state = await job.getState();
      const progress = job.progress;
      const attemptsMade = job.attemptsMade;
      const failedReason = job.failedReason;
      const processedOn = job.processedOn;
      const finishedOn = job.finishedOn;
      
      const duration = processedOn && finishedOn ? finishedOn - processedOn : undefined;

      return {
        jobId: job.id!,
        name: job.name,
        state: state as JobMetrics['state'],
        progress: progress as number,
        attemptsMade,
        ...(failedReason ? { failedReason } : {}),
        ...(processedOn !== undefined ? { processedOn } : {}),
        ...(finishedOn !== undefined ? { finishedOn } : {}),
        ...(duration !== undefined ? { duration } : {}),
      };
    } catch (error) {
      logger.error({ message: 'Failed to get job metrics', queue: queue.name, jobId, error });
      return null;
    }
  }

  /**
   * Get health status
   */
  getQueueHealth(queueName: string): 'healthy' | 'warning' | 'critical' {
    const metrics = this.metrics.get(queueName);
    
    if (!metrics) {
      return 'warning';
    }

    if (metrics.waiting > 1000 || metrics.failed > 100) {
      return 'critical';
    }

    if (metrics.waiting > 500 || metrics.failed > 50) {
      return 'warning';
    }

    return 'healthy';
  }

  /**
   * Get health status (alias for compatibility)
   */
  getHealthStatus(queueName: string): 'healthy' | 'warning' | 'critical' {
    return this.getQueueHealth(queueName);
  }

  /**
   * Get overall system health
   */
  getSystemHealth(): {
    status: 'healthy' | 'warning' | 'critical';
    queues: Array<{ name: string; health: string }>;
  } {
    const queueHealths = Array.from(this.metrics.keys()).map(name => ({
      name,
      health: this.getQueueHealth(name),
    }));

    const hasCritical = queueHealths.some(q => q.health === 'critical');
    const hasWarning = queueHealths.some(q => q.health === 'warning');

    return {
      status: hasCritical ? 'critical' : hasWarning ? 'warning' : 'healthy',
      queues: queueHealths,
    };
  }

  /**
   * Pause a queue
   */
  async pauseQueue(queueName: string): Promise<void> {
    const queue = this.queues.get(queueName);
    
    if (!queue) {
      throw new Error(`Queue ${queueName} not found`);
    }

    await queue.pause();
    logger.info({ message: 'Queue paused', queueName });
  }

  /**
   * Resume a paused queue
   */
  async resumeQueue(queueName: string): Promise<void> {
    const queue = this.queues.get(queueName);
    
    if (!queue) {
      throw new Error(`Queue ${queueName} not found`);
    }

    await queue.resume();
    logger.info({ message: 'Queue resumed', queueName });
  }

  /**
   * Clean up failed jobs
   */
  async cleanFailedJobs(queueName: string, limit = 100): Promise<number> {
    const queue = this.queues.get(queueName);
    
    if (!queue) {
      throw new Error(`Queue ${queueName} not found`);
    }

    await queue.clean(0, 0, 'failed');
    logger.info({ message: 'Failed jobs cleaned', queueName, limit });
    return limit;
  }

  /**
   * Clean up completed jobs
   */
  async cleanCompletedJobs(queueName: string, limit = 1000): Promise<number> {
    const queue = this.queues.get(queueName);
    
    if (!queue) {
      throw new Error(`Queue ${queueName} not found`);
    }

    await queue.clean(0, 0, 'completed');
    logger.info({ message: 'Completed jobs cleaned', queueName, limit });
    return limit;
  }
}

export function createQueueMonitoringService(redis: Redis): QueueMonitoringService {
  return new QueueMonitoringService(redis);
}
