/**
 * Database Performance Metrics Service
 *
 * Collects and reports database performance metrics including query times,
 * connection pool usage, and slow query detection.
 *
 * @doc.type service
 * @doc.purpose Database performance monitoring and metrics
 * @doc.layer platform
 * @doc.pattern Service
 */

import { Prisma } from '@tutorputor/core/db';
import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'DatabaseMetricsService' });

export interface DatabaseMetrics {
  queryCount: number;
  totalQueryTime: number;
  avgQueryTime: number;
  slowQueryCount: number;
  connectionPoolUsage: number;
  activeConnections: number;
  idleConnections: number;
  errors: number;
}

export interface SlowQuery {
  query: string;
  duration: number;
  timestamp: Date;
  params?: unknown;
}

export class DatabaseMetricsService {
  private metrics: DatabaseMetrics = {
    queryCount: 0,
    totalQueryTime: 0,
    avgQueryTime: 0,
    slowQueryCount: 0,
    connectionPoolUsage: 0,
    activeConnections: 0,
    idleConnections: 0,
    errors: 0,
  };

  private slowQueries: SlowQuery[] = [];
  private slowQueryThreshold: number;
  private maxSlowQueries: number;

  constructor(slowQueryThreshold = 1000, maxSlowQueries = 100) {
    this.slowQueryThreshold = slowQueryThreshold;
    this.maxSlowQueries = maxSlowQueries;
  }

  /**
   * Create Prisma query logging middleware
   */
  createQueryMiddleware() {
    return Prisma.defineExtension((prisma) => {
      return prisma.$use(async (params, next) => {
        const startTime = Date.now();

        try {
          const result = await next(params);
          const duration = Date.now() - startTime;

          this.recordQuery(params, duration);

          if (duration > this.slowQueryThreshold) {
            this.recordSlowQuery(params, duration);
          }

          return result;
        } catch (error) {
          const duration = Date.now() - startTime;
          this.recordError(params, duration, error);
          throw error;
        }
      });
    });
  }

  /**
   * Record query metrics
   */
  private recordQuery(params: Prisma.MiddlewareParams, duration: number): void {
    this.metrics.queryCount++;
    this.metrics.totalQueryTime += duration;
    this.metrics.avgQueryTime = this.metrics.totalQueryTime / this.metrics.queryCount;
  }

  /**
   * Record slow query
   */
  private recordSlowQuery(params: Prisma.MiddlewareParams, duration: number): void {
    this.metrics.slowQueryCount++;

    const slowQuery: SlowQuery = {
      query: `${params.model}.${params.action}`,
      duration,
      timestamp: new Date(),
      params: params.args,
    };

    this.slowQueries.push(slowQuery);

    // Keep only the most recent slow queries
    if (this.slowQueries.length > this.maxSlowQueries) {
      this.slowQueries.shift();
    }

    logger.warn({
      message: 'Slow query detected',
      model: params.model,
      action: params.action,
      duration,
      threshold: this.slowQueryThreshold,
    });
  }

  /**
   * Record query error
   */
  private recordError(params: Prisma.MiddlewareParams, duration: number, error: unknown): void {
    this.metrics.errors++;

    logger.error({
      message: 'Query error',
      model: params.model,
      action: params.action,
      duration,
      error: error instanceof Error ? error.message : String(error),
    });
  }

  /**
   * Update connection pool metrics
   */
  updateConnectionMetrics(active: number, idle: number, total: number): void {
    this.metrics.activeConnections = active;
    this.metrics.idleConnections = idle;
    this.metrics.connectionPoolUsage = total > 0 ? (active / total) * 100 : 0;
  }

  /**
   * Get current metrics
   */
  getMetrics(): DatabaseMetrics {
    return { ...this.metrics };
  }

  /**
   * Get slow queries
   */
  getSlowQueries(): SlowQuery[] {
    return [...this.slowQueries];
  }

  /**
   * Reset metrics
   */
  resetMetrics(): void {
    this.metrics = {
      queryCount: 0,
      totalQueryTime: 0,
      avgQueryTime: 0,
      slowQueryCount: 0,
      connectionPoolUsage: 0,
      activeConnections: 0,
      idleConnections: 0,
      errors: 0,
    };
    this.slowQueries = [];
  }

  /**
   * Get database health status
   */
  getHealthStatus(): 'healthy' | 'warning' | 'critical' {
    if (this.metrics.avgQueryTime > 500 || this.metrics.slowQueryCount > 50 || this.metrics.errors > 10) {
      return 'critical';
    }

    if (this.metrics.avgQueryTime > 200 || this.metrics.slowQueryCount > 20 || this.metrics.errors > 5) {
      return 'warning';
    }

    return 'healthy';
  }

  /**
   * Get performance summary
   */
  getPerformanceSummary(): {
    status: string;
    metrics: DatabaseMetrics;
    recentSlowQueries: number;
    recommendations: string[];
  } {
    const status = this.getHealthStatus();
    const recommendations: string[] = [];

    if (this.metrics.avgQueryTime > 200) {
      recommendations.push('Consider adding database indexes for frequently queried fields');
    }

    if (this.metrics.slowQueryCount > 20) {
      recommendations.push('Review and optimize slow queries');
    }

    if (this.metrics.connectionPoolUsage > 80) {
      recommendations.push('Consider increasing connection pool size');
    }

    if (this.metrics.errors > 5) {
      recommendations.push('Investigate query errors and optimize error handling');
    }

    return {
      status,
      metrics: this.getMetrics(),
      recentSlowQueries: this.slowQueries.length,
      recommendations,
    };
  }
}

export function createDatabaseMetricsService(
  slowQueryThreshold?: number,
  maxSlowQueries?: number
): DatabaseMetricsService {
  return new DatabaseMetricsService(slowQueryThreshold, maxSlowQueries);
}
