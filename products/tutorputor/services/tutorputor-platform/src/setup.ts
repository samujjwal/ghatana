import type { FastifyInstance } from "fastify";
import { PrismaClient } from "@ghatana/tutorputor-db";
import Redis from "ioredis";
import jwt from "@fastify/jwt";
import helmet from "@fastify/helmet";
import cors from "@fastify/cors";

import {
  setupMetrics,
  setupHealthChecks,
} from "./core/observability/metrics.js";
import { setupErrorTracking } from "./core/observability/error-tracking.js";
import { setupRateLimit } from "./core/middleware/rate-limit.js";
import { ContentWorkerService } from "./workers/content/index.js";

// Core Modules
import { contentModule } from "./modules/content/index.js";
import { learningModule } from "./modules/learning/index.js";
import { collaborationModule } from "./modules/collaboration/index.js";
import { userModule } from "./modules/user/index.js";
import { engagementModule } from "./modules/engagement/index.js";
import { integrationModule } from "./modules/integration/index.js";
import { tenantModule } from "./modules/tenant/index.js";
import { authModule } from "./modules/auth/index.js";
import { aiModule } from "./modules/ai/index.js";
import { autoRevisionModule } from "./modules/auto-revision/module.js";
import { contentNeedsModule } from "./modules/content-needs/module.js";

const REDIS_URL = process.env.REDIS_URL || "redis://localhost:6379";

function requireEnv(name: string, fallbackForTest?: string): string {
  const value = process.env[name];
  if (value) return value;
  if (process.env.NODE_ENV === "test" && fallbackForTest !== undefined) return fallbackForTest;
  throw new Error(`[startup] Required environment variable ${name} is not set.`);
}

export interface PlatformOptions {
  redisUrl?: string;
  jwtSecret?: string;
  startContentWorker?: boolean;
  grpcServerAddress?: string;
  grpcUseTls?: boolean;
}

/**
 * Configure the Fastify instance with TutorPutor Platform capabilities.
 * - Database (Prisma)
 * - Cache (Redis)
 * - Security (Helmet, CORS, JWT)
 * - Observability
 * - All Business Modules
 */
export async function setupPlatform(
  app: FastifyInstance,
  options: PlatformOptions = {},
) {
  // Security
  await app.register(helmet, {
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        styleSrc: ["'self'", "'unsafe-inline'"],
        scriptSrc: ["'self'"],
        imgSrc: ["'self'", "data:", "https:"],
      },
    },
  });

  // CORS might be handled by gateway if preferred, but safe to default here
  // If gateway registers it first, this might conflict or be skipped
  if (!app.hasPlugin("@fastify/cors")) {
    await app.register(cors, {
      origin: process.env.CORS_ORIGIN || "*",
      credentials: true,
    });
  }

  if (!app.hasPlugin("@fastify/jwt")) {
    await app.register(jwt, {
      secret:
        options.jwtSecret ||
        requireEnv("JWT_SECRET", "test-secret-do-not-use-in-prod"),
    });
  }

  // Database
  const prisma = new PrismaClient({
    log:
      process.env.NODE_ENV === "development"
        ? ["query", "error", "warn"]
        : ["error"],
  });
  app.decorate("prisma", prisma);

  // Redis
  const redis = new Redis(options.redisUrl || REDIS_URL, {
    maxRetriesPerRequest: 3,
    enableReadyCheck: true,
    lazyConnect: false,
  });
  app.decorate("redis", redis);

  // Observability
  await setupMetrics(app);
  await setupHealthChecks(app, prisma, redis);
  await setupErrorTracking(app);

  // Rate limiting
  await setupRateLimit(app);

  // Register All Modules
  app.log.info("Registering TutorPutor modules...");
  await app.register(contentModule, { prefix: "/api" });
  await app.register(learningModule, { prefix: "/api/learning" });
  await app.register(userModule, { prefix: "/api/users" });
  await app.register(collaborationModule, { prefix: "/api/collaboration" });
  await app.register(engagementModule, { prefix: "/api/engagement" });
  await app.register(integrationModule, { prefix: "/api/integration" });
  await app.register(tenantModule, { prefix: "/api/tenant" });
  await app.register(authModule, { prefix: "/api/auth" });
  await app.register(aiModule, { prefix: "/api/v1/ai" });
  await app.register(autoRevisionModule, { prefix: "/api/auto-revision" });
  await app.register(contentNeedsModule, { prefix: "/api/content-needs" });

  const shouldStartContentWorker =
    options.startContentWorker ??
    (process.env.CONTENT_WORKER_ENABLED
      ? process.env.CONTENT_WORKER_ENABLED === "true"
      : process.env.NODE_ENV !== "test");

  let contentWorker: ContentWorkerService | null = null;
  if (shouldStartContentWorker) {
    const redisUrlObj = new URL(options.redisUrl || REDIS_URL);
    const redisDb = redisUrlObj.pathname?.slice(1);

    contentWorker = new ContentWorkerService({
      redis: {
        host: redisUrlObj.hostname,
        port: parseInt(redisUrlObj.port || "6379", 10),
        password: redisUrlObj.password || undefined,
        db: redisDb ? parseInt(redisDb, 10) || 0 : 0,
      },
      grpc: {
        serverAddress:
          options.grpcServerAddress ||
          process.env.GRPC_SERVER_ADDRESS ||
          "localhost:50051",
        useTls:
          options.grpcUseTls ?? process.env.GRPC_USE_TLS === "true",
      },
      logger: app.log as any,
      prisma,
    });

    app.log.info("Content worker initialized");
  } else {
    app.log.info("Content worker startup disabled");
  }

  // Add cleanup hook
  app.addHook("onClose", async (instance) => {
    if (contentWorker) {
      await contentWorker.close();
    }
    if ((instance as any).hasDecorator?.("autoRevisionWorkerManager")) {
      await (instance as any).autoRevisionWorkerManager.stop();
    }
    await instance.prisma.$disconnect();
    instance.redis.disconnect();
  });

  return app;
}
