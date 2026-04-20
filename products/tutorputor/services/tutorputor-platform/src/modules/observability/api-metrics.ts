/**
 * API Latency Metrics Service
 *
 * Tracks API performance including:
 * - Request/response latency
 * - Endpoint-level metrics
 * - Error rate tracking
 * - Throughput measurement
 * - P99/P95/P50 latencies
 *
 * @doc.type service
 * @doc.purpose Monitor API endpoint performance and health
 * @doc.layer product
 * @doc.pattern Service
 */

export interface APIMetricsConfig {
  slowRequestThresholdMs: number;
  maxMetricsHistory: number;
  flushIntervalMs: number;
  enableDetailedTracing: boolean;
}

export interface RequestMetrics {
  endpoint: string;
  method: string;
  statusCode: number;
  latencyMs: number;
  timestamp: Date;
  tenantId?: string;
  error?: string;
}

export interface EndpointMetrics {
  endpoint: string;
  method: string;
  requestCount: number;
  errorCount: number;
  avgLatencyMs: number;
  p50LatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  minLatencyMs: number;
  maxLatencyMs: number;
  lastUpdated: Date;
}

export interface MetricsSnapshot {
  timestamp: Date;
  totalRequests: number;
  totalErrors: number;
  errorRate: number;
  avgLatencyMs: number;
  endpoints: EndpointMetrics[];
  slowRequests: RequestMetrics[];
}

export const DEFAULT_METRICS_CONFIG: APIMetricsConfig = {
  slowRequestThresholdMs: 1000,
  maxMetricsHistory: 10000,
  flushIntervalMs: 60000,
  enableDetailedTracing: false,
};

export class APIMetricsService {
  private config: APIMetricsConfig;
  private requestMetrics: RequestMetrics[] = [];
  private endpointStats: Map<string, EndpointMetrics> = new Map();
  private flushInterval?: NodeJS.Timeout;

  constructor(config?: Partial<APIMetricsConfig>) {
    this.config = { ...DEFAULT_METRICS_CONFIG, ...config };
    this.startFlushInterval();
  }

  /**
   * Record a request metric
   */
  recordRequest(metric: Omit<RequestMetrics, "timestamp">): void {
    const fullMetric: RequestMetrics = {
      ...metric,
      timestamp: new Date(),
    };

    this.requestMetrics.push(fullMetric);

    // Update endpoint statistics
    this.updateEndpointStats(fullMetric);

    // Check for slow request
    if (metric.latencyMs > this.config.slowRequestThresholdMs) {
      this.handleSlowRequest(fullMetric);
    }

    // Trim history if needed
    if (this.requestMetrics.length > this.config.maxMetricsHistory) {
      this.requestMetrics = this.requestMetrics.slice(-this.config.maxMetricsHistory);
    }
  }

  /**
   * Get current metrics snapshot
   */
  getMetricsSnapshot(): MetricsSnapshot {
    const now = new Date();
    const recentMetrics = this.getRecentMetrics(300000); // Last 5 minutes

    const totalRequests = recentMetrics.length;
    const errorRequests = recentMetrics.filter((m) => m.statusCode >= 400).length;

    return {
      timestamp: now,
      totalRequests,
      totalErrors: errorRequests,
      errorRate: totalRequests > 0 ? errorRequests / totalRequests : 0,
      avgLatencyMs:
        totalRequests > 0
          ? recentMetrics.reduce((sum, m) => sum + m.latencyMs, 0) / totalRequests
          : 0,
      endpoints: Array.from(this.endpointStats.values()),
      slowRequests: recentMetrics.filter(
        (m) => m.latencyMs > this.config.slowRequestThresholdMs
      ),
    };
  }

  /**
   * Get metrics for specific endpoint
   */
  getEndpointMetrics(endpoint: string, method?: string): EndpointMetrics | null {
    const key = this.getEndpointKey(endpoint, method ?? "GET");
    return this.endpointStats.get(key) ?? null;
  }

  /**
   * Get top slowest endpoints
   */
  getSlowestEndpoints(limit: number = 10): EndpointMetrics[] {
    return Array.from(this.endpointStats.values())
      .sort((a, b) => b.p99LatencyMs - a.p99LatencyMs)
      .slice(0, limit);
  }

  /**
   * Get error-prone endpoints
   */
  getErrorProneEndpoints(limit: number = 10): EndpointMetrics[] {
    return Array.from(this.endpointStats.values())
      .filter((e) => e.requestCount > 0)
      .map((e) => ({
        ...e,
        errorRate: e.errorCount / e.requestCount,
      }))
      .sort((a, b) => (b as EndpointMetrics & { errorRate: number }).errorRate - (a as EndpointMetrics & { errorRate: number }).errorRate)
      .slice(0, limit) as EndpointMetrics[];
  }

