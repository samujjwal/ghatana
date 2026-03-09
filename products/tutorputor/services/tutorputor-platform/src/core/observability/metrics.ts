import type { FastifyInstance } from "fastify";
import { register, Counter, Histogram, Gauge } from "prom-client";
import type { PrismaClient } from "@ghatana/tutorputor-db";
import type Redis from "ioredis";

/**
 * Setup Prometheus metrics collection.
 *
 * ✅ PRODUCTION-GRADE: Comprehensive metrics for all HTTP requests and system health
 */
export async function setupMetrics(app: FastifyInstance) {
  // Request metrics
  const httpRequestDuration = new Histogram({
    name: "tutorputor_http_request_duration_seconds",
    help: "Duration of HTTP requests in seconds",
    labelNames: ["method", "route", "status_code", "tenant_id"],
    buckets: [0.01, 0.05, 0.1, 0.5, 1, 2, 5],
  });

  const httpRequestTotal = new Counter({
    name: "tutorputor_http_request_total",
    help: "Total number of HTTP requests",
    labelNames: ["method", "route", "status_code", "tenant_id"],
  });

  const activeRequests = new Gauge({
    name: "tutorputor_active_requests",
    help: "Number of requests currently being processed",
  });

  // Database metrics
  const dbQueryDuration = new Histogram({
    name: "tutorputor_db_query_duration_seconds",
    help: "Duration of database queries",
    labelNames: ["operation", "model"],
    buckets: [0.001, 0.01, 0.05, 0.1, 0.5, 1],
  });

  // Cache metrics
  const cacheHits = new Counter({
    name: "tutorputor_cache_hits_total",
    help: "Total number of cache hits",
    labelNames: ["cache_key_prefix"],
  });

  const cacheMisses = new Counter({
    name: "tutorputor_cache_misses_total",
    help: "Total number of cache misses",
    labelNames: ["cache_key_prefix"],
  });

  // Hook into request lifecycle
  app.addHook("onRequest", async (request) => {
    (request as any).startTime = Date.now();
    activeRequests.inc();
  });

  app.addHook("onResponse", async (request, reply) => {
    activeRequests.dec();

    const duration = (Date.now() - (request as any).startTime!) / 1000;
    const route = request.routeOptions.url || "unknown";
    const tenantId = (request.headers["x-tenant-id"] as string) || "default";

    httpRequestDuration.observe(
      {
        method: request.method,
        route,
        status_code: reply.statusCode,
        tenant_id: tenantId,
      },
      duration,
    );

    httpRequestTotal.inc({
      method: request.method,
      route,
      status_code: reply.statusCode,
      tenant_id: tenantId,
    });
  });

  // Decorate app with metrics
  app.decorate("metrics", {
    dbQueryDuration,
    cacheHits,
    cacheMisses,
  });

  // Expose metrics endpoint
  app.get("/metrics", async (request, reply) => {
    reply.header("Content-Type", register.contentType);
    return register.metrics();
  });

  app.log.info("Metrics collection initialized");
}

/**
 * Setup health check endpoints.
 *
 * ✅ KUBERNETES-READY: Liveness, readiness, and health probes
 */
export async function setupHealthChecks(
  app: FastifyInstance,
  prisma: PrismaClient,
  redis: Redis,
) {
  // Main health check (deep checks)
  app.get("/health", async (request, reply) => {
    const checks: Record<
      string,
      { status: "ok" | "failed"; latency?: number; error?: string }
    > = {};
    let isHealthy = true;

    // Check database
    try {
      const start = Date.now();
      await prisma.$queryRaw`SELECT 1`;
      checks.database = { status: "ok", latency: Date.now() - start };
    } catch (error) {
      checks.database = {
        status: "failed",
        error: error instanceof Error ? error.message : "Unknown error",
      };
      isHealthy = false;
    }

    // Check Redis
    try {
      const start = Date.now();
      await redis.ping();
      checks.redis = { status: "ok", latency: Date.now() - start };
    } catch (error) {
      checks.redis = {
        status: "failed",
        error: error instanceof Error ? error.message : "Unknown error",
      };
      isHealthy = false;
    }

    return reply.code(isHealthy ? 200 : 503).send({
      status: isHealthy ? "healthy" : "unhealthy",
      timestamp: new Date().toISOString(),
      checks,
      uptime: process.uptime(),
      memory: process.memoryUsage(),
    });
  });

  // Liveness probe (for Kubernetes)
  app.get("/health/live", async (request, reply) => {
    return { status: "alive", timestamp: new Date().toISOString() };
  });

  // Readiness probe (for Kubernetes)
  app.get("/health/ready", async (request, reply) => {
    // Check if service is ready to accept traffic
    try {
      await prisma.$queryRaw`SELECT 1`;
      await redis.ping();
      return { status: "ready", timestamp: new Date().toISOString() };
    } catch (error) {
      return reply.code(503).send({
        status: "not-ready",
        timestamp: new Date().toISOString(),
        error: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.log.info("Health checks configured");
}
