/**
 * Performance Metrics Service for Flashit Web API
 * Collects and tracks performance metrics for monitoring
 *
 * @doc.type service
 * @doc.purpose Performance monitoring and metrics collection
 * @doc.layer product
 * @doc.pattern ObservabilityService
 */

// ============================================================================
// Types & Interfaces
// ============================================================================

export interface RequestMetrics {
  path: string;
  method: string;
  statusCode: number;
  responseTimeMs: number;
  timestamp: Date;
  userId?: string;
  userAgent?: string;
  ip?: string;
}

export interface DatabaseMetrics {
  query: string;
  operation: 'select' | 'insert' | 'update' | 'delete';
  table: string;
  durationMs: number;
  rowCount: number;
  timestamp: Date;
}

export interface CacheMetrics {
  key: string;
  hit: boolean;
  durationMs: number;
  size?: number;
  timestamp: Date;
}

export interface ErrorMetrics {
  error: Error;
  context: Record<string, unknown>;
  path?: string;
  userId?: string;
  timestamp: Date;
  severity: 'low' | 'medium' | 'high' | 'critical';
}

export interface PerformanceSummary {
  requests: {
    total: number;
    avgResponseTimeMs: number;
    p50ResponseTimeMs: number;
    p95ResponseTimeMs: number;
    p99ResponseTimeMs: number;
    errorRate: number;
    byPath: Record<string, { count: number; avgMs: number }>;
  };
  database: {
    totalQueries: number;
    avgDurationMs: number;
    slowQueries: number;
    byTable: Record<string, { count: number; avgMs: number }>;
  };
  cache: {
    hitRate: number;
    totalRequests: number;
    avgDurationMs: number;
  };
  memory: {
    heapUsedMB: number;
    heapTotalMB: number;
    externalMB: number;
    rss: number;
  };
  period: {
    start: Date;
    end: Date;
    durationMinutes: number;
  };
}

export interface PerformanceThresholds {
  requestTimeMs: number;
  databaseTimeMs: number;
  cacheTimeMs: number;
  errorRatePercent: number;
  memoryUsagePercent: number;
}

export interface PerformanceAlert {
  id: string;
  type: 'slow_request' | 'slow_query' | 'high_error_rate' | 'memory_pressure' | 'cache_miss_spike';
  message: string;
  value: number;
  threshold: number;
  timestamp: Date;
  resolved: boolean;
}

// ============================================================================
// Performance Metrics Service
// ============================================================================

/**
 * PerformanceMetricsService collects and analyzes performance data
 */
export class PerformanceMetricsService {
  private static instance: PerformanceMetricsService | null = null;
  
  private requestMetrics: RequestMetrics[] = [];
  private databaseMetrics: DatabaseMetrics[] = [];
  private cacheMetrics: CacheMetrics[] = [];
  private alerts: PerformanceAlert[] = [];
  
  private readonly maxMetricsAge = 60 * 60 * 1000; // 1 hour
  private readonly maxMetricsCount = 10000;
  
  private thresholds: PerformanceThresholds = {
    requestTimeMs: 1000,
    databaseTimeMs: 100,
    cacheTimeMs: 10,
    errorRatePercent: 5,
    memoryUsagePercent: 85,
  };

  private cleanupInterval: NodeJS.Timeout | null = null;

  private constructor() {
    // Start periodic cleanup
    this.cleanupInterval = setInterval(() => this.cleanup(), 60000);
  }

  /**
   * Get singleton instance
   */
  static getInstance(): PerformanceMetricsService {
    if (!this.instance) {
      this.instance = new PerformanceMetricsService();
    }
    return this.instance;
  }

  /**
   * Record a request metric
   */
  recordRequest(metrics: RequestMetrics): void {
    this.requestMetrics.push(metrics);
    
    // Check for slow request alert
    if (metrics.responseTimeMs > this.thresholds.requestTimeMs) {
      this.createAlert({
        type: 'slow_request',
        message: `Slow request: ${metrics.method} ${metrics.path} took ${metrics.responseTimeMs}ms`,
        value: metrics.responseTimeMs,
        threshold: this.thresholds.requestTimeMs,
      });
    }
  }

