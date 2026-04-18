/**
 * AI Health Check Service
 *
 * Monitors AI service availability, performance, and quality.
 * Provides health endpoints and metrics for AI integration.
 *
 * @doc.type class
 * @doc.purpose AI service health monitoring and metrics
 * @doc.layer platform
 * @doc.pattern Service
 */

import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'AIHealthCheckService' });

interface AIHealthStatus {
  service: string;
  healthy: boolean;
  latency: number;
  error?: string | undefined;
  lastCheck: Date;
}

interface AIMetrics {
  totalRequests: number;
  successfulRequests: number;
  failedRequests: number;
  averageLatency: number;
  p95Latency: number;
  p99Latency: number;
}

export class AIHealthCheckService {
  private healthStatus: Map<string, AIHealthStatus> = new Map();
  private metrics: Map<string, AIMetrics> = new Map();
  private latencyHistory: Map<string, number[]> = new Map();

  /**
   * Record AI service health check
   */
  recordHealthCheck(service: string, healthy: boolean, latency: number, error?: string): void {
    this.healthStatus.set(service, {
      service,
      healthy,
      latency,
      error,
      lastCheck: new Date(),
    });

    if (!healthy) {
      logger.warn({
        message: `AI service health check failed`,
        service,
        latency,
        error,
      });
    }
  }

  /**
   * Record AI request metrics
   */
  recordRequest(service: string, success: boolean, latency: number): void {
    let metrics = this.metrics.get(service) || {
      totalRequests: 0,
      successfulRequests: 0,
      failedRequests: 0,
      averageLatency: 0,
      p95Latency: 0,
      p99Latency: 0,
    };

    metrics.totalRequests++;
    if (success) {
      metrics.successfulRequests++;
    } else {
      metrics.failedRequests++;
    }

    // Update latency history
    let history = this.latencyHistory.get(service) || [];
    history.push(latency);
    if (history.length > 1000) {
      history.shift();
    }
    this.latencyHistory.set(service, history);

    // Calculate percentiles
    const sorted = [...history].sort((a, b) => a - b);
    metrics.averageLatency = history.reduce((a, b) => a + b, 0) / history.length;
    metrics.p95Latency = sorted[Math.floor(sorted.length * 0.95)] || 0;
    metrics.p99Latency = sorted[Math.floor(sorted.length * 0.99)] || 0;

    this.metrics.set(service, metrics);
  }

  /**
   * Get current health status for all AI services
   */
  getHealthStatus(): AIHealthStatus[] {
    return Array.from(this.healthStatus.values());
  }

  /**
   * Get metrics for a specific AI service
   */
  getMetrics(service: string): AIMetrics | undefined {
    return this.metrics.get(service);
  }

  /**
   * Get overall AI service health
   */
  isHealthy(service: string): boolean {
    const status = this.healthStatus.get(service);
    if (!status) return false;

    // Service is healthy if:
    // - Last check was successful
    // - Latency is under 5 seconds
    // - Last check was within the last 5 minutes
    const isRecent = (Date.now() - status.lastCheck.getTime()) < 5 * 60 * 1000;
    const isLatencyOk = status.latency < 5000;

    return status.healthy && isRecent && isLatencyOk;
  }

  /**
   * Get error rate for a service
   */
  getErrorRate(service: string): number {
    const metrics = this.metrics.get(service);
    if (!metrics || metrics.totalRequests === 0) return 0;

    return metrics.failedRequests / metrics.totalRequests;
  }
}
