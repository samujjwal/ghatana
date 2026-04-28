import Fastify from "fastify";
import type { FastifyInstance } from "fastify";
import { PrismaClient } from "@tutorputor/core/db";

import { assertTrustedProxyNotEnabledInProduction } from "./core/http/trustedProxyAuth.js";

// Composable plugins (F-024)
import { setupCorePlugins } from "./plugins/core.js";
import { setupContentModules } from "./plugins/content-modules.js";
import { setupBusinessModules } from "./plugins/business-modules.js";
import { setupAdminModules } from "./plugins/admin-modules.js";
import { setupWorkers } from "./plugins/workers.js";

export { getRedisClient, closeRedisClient } from "./plugins/redis-client.js";

export interface PlatformOptions {
  redisUrl?: string;
  jwtSecret?: string;
  startContentWorker?: boolean;
  grpcServerAddress?: string;
  grpcUseTls?: boolean;
  startLearnerProfileGrpcServer?: boolean;
  learnerProfileGrpcAddress?: string;
  /** Injected Prisma client for testing. When provided, the real PrismaClient is not created. */
  prisma?: PrismaClient;
  /** Injected Redis client for testing. When provided, the real Redis client is not created. */
  redis?: unknown;
}

/**
 * Configure the Fastify instance with TutorPutor Platform capabilities.
 *
 * Composes independent plugins (F-024) in a deterministic order:
 *  1. Core infrastructure (DB, Redis, security, auth guard, error handler)
 *  2. Content-domain modules (studio, simulation, search, kernel registry)
 *  3. Business-domain modules (learning, user, auth, AI, …)
 *  4. Admin / platform-ops modules (VR, notifications, payments, feature-flags)
 *  5. Background workers (content worker, lifecycle cleanup)
 *
 * @doc.type factory
 * @doc.purpose Configure the Fastify instance with all TutorPutor modules
 * @doc.layer platform
 * @doc.pattern Plugin
 */
export async function setupPlatform(
  app: FastifyInstance,
  options: PlatformOptions = {},
): Promise<FastifyInstance> {
  // F-025: Non-negotiable security gate — must run before any hook is registered.
  assertTrustedProxyNotEnabledInProduction();

  // 1. Core infrastructure
  await app.register(setupCorePlugins, {
    jwtSecret: options.jwtSecret,
    redisUrl: options.redisUrl,
    prisma: options.prisma,
    redis: options.redis,
  });

  // 2. Content-domain modules
  await app.register(setupContentModules);

  // 3. Business-domain modules
  await app.register(setupBusinessModules, {
    startLearnerProfileGrpcServer: options.startLearnerProfileGrpcServer,
    learnerProfileGrpcAddress: options.learnerProfileGrpcAddress,
  });

  // 4. Admin / platform-ops modules
  await app.register(setupAdminModules);

  // 5. Background workers + lifecycle cleanup
  await app.register(setupWorkers, {
    startContentWorker: options.startContentWorker,
    grpcServerAddress: options.grpcServerAddress,
    grpcUseTls: options.grpcUseTls,
    redisUrl: options.redisUrl,
  });

  return app;
}

/**
 * Creates a fully-configured Fastify server instance.
 * When `prisma` or `redis` are provided in options, they are used directly (useful for testing).
 *
 * @doc.type factory
 * @doc.purpose Creates and returns a configured FastifyInstance for the TutorPutor Platform.
 * @doc.layer platform
 * @doc.pattern Factory
 */
export async function createServer(
  options: PlatformOptions = {},
): Promise<FastifyInstance> {
  const app = Fastify({ logger: false }) as FastifyInstance;
  await setupPlatform(app, options);
  return app;
}

