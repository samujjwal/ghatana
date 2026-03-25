import type { FastifyInstance } from "fastify";
import { PrismaClient } from "@tutorputor/core/db";
import Redis from "ioredis";
import jwt from "@fastify/jwt";
import helmet from "@fastify/helmet";
import cors from "@fastify/cors";
import Stripe from "stripe";

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
import { simulationModule } from "./modules/simulation/index.js";
import { searchModule } from "./modules/search/index.js";
import { registerKernelRegistryRoutes } from "./modules/kernel-registry/fastify-routes.js";
import { vrRoutes } from "./modules/vr/vr-routes.js";
import { notificationRoutes } from "./modules/notifications/index.js";
import { paymentRoutes } from "./modules/payments/routes.js";
import { SubscriptionServiceImpl } from "./modules/payments/service.js";

const REDIS_URL = process.env.REDIS_URL || "redis://localhost:6379";

function requireEnv(name: string, fallbackForTest?: string): string {
  const value = process.env[name];
  if (value) return value;
  if (process.env.NODE_ENV === "test" && fallbackForTest !== undefined)
    return fallbackForTest;
  throw new Error(
    `[startup] Required environment variable ${name} is not set.`,
  );
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

  // -------------------------------------------------------------------------
  // Global JWT Authentication Guard
  // Protects all /api/v1/* routes. Verifies the Bearer token and populates
  // req.user with decoded claims for downstream route handlers.
  //
  // Exemptions (public routes):
  //   - /api/v1/auth/sso/*  — SSO login/callback flows
  //   - /api/v1/auth/health — module health probe
  //   - /health, /healthz, /metrics — infrastructure probes
  //   - Routes outside /api/v1/* (e.g., /api/sim-author/* handled by simulationModule)
  // -------------------------------------------------------------------------
  app.addHook("onRequest", async (req, reply) => {
    const url = req.raw.url ?? req.url;
    // Guard versioned API routes and content-studio routes
    const isGuarded =
      url.startsWith("/api/v1/") || url.startsWith("/api/content-studio/");
    if (!isGuarded) return;
    // Public auth sub-routes (only under /api/v1/)
    if (url.startsWith("/api/v1/auth/sso/") || url === "/api/v1/auth/health")
      return;
    // Public LTI interoperability routes are invoked by external LMS platforms.
    if (
      url === "/api/v1/integration/lti/launch" ||
      url === "/api/v1/integration/lti/jwks" ||
      url.startsWith("/api/v1/integration/lti/config/") ||
      url === "/api/v1/integration/lti/deep-linking" ||
      url === "/api/v1/integration/lti/grade-passback"
    ) {
      return;
    }
    // Stripe webhook endpoint – authentication is via Stripe-Signature header.
    if (url === "/api/v1/integration/billing/webhook") return;
    // Content-studio health is public
    if (url === "/api/content-studio/health") return;

    try {
      await req.jwtVerify();
    } catch {
      reply.code(401).send({
        error: "Unauthorized",
        message: "A valid Bearer token is required.",
      });
    }
  });

  // Register All Modules
  // Canonical prefix strategy: all routes exposed under /api/v1/
  app.log.info("Registering TutorPutor modules...");

  // Content module mounts at /api so its internal /v1/modules routes become /api/v1/modules
  await app.register(contentModule, { prefix: "/api" });

  // Learning: dashboard, enrollments, pathways, assessments
  // Routes within module use /learning/dashboard, /enrollments, /pathways etc.
  await app.register(learningModule, { prefix: "/api/v1" });

  // User: /api/v1/teacher/... and /api/v1/admin/...
  await app.register(userModule, { prefix: "/api/v1" });

  // Collaboration: /api/v1/collaboration/threads etc.
  await app.register(collaborationModule, { prefix: "/api/v1/collaboration" });

  // Engagement: /api/v1/gamification/..., /api/v1/social/..., /api/v1/credentials/...
  await app.register(engagementModule, { prefix: "/api/v1" });

  // Integration and tenant management
  await app.register(integrationModule, { prefix: "/api/v1/integration" });
  await app.register(tenantModule, { prefix: "/api/v1/tenant" });

  // Auth: /api/v1/auth/me, /api/v1/auth/sso/...
  await app.register(authModule, { prefix: "/api/v1/auth" });

  // AI: /api/v1/ai/tutor/query etc. (already versioned)
  await app.register(aiModule, { prefix: "/api/v1/ai" });

  // Revision and content needs
  await app.register(autoRevisionModule, { prefix: "/api/v1/auto-revision" });
  await app.register(contentNeedsModule, { prefix: "/api/v1/content-needs" });

  // Simulation: /api/sim-author/generate etc.
  await app.register(simulationModule);

  // Search: /api/v1/search and /api/v1/search/autocomplete
  await app.register(searchModule, { prefix: "/api/v1/search" });

  // Register consolidated modules
  await registerKernelRegistryRoutes(app);
  app.log.info("✅ Kernel Registry routes registered");

  // VR Labs: /api/v1/vr/labs, /api/v1/vr/sessions, /api/v1/vr/labs/:labId/analytics
  await app.register(vrRoutes, { prefix: "/api/v1/vr" });
  app.log.info("✅ VR routes registered");

  // Notifications: /api/v1/notifications, /api/v1/notifications/preferences
  await app.register(notificationRoutes, { prefix: "/api/v1/notifications" });
  app.log.info("✅ Notification routes registered");

  // Subscription payments: /api/v1/payments/...
  const stripeKey = process.env.STRIPE_SECRET_KEY;
  const stripe = new Stripe(stripeKey ?? "sk_test_placeholder", {
    apiVersion: "2023-10-16" as any,
  });
  const subscriptionService = new SubscriptionServiceImpl(prisma, stripe);
  await app.register(
    (fastify, _opts, done) => {
      paymentRoutes(fastify, { service: subscriptionService }).then(
        () => done(),
        done,
      );
    },
    { prefix: "/api/v1" },
  );
  app.log.info("✅ Payment/subscription routes registered");

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
        useTls: options.grpcUseTls ?? process.env.GRPC_USE_TLS === "true",
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
