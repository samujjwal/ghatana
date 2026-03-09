/**
 * Performance Monitoring Utilities
 * Week 14 Day 67 - Performance metrics and query optimization
 * 
 * @doc.type library
 * @doc.purpose Track performance metrics, slow queries, and resource usage
 * @doc.layer product
 * @doc.pattern Monitoring
 */

import { prisma } from '../lib/prisma.js';

// ============================================================================
// Performance Metrics
// ============================================================================

export interface PerformanceMetric {
  operation: string;
  duration: number;
  timestamp: Date;
  metadata?: Record<string, any>;
}

class PerformanceMonitor {
  private metrics: PerformanceMetric[] = [];
  private readonly maxMetrics = 1000; // Keep last 1000 metrics in memory

  /**
   * Track operation performance
   */
  async track<T>(operation: string, fn: () => Promise<T>, metadata?: Record<string, any>): Promise<T> {
    const start = Date.now();
    try {
      const result = await fn();
      const duration = Date.now() - start;

      this.addMetric({
        operation,
        duration,
        timestamp: new Date(),
        metadata,
      });

      // Log slow operations
      if (duration > 1000) {
        fastify?.log.warn({
          msg: 'Slow operation detected',
          operation,
          duration,
          metadata,
        });
      }

      return result;
    } catch (error) {
      const duration = Date.now() - start;
      this.addMetric({
        operation: `${operation}:error`,
        duration,
        timestamp: new Date(),
        metadata: { ...metadata, error: (error as Error).message },
      });
      throw error;
    }
  }

  /**
   * Add metric to in-memory store
   */
  private addMetric(metric: PerformanceMetric): void {
    this.metrics.push(metric);
    
    // Keep only last N metrics
    if (this.metrics.length > this.maxMetrics) {
      this.metrics.shift();
    }
  }

  /**
   * Get metrics summary
   */
  getSummary(operation?: string): {
    count: number;
    avgDuration: number;
    minDuration: number;
    maxDuration: number;
    p95Duration: number;
    p99Duration: number;
  } {
    const filtered = operation
      ? this.metrics.filter((m) => m.operation === operation)
      : this.metrics;

    if (filtered.length === 0) {
      return {
        count: 0,
        avgDuration: 0,
        minDuration: 0,
        maxDuration: 0,
        p95Duration: 0,
        p99Duration: 0,
      };
    }

    const durations = filtered.map((m) => m.duration).sort((a, b) => a - b);
    const sum = durations.reduce((a, b) => a + b, 0);

    return {
      count: filtered.length,
      avgDuration: Math.round(sum / durations.length),
      minDuration: durations[0],
      maxDuration: durations[durations.length - 1],
      p95Duration: durations[Math.floor(durations.length * 0.95)],
      p99Duration: durations[Math.floor(durations.length * 0.99)],
    };
  }

  /**
   * Get all operations with their summaries
   */
  getAllSummaries(): Record<string, ReturnType<typeof this.getSummary>> {
    const operations = new Set(this.metrics.map((m) => m.operation));
    const summaries: Record<string, ReturnType<typeof this.getSummary>> = {};

    for (const operation of operations) {
      summaries[operation] = this.getSummary(operation);
    }

    return summaries;
  }

  /**
   * Get slow operations (> threshold ms)
   */
  getSlowOperations(thresholdMs: number = 1000): PerformanceMetric[] {
    return this.metrics
      .filter((m) => m.duration > thresholdMs)
      .sort((a, b) => b.duration - a.duration)
      .slice(0, 20); // Top 20 slowest
  }

  /**
   * Clear all metrics
   */
  clear(): void {
    this.metrics = [];
  }
}

export const performanceMonitor = new PerformanceMonitor();

// ============================================================================
// Database Query Optimization
// ============================================================================

/**
 * Add database query logging middleware to Prisma
 * Note: Prisma middleware ($use) is deprecated in newer versions
 * TODO: Migrate to Prisma Client Extensions in Phase 2
 */
export function enableQueryLogging() {
  // Temporarily disabled - Prisma $use is deprecated
  // Use Prisma Client Extensions instead
  console.log('Query logging middleware disabled (Prisma $use deprecated)');
  
  /*
  // Prisma middleware to log slow queries
  prisma.$use(async (params, next) => {
    const start = Date.now();
    const result = await next(params);
    const duration = Date.now() - start;

    // Log slow queries
    if (duration > 500) {
      console.warn({
        msg: 'Slow database query',
        model: params.model,
        action: params.action,
        duration,
        args: params.args,
      });
    }

    // Track in performance monitor
    performanceMonitor.track(
      `db:${params.model}:${params.action}`,
      async () => result,
      { duration }
    );

    return result;
  });
  */
}

// ============================================================================
// Resource Usage Monitoring
// ============================================================================