  /**
   * Create Express middleware for automatic metrics collection
   */
  createMiddleware() {
    return (req: Request, res: Response, next: () => void) => {
      const startTime = performance.now();

      res.on("finish", () => {
        const latencyMs = performance.now() - startTime;

        this.recordRequest({
          endpoint: req.url,
          method: req.method,
          statusCode: res.status,
          latencyMs,
          ...(((req as Request & { tenantId?: string }).tenantId)
            ? { tenantId: (req as Request & { tenantId?: string }).tenantId }
            : {}),
        });
      });

      next();
    };
  }

  /**
   * Update configuration
   */
  updateConfig(config: Partial<APIMetricsConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Get current configuration
   */
  getConfig(): APIMetricsConfig {
    return { ...this.config };
  }

  /**
   * Stop the metrics service
   */
  stop(): void {
    if (this.flushInterval) {
      clearInterval(this.flushInterval);
    }
  }

  /**
   * Clear all metrics
   */
  clear(): void {
    this.requestMetrics = [];
    this.endpointStats.clear();
  }

  // Private methods

  private updateEndpointStats(metric: RequestMetrics): void {
    const key = this.getEndpointKey(metric.endpoint, metric.method);
    const existing = this.endpointStats.get(key);

    if (!existing) {
      this.endpointStats.set(key, {
        endpoint: metric.endpoint,
        method: metric.method,
        requestCount: 1,
        errorCount: metric.statusCode >= 400 ? 1 : 0,
        avgLatencyMs: metric.latencyMs,
        p50LatencyMs: metric.latencyMs,
        p95LatencyMs: metric.latencyMs,
        p99LatencyMs: metric.latencyMs,
        minLatencyMs: metric.latencyMs,
        maxLatencyMs: metric.latencyMs,
        lastUpdated: new Date(),
      });
      return;
    }

    // Update statistics
    const newCount = existing.requestCount + 1;
    const newErrorCount = existing.errorCount + (metric.statusCode >= 400 ? 1 : 0);

    // Calculate new average
    const newAvg =
      (existing.avgLatencyMs * existing.requestCount + metric.latencyMs) / newCount;

    // Update percentiles (simplified approximation)
    const latencyChange = metric.latencyMs - existing.avgLatencyMs;
    const p50Adjustment = latencyChange * 0.1;
    const p95Adjustment = latencyChange * 0.3;
    const p99Adjustment = latencyChange * 0.5;

    this.endpointStats.set(key, {
      ...existing,
      requestCount: newCount,
      errorCount: newErrorCount,
      avgLatencyMs: newAvg,
      p50LatencyMs: Math.max(0, existing.p50LatencyMs + p50Adjustment),
      p95LatencyMs: Math.max(0, existing.p95LatencyMs + p95Adjustment),
      p99LatencyMs: Math.max(0, existing.p99LatencyMs + p99Adjustment),
      minLatencyMs: Math.min(existing.minLatencyMs, metric.latencyMs),
      maxLatencyMs: Math.max(existing.maxLatencyMs, metric.latencyMs),
      lastUpdated: new Date(),
    });
  }

  private getRecentMetrics(durationMs: number): RequestMetrics[] {
    const cutoff = Date.now() - durationMs;
    return this.requestMetrics.filter((m) => m.timestamp.getTime() > cutoff);
  }

  private getEndpointKey(endpoint: string, method: string): string {
    return `${method}:${endpoint}`;
  }

  private handleSlowRequest(metric: RequestMetrics): void {
    console.warn(
      `[SLOW REQUEST] ${metric.method} ${metric.endpoint} took ${metric.latencyMs}ms`
    );
  }

  private startFlushInterval(): void {
    this.flushInterval = setInterval(() => {
      this.flushMetrics();
    }, this.config.flushIntervalMs);
  }

  private flushMetrics(): void {
    const snapshot = this.getMetricsSnapshot();

    // This would send metrics to monitoring service (e.g., DataDog, New Relic)
    // For now, just log summary
    if (snapshot.totalRequests > 0) {
      console.log(
        `[METRICS] ${snapshot.totalRequests} requests, ${(snapshot.errorRate * 100).toFixed(2)}% errors, avg ${snapshot.avgLatencyMs.toFixed(0)}ms`
      );
    }
  }
}

// Global instance for application-wide metrics
let globalMetricsService: APIMetricsService | null = null;

export function getGlobalMetricsService(): APIMetricsService {
  if (!globalMetricsService) {
    globalMetricsService = new APIMetricsService();
  }
  return globalMetricsService;
}

// Type definitions for Express-like middleware
interface Request {
  url: string;
  method: string;
  tenantId?: string;
}

interface Response {
  status: number;
  on(event: string, callback: () => void): void;
}