  /**
   * Record a database metric
   */
  recordDatabaseQuery(metrics: DatabaseMetrics): void {
    this.databaseMetrics.push(metrics);
    
    // Check for slow query alert
    if (metrics.durationMs > this.thresholds.databaseTimeMs) {
      this.createAlert({
        type: 'slow_query',
        message: `Slow query on ${metrics.table}: ${metrics.durationMs}ms`,
        value: metrics.durationMs,
        threshold: this.thresholds.databaseTimeMs,
      });
    }
  }

  /**
   * Record a cache metric
   */
  recordCacheAccess(metrics: CacheMetrics): void {
    this.cacheMetrics.push(metrics);
  }

  /**
   * Record an error
   */
  recordError(metrics: ErrorMetrics): void {
    // Check error rate
    const recentRequests = this.requestMetrics.filter(
      (r) => r.timestamp.getTime() > Date.now() - 5 * 60 * 1000
    );
    const errorCount = recentRequests.filter((r) => r.statusCode >= 500).length;
    const errorRate = (errorCount / Math.max(recentRequests.length, 1)) * 100;

    if (errorRate > this.thresholds.errorRatePercent) {
      this.createAlert({
        type: 'high_error_rate',
        message: `High error rate: ${errorRate.toFixed(2)}%`,
        value: errorRate,
        threshold: this.thresholds.errorRatePercent,
      });
    }
  }

  /**
   * Get performance summary
   */
  getSummary(periodMinutes: number = 60): PerformanceSummary {
    const cutoff = Date.now() - periodMinutes * 60 * 1000;
    
    const requests = this.requestMetrics.filter((r) => r.timestamp.getTime() > cutoff);
    const dbMetrics = this.databaseMetrics.filter((d) => d.timestamp.getTime() > cutoff);
    const cacheHits = this.cacheMetrics.filter((c) => c.timestamp.getTime() > cutoff);

    // Request metrics
    const sortedResponseTimes = requests
      .map((r) => r.responseTimeMs)
      .sort((a, b) => a - b);

    const requestsByPath: Record<string, { count: number; totalMs: number }> = {};
    for (const req of requests) {
      if (!requestsByPath[req.path]) {
        requestsByPath[req.path] = { count: 0, totalMs: 0 };
      }
      requestsByPath[req.path].count++;
      requestsByPath[req.path].totalMs += req.responseTimeMs;
    }

    const byPath: Record<string, { count: number; avgMs: number }> = {};
    for (const [path, data] of Object.entries(requestsByPath)) {
      byPath[path] = { count: data.count, avgMs: Math.round(data.totalMs / data.count) };
    }

    // Database metrics
    const dbByTable: Record<string, { count: number; totalMs: number }> = {};
    for (const db of dbMetrics) {
      if (!dbByTable[db.table]) {
        dbByTable[db.table] = { count: 0, totalMs: 0 };
      }
      dbByTable[db.table].count++;
      dbByTable[db.table].totalMs += db.durationMs;
    }

    const byTable: Record<string, { count: number; avgMs: number }> = {};
    for (const [table, data] of Object.entries(dbByTable)) {
      byTable[table] = { count: data.count, avgMs: Math.round(data.totalMs / data.count) };
    }

    // Memory metrics
    const memUsage = process.memoryUsage();

    return {
      requests: {
        total: requests.length,
        avgResponseTimeMs: this.calculateAverage(sortedResponseTimes),
        p50ResponseTimeMs: this.calculatePercentile(sortedResponseTimes, 50),
        p95ResponseTimeMs: this.calculatePercentile(sortedResponseTimes, 95),
        p99ResponseTimeMs: this.calculatePercentile(sortedResponseTimes, 99),
        errorRate: (requests.filter((r) => r.statusCode >= 400).length / Math.max(requests.length, 1)) * 100,
        byPath,
      },
      database: {
        totalQueries: dbMetrics.length,
        avgDurationMs: this.calculateAverage(dbMetrics.map((d) => d.durationMs)),
        slowQueries: dbMetrics.filter((d) => d.durationMs > this.thresholds.databaseTimeMs).length,
        byTable,
      },
      cache: {
        hitRate: (cacheHits.filter((c) => c.hit).length / Math.max(cacheHits.length, 1)) * 100,
        totalRequests: cacheHits.length,
        avgDurationMs: this.calculateAverage(cacheHits.map((c) => c.durationMs)),
      },
      memory: {
        heapUsedMB: Math.round(memUsage.heapUsed / 1024 / 1024),
        heapTotalMB: Math.round(memUsage.heapTotal / 1024 / 1024),
        externalMB: Math.round(memUsage.external / 1024 / 1024),
        rss: Math.round(memUsage.rss / 1024 / 1024),
      },
      period: {
        start: new Date(cutoff),
        end: new Date(),
        durationMinutes: periodMinutes,
      },
    };
  }

