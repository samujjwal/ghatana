import type { FastifyPluginAsync } from "fastify";
import fastifyJwt from "@fastify/jwt";
import type { PrismaClient } from "@tutorputor/core/db";
import type Redis from "ioredis";
import { register as metricsRegistry, collectDefaultMetrics, Counter, Histogram } from "prom-client";
import { getConfig } from "../config/config.js";
import { getRedisClient } from "./redis-client.js";
import { errorHandler } from "../core/middleware/error-handler.js";
import { setupRateLimit } from "../core/middleware/rate-limit.js";
import { createConsentEnforcement } from "../core/middleware/consent-enforcement.js";
import { setupInputSanitizer } from "../core/middleware/input-sanitizer.js";
import {
  standardErrorResponseMiddleware,
  addRequestIdHook,
} from "../core/middleware/standard-error-response.js";

interface CorePluginsOptions {
  jwtSecret?: string;
  redisUrl?: string;
  prisma?: PrismaClient;
  redis?: unknown;
}

interface RedisHealthClient {
  ping(): Promise<unknown>;
}

/**
 * Core infrastructure plugin.
 * Sets up Prisma, Redis, JWT, error handling, rate limiting, consent enforcement, input sanitization, and standard error responses.
 *
 * @doc.type module
 * @doc.purpose Core infrastructure setup (DB, Redis, JWT, security, error handling)
 * @doc.layer platform
 * @doc.pattern Plugin
 */
export const setupCorePlugins: FastifyPluginAsync<CorePluginsOptions> = async (
  app,
  options,
) => {
  const config = getConfig();

  // 1. Setup Prisma
  if (options.prisma) {
    app.prisma = options.prisma;
  } else {
    const { PrismaClient } = await import("@tutorputor/core/db");
    app.prisma = new PrismaClient({
      datasourceUrl: process.env.DATABASE_URL,
    } as ConstructorParameters<typeof PrismaClient>[0]) as PrismaClient;
  }

  // 2. Setup Redis
  if (options.redis) {
    app.redis = options.redis as Redis;
  } else {
    app.redis = getRedisClient(options.redisUrl);
  }

  // 3. Setup JWT
  await app.register(fastifyJwt, {
    secret: options.jwtSecret || config.JWT_SECRET,
  });

  // 4. Setup standard error handling
  app.setErrorHandler(standardErrorResponseMiddleware);

  // 5. Setup request ID generation
  app.addHook("onRequest", addRequestIdHook);

  // 6. Setup rate limiting
  await setupRateLimit(app);

  // 7. Setup consent enforcement middleware
  const consentEnforcement = createConsentEnforcement({
    prisma: app.prisma,
  });

  // Register consent enforcement preHandler globally
  // This will check consent for routes that require it
  app.addHook("preHandler", consentEnforcement.preHandler);

  // 8. Setup input sanitization
  await setupInputSanitizer(app);

  // 9. Setup health check
  app.get("/health", async (request, reply) => {
    try {
      // Check database connection
      await app.prisma.$queryRaw`SELECT 1`;

      // Check Redis connection
      await (app.redis as unknown as RedisHealthClient).ping();

      return reply.send({
        status: "ok",
        timestamp: new Date().toISOString(),
        checks: {
          database: "ok",
          redis: "ok",
        },
      });
    } catch (error) {
      request.log.error({ error }, "Health check failed");
      return reply.status(503).send({
        status: "error",
        timestamp: new Date().toISOString(),
        checks: {
          database: "error",
          redis: "error",
        },
      });
    }
  });

  // 10. Setup Prometheus metrics registry
  collectDefaultMetrics({ register: metricsRegistry });

  const httpRequestCounter = new Counter({
    name: "http_requests_total",
    help: "Total number of HTTP requests",
    labelNames: ["method", "route", "status_code"],
    registers: [metricsRegistry],
  });

  const httpRequestDurationHistogram = new Histogram({
    name: "http_request_duration_seconds",
    help: "Duration of HTTP requests in seconds",
    labelNames: ["method", "route", "status_code"],
    buckets: [0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10],
    registers: [metricsRegistry],
  });

  app.addHook("onResponse", async (request, reply) => {
    const route = request.routeOptions.url ?? "unknown";
    const labels = { method: request.method, route, status_code: String(reply.statusCode) };
    httpRequestCounter.inc(labels);
    httpRequestDurationHistogram.observe(labels, reply.elapsedTime / 1000);
  });

  app.get("/metrics", async (request, reply) => {
    reply.type("text/plain");
    return reply.send(await metricsRegistry.metrics());
  });

  // 11. Setup readiness check
  app.get("/ready", async (request, reply) => {
    try {
      await app.prisma.$queryRaw`SELECT 1`;
      await (app.redis as unknown as RedisHealthClient).ping();
      return reply.send({ status: "ready" });
    } catch {
      return reply.status(503).send({ status: "not ready" });
    }
  });

  app.log.info("✅ Core plugins registered");
};
