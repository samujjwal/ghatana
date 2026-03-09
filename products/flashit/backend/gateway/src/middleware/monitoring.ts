/**
 * Monitoring Middleware for Flashit Web API
 * Request/response monitoring and metrics collection
 *
 * @doc.type middleware
 * @doc.purpose API monitoring and observability
 * @doc.layer product
 * @doc.pattern Middleware
 */

import {
  FastifyInstance,
  FastifyRequest,
  FastifyReply,
  FastifyPluginAsync,
} from 'fastify';
import fp from 'fastify-plugin';

// ============================================================================
// Types & Interfaces
// ============================================================================

export interface MonitoringConfig {
  enabled: boolean;
  collectRequestBody?: boolean;
  collectResponseBody?: boolean;
  slowRequestThreshold?: number;
  excludePaths?: string[];
}

export interface RequestMetrics {
  requestId: string;
  method: string;
  url: string;
  statusCode: number;
  duration: number;
  timestamp: number;
  userId?: string;
  userAgent?: string;
  ip?: string;
  errorMessage?: string;
}

// ============================================================================
// Constants
// ============================================================================

const DEFAULT_CONFIG: MonitoringConfig = {
  enabled: true,
  collectRequestBody: false,
  collectResponseBody: false,
  slowRequestThreshold: 1000, // 1 second
  excludePaths: ['/health', '/metrics', '/favicon.ico'],
};

// Metrics storage (in-memory, would use proper metrics backend in production)
const requestMetrics: RequestMetrics[] = [];
const MAX_STORED_METRICS = 10000;

// ============================================================================
// Metrics Aggregation
// ============================================================================

class MetricsAggregator {
  private metrics: Map<string, number[]> = new Map();

  record(key: string, value: number): void {
    if (!this.metrics.has(key)) {
      this.metrics.set(key, []);
    }
    this.metrics.get(key)!.push(value);
  }

  getStats(key: string): {
    count: number;
    min: number;
    max: number;
    avg: number;
    p50: number;
    p95: number;
    p99: number;
  } | null {
    const values = this.metrics.get(key);
    if (!values || values.length === 0) return null;

    const sorted = [...values].sort((a, b) => a - b);
    const count = sorted.length;

    return {
      count,
      min: sorted[0],
      max: sorted[count - 1],
      avg: sorted.reduce((a, b) => a + b, 0) / count,
      p50: sorted[Math.floor(count * 0.5)],
      p95: sorted[Math.floor(count * 0.95)],
      p99: sorted[Math.floor(count * 0.99)],
    };
  }

  clear(): void {
    this.metrics.clear();
  }
}

const aggregator = new MetricsAggregator();

// ============================================================================
// Monitoring Plugin
// ============================================================================

const monitoringPlugin: FastifyPluginAsync<MonitoringConfig> = async (
  fastify: FastifyInstance,
  options: MonitoringConfig
) => {
  const config: MonitoringConfig = { ...DEFAULT_CONFIG, ...options };

  if (!config.enabled) return;

  // Decorate fastify with monitoring utilities
  fastify.decorate('getMetrics', () => requestMetrics);
  fastify.decorate('getAggregatedMetrics', (metric: string) => aggregator.getStats(metric));

  // Request start hook
  fastify.addHook('onRequest', async (request, reply) => {
    // Skip excluded paths
    if (config.excludePaths?.some((path) => request.url.startsWith(path))) {
      return;
    }

    // Store start time
    (request as unknown as { startTime: number }).startTime = Date.now();
  });

  // Response hook
  fastify.addHook('onResponse', async (request, reply) => {
    // Skip excluded paths
    if (config.excludePaths?.some((path) => request.url.startsWith(path))) {
      return;
    }

    const startTime = (request as unknown as { startTime?: number }).startTime;
    if (!startTime) return;

    const duration = Date.now() - startTime;
    const metrics: RequestMetrics = {
      requestId: request.id,
      method: request.method,
      url: request.url,
      statusCode: reply.statusCode,
      duration,
      timestamp: Date.now(),
      userId: (request as unknown as { userId?: string }).userId,
      userAgent: request.headers['user-agent'],
      ip: request.ip,
    };

    // Store metrics
    requestMetrics.push(metrics);

    // Keep only recent metrics
    if (requestMetrics.length > MAX_STORED_METRICS) {
      requestMetrics.shift();
    }

    // Aggregate metrics
    aggregator.record(`${request.method}:${request.url}`, duration);
    aggregator.record(`status:${reply.statusCode}`, duration);
    aggregator.record('all', duration);

    // Log slow requests
    if (duration > (config.slowRequestThreshold || 1000)) {
      fastify.log.warn({
        msg: 'Slow request detected',
        ...metrics,
      });
    }

    // Log errors
    if (reply.statusCode >= 500) {
      fastify.log.error({
        msg: 'Server error',
        ...metrics,
      });
    }
  });

  // Error hook
  fastify.addHook('onError', async (request, reply, error) => {
    const startTime = (request as unknown as { startTime?: number }).startTime;
    const duration = startTime ? Date.now() - startTime : 0;

    const metrics: RequestMetrics = {
      requestId: request.id,
      method: request.method,
      url: request.url,
      statusCode: reply.statusCode || 500,
      duration,
      timestamp: Date.now(),
      userId: (request as unknown as { userId?: string }).userId,
      userAgent: request.headers['user-agent'],
      ip: request.ip,
      errorMessage: error.message,
    };

    requestMetrics.push(metrics);

    fastify.log.error({
      msg: 'Request error',
      ...metrics,
      stack: error.stack,
    });
  });

  // Metrics endpoint
  fastify.get('/api/monitoring/metrics', async () => {
    return {
      total: requestMetrics.length,
      recent: requestMetrics.slice(-100),
    };
  });

  // Aggregated metrics endpoint
  fastify.get('/api/monitoring/stats', async () => {
    return {
      all: aggregator.getStats('all'),
      byMethod: {
        GET: aggregator.getStats('GET:*'),
        POST: aggregator.getStats('POST:*'),
        PUT: aggregator.getStats('PUT:*'),
        DELETE: aggregator.getStats('DELETE:*'),
      },
      byStatus: {
        '2xx': aggregator.getStats('status:2*'),
        '4xx': aggregator.getStats('status:4*'),
        '5xx': aggregator.getStats('status:5*'),
      },
    };
  });

  // Health check endpoint
  fastify.get('/api/health/monitoring', async () => {
    const memoryUsage = process.memoryUsage();
    const uptime = process.uptime();

    return {
      status: 'healthy',
      uptime,
      memory: {
        rss: memoryUsage.rss,
        heapTotal: memoryUsage.heapTotal,
        heapUsed: memoryUsage.heapUsed,
        external: memoryUsage.external,
      },
      timestamp: new Date().toISOString(),
    };
  });

  // Readiness check endpoint
  fastify.get('/ready', async () => {
    // Check if server is ready to accept requests
    // Could include database connectivity, external services, etc.
    return {
      status: 'ready',
      timestamp: new Date().toISOString(),
    };
  });
};

// ============================================================================
// Exports
// ============================================================================

export const monitoring = fp(monitoringPlugin, {
  name: 'monitoring',
  fastify: '5.x',
});

/**
 * Get request metrics
 */
export function getRequestMetrics(): RequestMetrics[] {
  return [...requestMetrics];
}

/**
 * Get aggregated metrics
 */
export function getAggregatedMetrics(metric: string) {
  return aggregator.getStats(metric);
}

/**
 * Clear all metrics
 */
export function clearMetrics(): void {
  requestMetrics.length = 0;
  aggregator.clear();
}

export default monitoring;
