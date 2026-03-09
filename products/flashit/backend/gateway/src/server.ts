// OpenTelemetry MUST be initialized before any other imports for auto-instrumentation
import './plugins/tracing';

import "dotenv/config";

import Fastify from "fastify";
import cors from "@fastify/cors";
import helmet from "@fastify/helmet";
import sensible from "@fastify/sensible";
import rateLimit from "@fastify/rate-limit";
import jwt from "@fastify/jwt";
import multipart from "@fastify/multipart";
import { loadEnv } from "./env";
import { prisma, disconnectPrisma } from "./lib/prisma";
import monitoring from "./middleware/monitoring";
import { registerLoggerPlugin } from "./lib/logger";
import { registerAuthRoutesEnhanced as registerAuthRoutes } from "./routes/auth-enhanced";
import { registerMomentRoutes } from "./routes/moments";
import { registerSphereRoutes } from "./routes/spheres";
import uploadRoutes from "./routes/upload";
import progressiveUploadRoutes from "./routes/progressive-upload";
import transcriptionRoutes from "./routes/transcription";
import searchRoutes from "./routes/search";
import analyticsRoutes from "./routes/analytics";
import collaborationRoutes from "./routes/collaboration";
import privacyRoutes from "./routes/privacy";
import momentLinkRoutes from "./routes/moment-links";
import memoryExpansionRoutes from "./routes/memory-expansion";
import healthRoutes from "./routes/health";
import adoptionRoutes from "./routes/adoption";
import { registerBillingRoutes } from "./routes/billing";
import { registerReflectionRoutes } from "./routes/reflection";
import { registerSystemRoutes } from "./routes/system";
import templateRoutes from "./routes/templates";
import adminRoutes from "./routes/admin";
import notificationRoutes from "./routes/notifications";
import apiKeyRoutes from "./routes/api-keys";
import recommendationRoutes from "./routes/recommendations";
import knowledgeGraphRoutes from "./routes/knowledge-graph";
import { startScheduler, stopScheduler } from "./services/scheduler";
import metricsPlugin from "./plugins/prometheus";
import { registerTracingMiddleware } from "./middleware/tracing";
import { initCache, disconnectCache } from "./lib/cache";
import { JwtPayload } from "./lib/auth";

/**
 * Builds and configures the Flashit Web API server with all middleware and routes.
 * 
 * @description Creates a Fastify server instance with security, CORS, rate limiting,
 * JWT authentication, and multipart file upload support. Registers all API routes
 * for the Flashit personal context capture platform.
 * 
 * @returns {FastifyInstance} Configured Fastify server instance
 * 
 * @example
 * ```typescript
 * const server = buildServer();
 * await server.listen({ port: 8000 });
 * ```
 */
