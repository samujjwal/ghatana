/**
 * Prometheus Metrics Plugin for Fastify
 *
 * Exposes a /metrics endpoint in Prometheus text format.
 * Collects default Node.js metrics + custom Flashit counters.
 *
 * Requires `prom-client` to be installed:
 *   npm install prom-client
 *
 * @doc.type plugin
 * @doc.purpose Expose Prometheus-compatible metrics
 * @doc.layer product
 * @doc.pattern Plugin
 */

import { FastifyPluginAsync } from 'fastify';
import client from 'prom-client';

// ============================================================================
// Metrics registry & defaults
// ============================================================================

const register = new client.Registry();

// Collect default metrics (CPU, memory, GC, event loop, etc.)
client.collectDefaultMetrics({ register });

// ============================================================================
// Custom application counters / histograms
// ============================================================================

export const httpRequestDuration = new client.Histogram({
  name: 'flashit_http_request_duration_seconds',
  help: 'Duration of HTTP requests in seconds',
  labelNames: ['method', 'route', 'status_code'] as const,
  buckets: [0.01, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10],
  registers: [register],
});

export const httpRequestTotal = new client.Counter({
  name: 'flashit_http_requests_total',
  help: 'Total number of HTTP requests',
  labelNames: ['method', 'route', 'status_code'] as const,
  registers: [register],
});

export const agentCallTotal = new client.Counter({
  name: 'flashit_agent_calls_total',
  help: 'Total calls to the Java Agent service',
  labelNames: ['operation', 'status'] as const,
  registers: [register],
});

export const agentCallDuration = new client.Histogram({
  name: 'flashit_agent_call_duration_seconds',
  help: 'Duration of Java Agent service calls',
  labelNames: ['operation'] as const,
  buckets: [0.1, 0.5, 1, 2, 5, 10, 30],
  registers: [register],
});

export const activeConnections = new client.Gauge({
  name: 'flashit_active_connections',
  help: 'Number of active HTTP connections',
  registers: [register],
});

// ============================================================================
// Plugin
// ============================================================================

const metricsPlugin: FastifyPluginAsync = async (app) => {
  // Track request duration and count
  app.addHook('onRequest', async (request) => {
    (request as any).__metricsStart = process.hrtime.bigint();
    activeConnections.inc();
  });

  app.addHook('onResponse', async (request, reply) => {
    activeConnections.dec();

    const startBigInt = (request as any).__metricsStart as bigint | undefined;
    if (!startBigInt) return;

    const durationNs = Number(process.hrtime.bigint() - startBigInt);
    const durationSec = durationNs / 1e9;

    const route = request.routeOptions?.url || request.url || 'unknown';
    const method = request.method;
    const statusCode = String(reply.statusCode);

    httpRequestDuration.labels(method, route, statusCode).observe(durationSec);
    httpRequestTotal.labels(method, route, statusCode).inc();
  });

  // Expose /metrics endpoint
  app.get('/metrics', async (_request, reply) => {
    reply.header('Content-Type', register.contentType);
    return register.metrics();
  });
};

export default metricsPlugin;
