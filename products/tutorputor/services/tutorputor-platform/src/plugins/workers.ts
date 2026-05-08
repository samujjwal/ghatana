import type { FastifyPluginAsync, FastifyInstance } from "fastify";
import { initializeContentWorker } from "../startup/content-worker-init.js";
import { closeRedisClient } from "./redis-client.js";
import { getConfig } from "../config/config.js";
import type { PrismaClient } from "@tutorputor/core/db";
import type { Logger } from "pino";

interface WorkersOptions {
  startContentWorker?: boolean;
  grpcServerAddress?: string;
  grpcUseTls?: boolean;
  redisUrl?: string;
}

/**
 * Background workers plugin.
 * Initializes and manages the content generation worker with graceful shutdown.
 *
 * @doc.type module
 * @doc.purpose Register background workers (content generation worker)
 * @doc.layer platform
 * @doc.pattern Plugin
 */
export const setupWorkers: FastifyPluginAsync<WorkersOptions> = async (
  app,
  options,
) => {
  const config = getConfig();

  const startWorker =
    options.startContentWorker !== undefined
      ? options.startContentWorker
      : config.CONTENT_WORKER_ENABLED === undefined
        ? true
        : String(config.CONTENT_WORKER_ENABLED) === "true";

  if (startWorker) {
    try {
      const worker = await initializeContentWorker({
        shouldStart: true,
        redisUrl: options.redisUrl || config.REDIS_URL || "",
        grpcServerAddress: options.grpcServerAddress || "",
        grpcUseTls: options.grpcUseTls ?? false,
        logger: app.log as Logger,
        prisma: app.prisma as PrismaClient,
        requireContentWorker: process.env.REQUIRE_CONTENT_WORKER === "true",
      });

      // Store worker controller on app instance for cleanup
      if (worker) {
        (app as FastifyInstance & { contentWorkerController?: unknown }).contentWorkerController = worker;
      }

      app.log.info("✅ Content worker initialized");
    } catch (error) {
      app.log.error({ error }, "Failed to initialize content worker");

      // If worker is required, fail hard
      if (process.env.REQUIRE_CONTENT_WORKER === "true") {
        throw error;
      }

      // Otherwise, continue in degraded mode
      app.log.warn("Content worker not available, running in degraded mode");
    }
  } else {
    app.log.info("Content worker disabled by configuration");
  }

  // Graceful shutdown hook
  app.addHook("onClose", async () => {
    app.log.info("Shutting down workers...");

    // Close content worker if initialized
    const worker = (app as FastifyInstance & { contentWorkerController?: unknown }).contentWorkerController;
    if (worker && typeof worker === "object" && "close" in worker && typeof worker.close === "function") {
      try {
        app.log.info("Closing content worker...");
        await worker.close();
        app.log.info("Content worker closed successfully");
      } catch (error) {
        app.log.error({ error }, "Error closing content worker");
      }
    }

    // Close Redis connection
    await closeRedisClient();
    app.log.info("Workers shutdown complete");
  });
};
