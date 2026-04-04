import type { FastifyInstance } from "fastify";
import { register, Counter, Histogram, Gauge } from "prom-client";
import type { PrismaClient } from "@tutorputor/core/db";
import type Redis from "ioredis";
import type { LearnerProfileGrpcRuntimeState } from "../../modules/learning/grpc-runtime-state.js";

// Module-level singleton metric instances — prom-client uses a global registry;
// creating a named metric twice throws. These are initialized once per process.
let _httpRequestDuration: Histogram | undefined;
let _httpRequestTotal: Counter | undefined;
let _activeRequests: Gauge | undefined;
let _dbQueryDuration: Histogram | undefined;
let _dbConnectionsActive: Gauge | undefined;
let _dbConnectionsIdle: Gauge | undefined;
let _dbConnectionsTotal: Gauge | undefined;
let _dbConnectionAcquisitions: Counter | undefined;
let _cacheHits: Counter | undefined;
let _cacheMisses: Counter | undefined;

function getOrInitMetrics() {
  if (!_httpRequestDuration) {
    _httpRequestDuration = new Histogram({
      name: "tutorputor_http_request_duration_seconds",
      help: "Duration of HTTP requests in seconds",
      labelNames: ["method", "route", "status_code", "tenant_id"],
      buckets: [0.01, 0.05, 0.1, 0.5, 1, 2, 5],
    });
    _httpRequestTotal = new Counter({
      name: "tutorputor_http_request_total",
      help: "Total number of HTTP requests",
      labelNames: ["method", "route", "status_code", "tenant_id"],
    });
    _activeRequests = new Gauge({
      name: "tutorputor_active_requests",
      help: "Number of requests currently being processed",
    });
    _dbQueryDuration = new Histogram({
      name: "tutorputor_db_query_duration_seconds",
      help: "Duration of database queries",
      labelNames: ["operation", "model"],
      buckets: [0.001, 0.01, 0.05, 0.1, 0.5, 1],
    });
    _dbConnectionsActive = new Gauge({
      name: "tutorputor_db_connections_active",
      help: "Number of active database connections",
    });
    _dbConnectionsIdle = new Gauge({
      name: "tutorputor_db_connections_idle",
      help: "Number of idle database connections",
    });
    _dbConnectionsTotal = new Gauge({
      name: "tutorputor_db_connections_total",
      help: "Total number of database connections in pool",
    });
    _dbConnectionAcquisitions = new Counter({
      name: "tutorputor_db_connection_acquisitions_total",
      help: "Total number of connection acquisitions from pool",
      labelNames: ["result"],
    });
    _cacheHits = new Counter({
      name: "tutorputor_cache_hits_total",
      help: "Total number of cache hits",
      labelNames: ["cache_key_prefix"],
    });
    _cacheMisses = new Counter({
      name: "tutorputor_cache_misses_total",
      help: "Total number of cache misses",
      labelNames: ["cache_key_prefix"],
    });
  }
  return {
    httpRequestDuration: _httpRequestDuration!,
    httpRequestTotal: _httpRequestTotal!,
    activeRequests: _activeRequests!,
    dbQueryDuration: _dbQueryDuration!,
    dbConnectionsActive: _dbConnectionsActive!,
    dbConnectionsIdle: _dbConnectionsIdle!,
    dbConnectionsTotal: _dbConnectionsTotal!,
    dbConnectionAcquisitions: _dbConnectionAcquisitions!,
    cacheHits: _cacheHits!,
    cacheMisses: _cacheMisses!,
  };
}

/**
 * Setup Prometheus metrics collection.
 *
 * ✅ PRODUCTION-GRADE: Comprehensive metrics for all HTTP requests and system health
 */
