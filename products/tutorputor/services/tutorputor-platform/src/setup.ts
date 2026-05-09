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
  const corePluginsOptions: {
    jwtSecret?: string;
    redisUrl?: string;
    prisma?: PrismaClient;
    redis?: unknown;
  } = {};
  if (options.jwtSecret !== undefined) {
    corePluginsOptions.jwtSecret = options.jwtSecret;
  }
  if (options.redisUrl !== undefined) {
    corePluginsOptions.redisUrl = options.redisUrl;
  }
  if (options.prisma !== undefined) {
    corePluginsOptions.prisma = options.prisma;
  }
  if (options.redis !== undefined) {
    corePluginsOptions.redis = options.redis;
  }
  await app.register(setupCorePlugins, corePluginsOptions);

  // 2. Content-domain modules
  await app.register(setupContentModules);

  // 3. Business-domain modules
  const businessModulesOptions: {
    startLearnerProfileGrpcServer?: boolean;
    learnerProfileGrpcAddress?: string;
    prisma?: PrismaClient;
  } = {};
  if (options.startLearnerProfileGrpcServer !== undefined) {
    businessModulesOptions.startLearnerProfileGrpcServer =
      options.startLearnerProfileGrpcServer;
  }
  if (options.learnerProfileGrpcAddress !== undefined) {
    businessModulesOptions.learnerProfileGrpcAddress =
      options.learnerProfileGrpcAddress;
  }
  if (app.prisma) {
    businessModulesOptions.prisma = app.prisma;
  }
  await app.register(setupBusinessModules, businessModulesOptions);

  // 4. Admin / platform-ops modules
  await app.register(setupAdminModules);

  // 5. Background workers + lifecycle cleanup
  const workersOptions: {
    startContentWorker?: boolean;
    grpcServerAddress?: string;
    grpcUseTls?: boolean;
    redisUrl?: string;
  } = {};
  if (options.startContentWorker !== undefined) {
    workersOptions.startContentWorker = options.startContentWorker;
  }
  if (options.grpcServerAddress !== undefined) {
    workersOptions.grpcServerAddress = options.grpcServerAddress;
  }
  if (options.grpcUseTls !== undefined) {
    workersOptions.grpcUseTls = options.grpcUseTls;
  }
  if (options.redisUrl !== undefined) {
    workersOptions.redisUrl = options.redisUrl;
  }
  await app.register(setupWorkers, workersOptions);

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