  /**
   * Get active alerts
   */
  getAlerts(includeResolved: boolean = false): PerformanceAlert[] {
    if (includeResolved) {
      return [...this.alerts];
    }
    return this.alerts.filter((a) => !a.resolved);
  }

  /**
   * Resolve an alert
   */
  resolveAlert(alertId: string): void {
    const alert = this.alerts.find((a) => a.id === alertId);
    if (alert) {
      alert.resolved = true;
    }
  }

  /**
   * Update thresholds
   */
  setThresholds(thresholds: Partial<PerformanceThresholds>): void {
    this.thresholds = { ...this.thresholds, ...thresholds };
  }

  /**
   * Get current thresholds
   */
  getThresholds(): PerformanceThresholds {
    return { ...this.thresholds };
  }

  /**
   * Create a performance alert
   */
  private createAlert(params: {
    type: PerformanceAlert['type'];
    message: string;
    value: number;
    threshold: number;
  }): void {
    // Check for recent similar alert to avoid spam
    const recentSimilar = this.alerts.find(
      (a) =>
        a.type === params.type &&
        !a.resolved &&
        a.timestamp.getTime() > Date.now() - 5 * 60 * 1000
    );

    if (recentSimilar) {
      return;
    }

    this.alerts.push({
      id: crypto.randomUUID(),
      ...params,
      timestamp: new Date(),
      resolved: false,
    });
  }

  /**
   * Calculate average of numbers
   */
  private calculateAverage(values: number[]): number {
    if (values.length === 0) return 0;
    return Math.round(values.reduce((a, b) => a + b, 0) / values.length);
  }

  /**
   * Calculate percentile
   */
  private calculatePercentile(sortedValues: number[], percentile: number): number {
    if (sortedValues.length === 0) return 0;
    const index = Math.ceil((percentile / 100) * sortedValues.length) - 1;
    return sortedValues[Math.max(0, index)];
  }

  /**
   * Cleanup old metrics
   */
  private cleanup(): void {
    const cutoff = Date.now() - this.maxMetricsAge;

    this.requestMetrics = this.requestMetrics
      .filter((r) => r.timestamp.getTime() > cutoff)
      .slice(-this.maxMetricsCount);

    this.databaseMetrics = this.databaseMetrics
      .filter((d) => d.timestamp.getTime() > cutoff)
      .slice(-this.maxMetricsCount);

    this.cacheMetrics = this.cacheMetrics
      .filter((c) => c.timestamp.getTime() > cutoff)
      .slice(-this.maxMetricsCount);

    // Keep only last 100 alerts
    this.alerts = this.alerts.slice(-100);

    // Check memory pressure
    const memUsage = process.memoryUsage();
    const usagePercent = (memUsage.heapUsed / memUsage.heapTotal) * 100;

    if (usagePercent > this.thresholds.memoryUsagePercent) {
      this.createAlert({
        type: 'memory_pressure',
        message: `High memory usage: ${usagePercent.toFixed(1)}%`,
        value: usagePercent,
        threshold: this.thresholds.memoryUsagePercent,
      });
    }
  }

  /**
   * Shutdown the service
   */
  shutdown(): void {
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
    }
  }

  /**
   * Reset metrics (for testing)
   */
  reset(): void {
    this.requestMetrics = [];
    this.databaseMetrics = [];
    this.cacheMetrics = [];
    this.alerts = [];
  }
}

/**
 * Get the performance metrics service instance
 */
export function getPerformanceMetrics(): PerformanceMetricsService {
  return PerformanceMetricsService.getInstance();
}

export default PerformanceMetricsService;
