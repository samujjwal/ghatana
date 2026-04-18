/**
 * Queue Performance Metrics Service
 *
 * Collects and reports queue performance metrics including throughput,
 * latency, and job statistics for BullMQ queues.
 *
 * @doc.type service
 * @doc.purpose Queue performance monitoring and metrics
 * @doc.layer platform
 * @doc.pattern Service
 */

import { Queue } from 'bullmq';
import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'QueueMetricsService' });

export interface QueuePerformanceMetrics {
  name: string;
  throughput: number; // jobs per second
  avgLatency: number; // milliseconds
  p95Latency: number; // milliseconds
  p99Latency: number; // milliseconds
  successRate: number;
  errorRate: number;
  avgProcessingTime: number; // milliseconds
}

export interface JobTiming {
  jobId: string;
  queuedAt: number;
  startedAt: number;
  finishedAt: number;
  processingTime: number;
  waitTime: number;
  totalTime: number;
}

export class QueueMetricsService {
  private queues: Map<string, Queue> = new Map();
  private metrics: Map<string, QueuePerformanceMetrics> = new Map();
  private jobTimings: Map<string, JobTiming[]> = new Map();
  private maxJobTimings = 1000;

  /**
   * Register a queue for metrics collection
   */
  registerQueue(queue: Queue): void {
    this.queues.set(queue.name, queue);
    this.jobTimings.set(queue.name, []);
    
    // Set up event listeners
    queue.on('completed', async (job) => {
      this.recordJobCompletion(job);
    });

    queue.on('failed', async (job) => {
      this.recordJobFailure(job);
    });

    logger.info({ message: 'Queue registered for metrics', queueName: queue.name });
  }

  /**
   * Record job completion timing
   */
  private async recordJobCompletion(job: any): Promise<void> {
    const queueName = job.queueName;
    const timings = this.jobTimings.get(queueName) || [];

    const timing: JobTiming = {
      jobId: job.id,
      queuedAt: job.timestamp,
      startedAt: job.processedOn || Date.now(),
      finishedAt: job.finishedOn || Date.now(),
      processingTime: job.processedOn && job.finishedOn ? job.finishedOn - job.processedOn : 0,
      waitTime: job.processedOn && job.timestamp ? job.processedOn - job.timestamp : 0,
      totalTime: job.finishedOn && job.timestamp ? job.finishedOn - job.timestamp : 0,
    };

    timings.push(timing);

    // Keep only recent timings
    if (timings.length > this.maxJobTimings) {
      timings.shift();
    }

    this.jobTimings.set(queueName, timings);
    await this.calculateMetrics(queueName);
  }

  /**
   * Record job failure
   */
  private async recordJobFailure(job: any): Promise<void> {
    const queueName = job.queueName;
    const timings = this.jobTimings.get(queueName) || [];

    const timing: JobTiming = {
      jobId: job.id,
      queuedAt: job.timestamp,
      startedAt: job.processedOn || Date.now(),
      finishedAt: job.finishedOn || Date.now(),
      processingTime: job.processedOn && job.finishedOn ? job.finishedOn - job.processedOn : 0,
      waitTime: job.processedOn && job.timestamp ? job.processedOn - job.timestamp : 0,
      totalTime: job.finishedOn && job.timestamp ? job.finishedOn - job.timestamp : 0,
    };

    timings.push(timing);

    // Keep only recent timings
    if (timings.length > this.maxJobTimings) {
      timings.shift();
    }

    this.jobTimings.set(queueName, timings);
    await this.calculateMetrics(queueName);
  }

