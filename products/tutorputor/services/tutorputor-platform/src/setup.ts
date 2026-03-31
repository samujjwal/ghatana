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
import { startLearnerProfileGrpcRuntime } from "./modules/learning/grpc-runtime.js";
import { createLearnerProfileGrpcRuntimeState } from "./modules/learning/grpc-runtime-state.js";
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

function validateStripeKey(key: string): void {
  const stripeKeyPattern = /^sk_(test|live)_[a-zA-Z0-9]{24,}$/;
  if (!stripeKeyPattern.test(key)) {
    throw new Error(
      `[startup] Invalid STRIPE_SECRET_KEY format. Expected sk_test_* or sk_live_* with at least 24 characters.`,
    );
  }
}

function isPublicLtiRoute(method: string, url: string): boolean {
  const routePath = url.split("?")[0] ?? url;

  if (method === "GET") {
    return (
      routePath === "/api/v1/integration/lti/jwks" ||
      routePath.startsWith("/api/v1/integration/lti/config/")
    );
  }

  if (method === "POST") {
    return (
      routePath === "/api/v1/integration/lti/launch" ||
      routePath === "/api/v1/integration/lti/deep-linking" ||
      routePath === "/api/v1/integration/lti/grade-passback"
    );
  }

  return false;
}

export interface PlatformOptions {
  redisUrl?: string;
  jwtSecret?: string;
  startContentWorker?: boolean;
  grpcServerAddress?: string;
  grpcUseTls?: boolean;
  startLearnerProfileGrpcServer?: boolean;
  learnerProfileGrpcAddress?: string;
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
  await app.register(helmet as any, {
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
    await app.register(cors as any, {
      origin: process.env.CORS_ORIGIN || "*",
      credentials: true,
    });
  }

  if (!app.hasPlugin("@fastify/jwt")) {
    await app.register(jwt as any, {
      secret:
        options.jwtSecret ||
        requireEnv("JWT_SECRET", "test-secret-do-not-use-in-prod"),
    });
  }

  // Database with connection pooling
  const prisma = new PrismaClient({
    log:
      process.env.NODE_ENV === "development"
        ? ["query", "error", "warn"]
        : ["error"],
    // Connection pool configuration
    datasources: {
      db: {
        url: process.env.DATABASE_URL,
      },
    },
    // Connection pool limits based on environment
    __internal: {
      engine: {
        // Connection pool configuration
        connectionLimit: parseInt(process.env.DATABASE_POOL_SIZE || "10", 10),
        // Pool timeout in seconds
        poolTimeout: parseInt(process.env.DATABASE_POOL_TIMEOUT || "10", 10),
        // Connection timeout in seconds
        connectTimeout: parseInt(
          process.env.DATABASE_CONNECT_TIMEOUT || "5",
          10,
        ),
        // How long to wait for a connection from the pool (milliseconds)
        acquireConnectionTimeout: parseInt(
          process.env.DATABASE_ACQUIRE_TIMEOUT || "30000",
          10,
        ),
        // How long a connection can be idle before being closed (seconds)
        idleTimeout: parseInt(process.env.DATABASE_IDLE_TIMEOUT || "600", 10),
        // How long a connection can live before being closed (seconds)
        maxLifetime: parseInt(process.env.DATABASE_MAX_LIFETIME || "1800", 10),
      },
    },
  } as any);
  app.decorate("prisma", prisma);

  // Redis
  const redis = new (Redis as unknown as new (...args: any[]) => any)(options.redisUrl || REDIS_URL, {
    maxRetriesPerRequest: 3,
    enableReadyCheck: true,
    lazyConnect: false,
  });
  app.decorate("redis", redis);
  app.decorate(
    "learnerProfileGrpcRuntimeState",
    createLearnerProfileGrpcRuntimeState(),
  );

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
    // Restrict the public surface to expected method + route pairs.
    if (isPublicLtiRoute(req.method, url)) {
      return;
    }
    // Stripe webhook endpoint – authentication is via Stripe-Signature header.
    if (url === "/api/v1/integration/billing/webhook") return;
    // Content-studio health is public
    if (url === "/api/content-studio/health") return;

    try {
      await (req as typeof req & { jwtVerify: () => Promise<void> }).jwtVerify();
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
  await app.register(learningModule as any, { prefix: "/api/v1" });

  const shouldStartLearnerProfileGrpcServer =
    options.startLearnerProfileGrpcServer ??
    process.env.LEARNER_PROFILE_GRPC_ENABLED === "true";
  const learnerProfileGrpcAddress =
    options.learnerProfileGrpcAddress ||
    process.env.LEARNER_PROFILE_GRPC_ADDRESS ||
    "127.0.0.1:50052";
  let learnerProfileGrpcRuntime:
    | Awaited<ReturnType<typeof startLearnerProfileGrpcRuntime>>
    | null = null;

  if (shouldStartLearnerProfileGrpcServer) {
    app.learnerProfileGrpcRuntimeState = {
      enabled: true,
      status: "starting",
      address: learnerProfileGrpcAddress,
    };

    const learnerProfileService = (app as typeof app & {
      learnerProfileService?: unknown;
    }).learnerProfileService;

    if (!learnerProfileService) {
      throw new Error(
        "Learner profile service not found on Fastify instance after learning module registration.",
      );
    }

    try {
      learnerProfileGrpcRuntime = await startLearnerProfileGrpcRuntime({
        learnerProfileService: learnerProfileService as Parameters<
          typeof startLearnerProfileGrpcRuntime
        >[0]["learnerProfileService"],
        address: learnerProfileGrpcAddress,
        logger: app.log,
      });

      app.learnerProfileGrpcRuntimeState = {
        enabled: true,
        status: "running",
        address: learnerProfileGrpcRuntime.address,
        port: learnerProfileGrpcRuntime.port,
        startedAt: new Date().toISOString(),
      };
    } catch (error) {
      app.learnerProfileGrpcRuntimeState = {
        enabled: true,
        status: "failed",
        address: learnerProfileGrpcAddress,
        lastError: error instanceof Error ? error.message : "Unknown error",
      };
      throw error;
    }
  }

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
  const stripeKey = requireEnv("STRIPE_SECRET_KEY");
  validateStripeKey(stripeKey);
  const stripe = new Stripe(stripeKey, {
    apiVersion: "2026-02-25.clover",
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
        ...(redisUrlObj.password ? { password: redisUrlObj.password } : {}),
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
    if (learnerProfileGrpcRuntime) {
      await learnerProfileGrpcRuntime.stop();
      instance.learnerProfileGrpcRuntimeState = {
        ...instance.learnerProfileGrpcRuntimeState,
        enabled: true,
        status: "stopped",
      };
    }
    const instanceWithDecorators = instance as typeof instance & {
      hasDecorator?: (name: string) => boolean;
      autoRevisionWorkerManager?: { stop: () => Promise<void> };
    };

    if (instanceWithDecorators.hasDecorator?.("autoRevisionWorkerManager")) {
      await instanceWithDecorators.autoRevisionWorkerManager?.stop();
    }
    await instance.prisma.$disconnect();
    (instance.redis as any).disconnect();
  });

  return app;
}