export async function setupMetrics(app: FastifyInstance) {
  const {
    httpRequestDuration,
    httpRequestTotal,
    activeRequests,
    dbQueryDuration,
    dbConnectionsActive,
    dbConnectionsIdle,
    dbConnectionsTotal,
    dbConnectionAcquisitions,
    cacheHits,
    cacheMisses,
  } = getOrInitMetrics();

  // Hook into request lifecycle
  app.addHook("onRequest", async (request) => {
    (request as any).startTime = Date.now();
    activeRequests.inc();
  });

  app.addHook("onResponse", async (request, reply) => {
    activeRequests.dec();

    const duration = (Date.now() - (request as any).startTime!) / 1000;
    const route =
      (request.routeOptions as { url?: string } | undefined)?.url ?? "unknown";
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
    dbConnectionsActive,
    dbConnectionsIdle,
    dbConnectionsTotal,
    dbConnectionAcquisitions,
    cacheHits,
    cacheMisses,
  });

  // Expose metrics endpoint
  app.get("/metrics", async (_request, reply) => {
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
  const getLearnerProfileGrpcRuntimeState =
    (): LearnerProfileGrpcRuntimeState | null =>
      app.learnerProfileGrpcRuntimeState ?? null;

  // Main health check (deep checks)
  app.get("/health", async (_request, reply) => {
    const checks: Record<
      string,
      {
        status: "ok" | "failed";
        latency?: number;
        error?: string;
        poolSize?: number;
        mode?: string;
        address?: string;
        port?: number;
      }
    > = {};
    let isHealthy = true;

    // Check database
    try {
      const start = Date.now();
      await prisma.$queryRaw`SELECT 1`;
      checks.database = { status: "ok", latency: Date.now() - start };

      // Add connection pool info if available
      try {
        // Get connection pool metrics (Prisma doesn't expose this directly, but we can infer)
        const poolSize = process.env.DATABASE_POOL_SIZE || "10";
        checks.database.poolSize = parseInt(poolSize, 10);
      } catch (_poolError) {
        // Pool info not available, that's ok
      }
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
      await (redis as any).ping();
      checks.redis = { status: "ok", latency: Date.now() - start };
    } catch (error) {
      checks.redis = {
        status: "failed",
        error: error instanceof Error ? error.message : "Unknown error",
      };
      isHealthy = false;
    }

    const learnerProfileGrpcRuntime = getLearnerProfileGrpcRuntimeState();
    if (learnerProfileGrpcRuntime) {
      const runtimeIsHealthy =
        !learnerProfileGrpcRuntime.enabled ||
        learnerProfileGrpcRuntime.status === "running";

      checks.learnerProfileGrpc = {
        status: runtimeIsHealthy ? "ok" : "failed",
        mode: learnerProfileGrpcRuntime.status,
        ...(learnerProfileGrpcRuntime.address
          ? { address: learnerProfileGrpcRuntime.address }
          : {}),
        ...(learnerProfileGrpcRuntime.port
          ? { port: learnerProfileGrpcRuntime.port }
          : {}),
        ...(learnerProfileGrpcRuntime.lastError
          ? { error: learnerProfileGrpcRuntime.lastError }
          : {}),
      };

      if (!runtimeIsHealthy) {
        isHealthy = false;
      }
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
  app.get("/health/live", async (_request, _reply) => {
    return { status: "alive", timestamp: new Date().toISOString() };
  });

  // Readiness probe (for Kubernetes)
  app.get("/health/ready", async (_request, reply) => {
    // Check if service is ready to accept traffic
    try {
      await prisma.$queryRaw`SELECT 1`;
      await (redis as any).ping();

      const learnerProfileGrpcRuntime = getLearnerProfileGrpcRuntimeState();
      if (
        learnerProfileGrpcRuntime?.enabled &&
        learnerProfileGrpcRuntime.status !== "running"
      ) {
        return reply.code(503).send({
          status: "not-ready",
          timestamp: new Date().toISOString(),
          error: "Learner-profile gRPC runtime is not running",
          checks: {
            learnerProfileGrpc: learnerProfileGrpcRuntime,
          },
        });
      }

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