  /**
   * Calculate performance metrics for a queue
   */
  private async calculateMetrics(queueName: string): Promise<void> {
    const queue = this.queues.get(queueName);
    const timings = this.jobTimings.get(queueName) || [];

    if (!queue || timings.length === 0) {
      return;
    }

    const [completed, failed] = await Promise.all([
      queue.getCompletedCount(),
      queue.getFailedCount(),
    ]);

    const totalJobs = completed + failed;
    const successRate = totalJobs > 0 ? completed / totalJobs : 1;
    const errorRate = totalJobs > 0 ? failed / totalJobs : 0;

    // Calculate latency percentiles
    const waitTimes = timings.map(t => t.waitTime).sort((a, b) => a - b);
    const processingTimes = timings.map(t => t.processingTime).sort((a, b) => a - b);
    const totalTimes = timings.map(t => t.totalTime).sort((a, b) => a - b);

    const p95Index = Math.floor(timings.length * 0.95);
    const p99Index = Math.floor(timings.length * 0.99);

    const avgWaitTime = waitTimes.reduce((a, b) => a + b, 0) / waitTimes.length;
    const avgProcessingTime = processingTimes.reduce((a, b) => a + b, 0) / processingTimes.length;
    const avgTotalTime = totalTimes.reduce((a, b) => a + b, 0) / totalTimes.length;

    // Calculate throughput (jobs per second in last minute)
    const oneMinuteAgo = Date.now() - 60000;
    const recentJobs = timings.filter(t => t.finishedAt >= oneMinuteAgo);
    const throughput = recentJobs.length / 60;

    const metrics: QueuePerformanceMetrics = {
      name: queueName,
      throughput,
      avgLatency: avgWaitTime,
      p95Latency: waitTimes[Math.min(p95Index, waitTimes.length - 1)] || 0,
      p99Latency: waitTimes[Math.min(p99Index, waitTimes.length - 1)] || 0,
      successRate,
      errorRate,
      avgProcessingTime,
    };

    this.metrics.set(queueName, metrics);
  }

  /**
   * Get metrics for a specific queue
   */
  getMetrics(queueName: string): QueuePerformanceMetrics | undefined {
    return this.metrics.get(queueName);
  }

  /**
   * Get all metrics
   */
  getAllMetrics(): QueuePerformanceMetrics[] {
    return Array.from(this.metrics.values());
  }

  /**
   * Get job timings for a queue
   */
  getJobTimings(queueName: string): JobTiming[] {
    return this.jobTimings.get(queueName) || [];
  }

  /**
   * Get queue health status
   */
  getHealthStatus(queueName: string): 'healthy' | 'warning' | 'critical' {
    const metrics = this.metrics.get(queueName);
    
    if (!metrics) {
      return 'warning';
    }

    if (metrics.errorRate > 0.1 || metrics.avgLatency > 5000 || metrics.successRate < 0.9) {
      return 'critical';
    }

    if (metrics.errorRate > 0.05 || metrics.avgLatency > 2000 || metrics.successRate < 0.95) {
      return 'warning';
    }

    return 'healthy';
  }

  /**
   * Get overall queue health
   */
  getOverallHealth(): {
    status: 'healthy' | 'warning' | 'critical';
    queues: Array<{ name: string; health: string }>;
  } {
    const queueHealths = Array.from(this.metrics.keys()).map(name => ({
      name,
      health: this.getHealthStatus(name),
    }));

    const hasCritical = queueHealths.some(q => q.health === 'critical');
    const hasWarning = queueHealths.some(q => q.health === 'warning');

    return {
      status: hasCritical ? 'critical' : hasWarning ? 'warning' : 'healthy',
      queues: queueHealths,
    };
  }

  /**
   * Get performance summary
   */
  getPerformanceSummary(): {
    status: string;
    queues: QueuePerformanceMetrics[];
    recommendations: string[];
  } {
    const queues = this.getAllMetrics();
    const status = this.getOverallHealth().status;
    const recommendations: string[] = [];

    for (const queue of queues) {
      if (queue.errorRate > 0.05) {
        recommendations.push(`${queue.name}: High error rate detected - investigate job failures`);
      }

      if (queue.avgLatency > 2000) {
        recommendations.push(`${queue.name}: High latency detected - consider scaling workers`);
      }

      if (queue.successRate < 0.95) {
        recommendations.push(`${queue.name}: Low success rate - review job logic and error handling`);
      }
    }

    return {
      status,
      queues,
      recommendations,
    };
  }

  /**
   * Reset metrics for a queue
   */
  resetMetrics(queueName?: string): void {
    if (queueName) {
      this.metrics.delete(queueName);
      this.jobTimings.set(queueName, []);
    } else {
      this.metrics.clear();
      for (const name of this.jobTimings.keys()) {
        this.jobTimings.set(name, []);
      }
    }
  }
}

export function createQueueMetricsService(): QueueMetricsService {
  return new QueueMetricsService();
}