export interface ResourceUsage {
  memory: {
    heapUsed: number;
    heapTotal: number;
    external: number;
    rss: number;
  };
  cpu: {
    user: number;
    system: number;
  };
  uptime: number;
}

/**
 * Get current resource usage
 */
export function getResourceUsage(): ResourceUsage {
  const memUsage = process.memoryUsage();
  const cpuUsage = process.cpuUsage();

  return {
    memory: {
      heapUsed: Math.round(memUsage.heapUsed / 1024 / 1024), // MB
      heapTotal: Math.round(memUsage.heapTotal / 1024 / 1024),
      external: Math.round(memUsage.external / 1024 / 1024),
      rss: Math.round(memUsage.rss / 1024 / 1024),
    },
    cpu: {
      user: Math.round(cpuUsage.user / 1000), // ms
      system: Math.round(cpuUsage.system / 1000),
    },
    uptime: Math.round(process.uptime()),
  };
}

// ============================================================================
// Database Connection Pool Monitoring
// ============================================================================

/**
 * Get database connection pool stats
 */
export async function getDatabaseStats() {
  try {
    // Get active connections
    const activeConnections = await prisma.$queryRaw<any[]>`
      SELECT count(*) as count
      FROM pg_stat_activity
      WHERE state = 'active'
    `;

    // Get database size
    const dbSize = await prisma.$queryRaw<any[]>`
      SELECT pg_database_size(current_database()) as size
    `;

    // Get table stats
    const tableStats = await prisma.$queryRaw<any[]>`
      SELECT
        schemaname,
        tablename,
        pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size,
        n_live_tup as row_count
      FROM pg_stat_user_tables
      ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC
      LIMIT 10
    `;

    // Get slow queries (if pg_stat_statements extension is enabled)
    let slowQueries: any[] = [];
    try {
      slowQueries = await prisma.$queryRaw<any[]>`
        SELECT
          query,
          calls,
          total_exec_time / 1000 as total_time_seconds,
          mean_exec_time / 1000 as mean_time_seconds
        FROM pg_stat_statements
        ORDER BY mean_exec_time DESC
        LIMIT 10
      `;
    } catch {
      // pg_stat_statements not enabled, skip
    }

    return {
      activeConnections: activeConnections[0]?.count || 0,
      databaseSize: dbSize[0]?.size || 0,
      tableStats,
      slowQueries,
    };
  } catch (error) {
    fastify?.log.error({
      msg: 'Failed to get database stats',
      error: (error as Error).message,
    });
    return null;
  }
}

// ============================================================================
// API Endpoint Performance Tracking
// ============================================================================

/**
 * Fastify hook to track request performance
 */
export function setupRequestTracking(app: any) {
  app.addHook('onRequest', async (request: any, reply: any) => {
    request.startTime = Date.now();
  });

  app.addHook('onResponse', async (request: any, reply: any) => {
    const duration = Date.now() - (request.startTime || Date.now());
    const route = request.routeOptions?.url || request.url;

    performanceMonitor.track(
      `api:${request.method}:${route}`,
      async () => null,
      {
        statusCode: reply.statusCode,
        duration,
        method: request.method,
      }
    );

    // Log slow API calls
    if (duration > 2000) {
      fastify?.log.warn({
        msg: 'Slow API call',
        method: request.method,
        url: request.url,
        duration,
        statusCode: reply.statusCode,
      });
    }
  });
}

// ============================================================================
// Memory Leak Detection
// ============================================================================

let baselineHeap = 0;

/**
 * Check for potential memory leaks
 */
export function checkMemoryLeak(): {
  isLeaking: boolean;
  heapGrowth: number;
  recommendation: string;
} {
  const currentHeap = process.memoryUsage().heapUsed;

  if (baselineHeap === 0) {
    baselineHeap = currentHeap;
    return {
      isLeaking: false,
      heapGrowth: 0,
      recommendation: 'Baseline set, monitor over time',
    };
  }

  const heapGrowth = currentHeap - baselineHeap;
  const growthPercent = (heapGrowth / baselineHeap) * 100;

  // Flag if heap grew by more than 50%
  const isLeaking = growthPercent > 50;

  return {
    isLeaking,
    heapGrowth: Math.round(heapGrowth / 1024 / 1024), // MB
    recommendation: isLeaking
      ? 'Potential memory leak detected. Check for unreleased resources.'
      : 'Memory usage normal',
  };
}

// Run memory leak check every 5 minutes
setInterval(() => {
  const check = checkMemoryLeak();
  if (check.isLeaking) {
    fastify?.log.error({
      msg: 'Memory leak detected',
      heapGrowthMB: check.heapGrowth,
      recommendation: check.recommendation,
    });
  }
}, 5 * 60 * 1000);