export const buildServer = () => {
  const env = loadEnv();

  const app = Fastify({
    logger: {
      level: env.LOG_LEVEL,
    },
  });

  // Register plugins
  app.register(helmet);
  app.register(cors, { origin: true, credentials: true });
  app.register(sensible);
  app.register(multipart, {
    limits: {
      fileSize: 50 * 1024 * 1024, // 50MB max file size
    },
  });
  app.register(rateLimit, {
    max: 500,
    timeWindow: "1 minute",
  });

  // Monitoring & internal metrics
  app.register(monitoring, {
    enabled: true,
  });

  // Prometheus metrics
  app.register(metricsPlugin);

  // Structured logging with correlation IDs
  app.register(registerLoggerPlugin);

  // Distributed tracing middleware (enriches OTEL spans with FlashIt context)
  app.register(registerTracingMiddleware);

  // Multi-tenant isolation (extracts X-Tenant-ID / JWT tenantId)
  const tenantIsolation = (await import('./middleware/tenant-isolation')).default;
  app.register(tenantIsolation, { strict: false });

  // JWT authentication
  app.register(jwt, {
    secret: env.JWT_SECRET,
  });

  // JWT authentication decorator
  app.decorate("authenticate", async function (request: any, reply: any) {
    try {
      await request.jwtVerify();
    } catch (err) {
      reply.code(401).send({
        error: "Unauthorized",
        message: "Invalid or missing authentication token",
      });
    }
  });

  // Health check
  app.get("/health", async () => ({
    status: "ok",
    service: "flashit-web-api",
    timestamp: new Date().toISOString(),
  }));

  // Legacy status endpoint
  app.get("/api/context/status", async () => ({
    service: "flashit-web-api",
    checkedAt: new Date().toISOString(),
  }));

  // Register API routes
  app.register(registerAuthRoutes);
  app.register(registerMomentRoutes);
  app.register(registerSphereRoutes);
  app.register(uploadRoutes, { prefix: '/api/upload' });
  app.register(progressiveUploadRoutes, { prefix: '/api/progressive' });
  app.register(transcriptionRoutes, { prefix: '/api/transcription' });
  app.register(searchRoutes, { prefix: '/api/search' });
  app.register(analyticsRoutes, { prefix: '/api/analytics' });
  app.register(collaborationRoutes, { prefix: '/api/collaboration' });
  app.register(privacyRoutes, { prefix: '/api/privacy' });
  app.register(momentLinkRoutes, { prefix: '/api/moments' });
  app.register(memoryExpansionRoutes, { prefix: '/api/memory-expansion' });
  app.register(healthRoutes, { prefix: '/api/health' });
  app.register(adoptionRoutes, { prefix: '/api/adoption' });
  // Billing routes define absolute paths under /api/billing/* already
  app.register(registerBillingRoutes);
  app.register(registerReflectionRoutes);
  app.register(registerSystemRoutes);
  app.register(templateRoutes, { prefix: '/api/templates' });
  app.register(adminRoutes, { prefix: '/api/admin' });
  app.register(notificationRoutes, { prefix: '/api/notifications' });
  app.register(apiKeyRoutes, { prefix: '/api/api-keys' });
  app.register(recommendationRoutes, { prefix: '/api/recommendations' });
  app.register(knowledgeGraphRoutes, { prefix: '/api/knowledge-graph' });

  // Global error handler
  app.setErrorHandler((error: any, request: any, reply) => {
    const logger = request.logger || app.log;
    
    // Log error with context
    if (typeof logger.error === 'function' && logger.error.length > 1) {
      // Structured logger
      logger.error('Request failed', error, {
        statusCode: error.statusCode || 500,
        errorName: error.name,
      });
    } else {
      // Fallback to fastify logger
      app.log.error(error);
    }

    // Validation errors
    if (error.validation) {
      return reply.code(400).send({
        error: "Validation error",
        message: error.message,
        details: error.validation,
      });
    }

    // Prisma errors
    if (error.name === "PrismaClientKnownRequestError") {
      return reply.code(400).send({
        error: "Database error",
        message: "An error occurred while processing your request",
      });
    }

    // Default error
    reply.code(error.statusCode || 500).send({
      error: error.name || "Internal Server Error",
      message: error.message || "An unexpected error occurred",
    });
  });

  // Graceful shutdown
  const closeGracefully = async (signal: string) => {
    app.log.info(`Received ${signal}, closing gracefully`);
    stopScheduler();
    await app.close();
    await disconnectCache();
    await disconnectPrisma();
    process.exit(0);
  };

  process.on("SIGINT", () => closeGracefully("SIGINT"));
  process.on("SIGTERM", () => closeGracefully("SIGTERM"));

  return app;
};

export const start = async () => {
  const env = loadEnv();
  const server = buildServer();

  try {
    // Initialize Redis cache (graceful fallback if unavailable)
    await initCache();

    await server.listen({ port: env.PORT, host: env.HOST });
    server.log.info(
      { address: `http://${env.HOST}:${env.PORT}` },
      "Flashit Web API listening"
    );

    // Start background cron jobs
    startScheduler();
  } catch (err) {
    server.log.error(err, "Failed to start Flashit Web API");
    process.exitCode = 1;
  }
};

if (import.meta.url === `file://${process.argv[1]}`) {
  void start();
}
