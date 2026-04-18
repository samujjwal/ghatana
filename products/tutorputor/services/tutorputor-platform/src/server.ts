import Fastify from "fastify";
import pino from "pino";
import "./types/fastify.js";
import { setupPlatform } from "./setup.js";
import { getConfig } from "./config/config.js";
import { initializeTracing } from "./monitoring/tracing.js";
import { tracingMiddleware } from "./monitoring/middleware/tracing.js";

const config = getConfig();

// Global shutdown flag
let isShuttingDown = false;

async function bootstrap() {
  // Initialize distributed tracing
  await initializeTracing();

  const app = Fastify({
    logger: {
      level: config.LOG_LEVEL,
      transport: {
        target: "pino-pretty",
        options: {
          translateTime: "HH:MM:ss Z",
          ignore: "pid,hostname",
        },
      },
    },
  });

  await setupPlatform(app, {
    startContentWorker:
      process.env.CONTENT_WORKER_ENABLED === undefined
        ? true
        : process.env.CONTENT_WORKER_ENABLED === "true",
  });

  // Add tracing middleware for HTTP requests
  app.addHook('onRequest', tracingMiddleware);

  await app.listen({ port: config.PORT, host: "0.0.0.0" });
  app.log.info(
    `TutorPutor Platform running on http://localhost:${config.PORT}`,
  );
  app.log.info(`Metrics available at http://localhost:${config.PORT}/metrics`);
  app.log.info(`Health checks at http://localhost:${config.PORT}/health`);

  // Setup graceful shutdown handlers
  setupGracefulShutdown(app);
}

function setupGracefulShutdown(app: ReturnType<typeof Fastify>) {
  const logger = app.log;

  const shutdown = async (signal: string) => {
    if (isShuttingDown) {
      logger.warn({ signal }, "Shutdown already in progress, ignoring signal");
      return;
    }

    isShuttingDown = true;
    logger.info({ signal }, "Starting graceful shutdown...");

    try {
      // Set timeout for graceful shutdown
      const shutdownTimeout = setTimeout(() => {
        logger.error("Graceful shutdown timeout, forcing exit");
        process.exit(1);
      }, 30000); // 30 seconds timeout

      // Stop accepting new requests
      logger.info("Stopping accepting new requests...");
      await app.close();

      // Close database connections
      if (app.prisma) {
        logger.info("Closing database connections...");
        await app.prisma.$disconnect();
      }

      // Close Redis connections
      if (app.redis) {
        logger.info("Closing Redis connections...");
        await app.redis.quit();
      }

      // Clear timeout
      clearTimeout(shutdownTimeout);

      logger.info("Graceful shutdown completed successfully");
      process.exit(0);
    } catch (error) {
      logger.error({ error }, "Error during graceful shutdown");
      process.exit(1);
    }
  };

  // Handle shutdown signals
  process.on("SIGTERM", () => shutdown("SIGTERM"));
  process.on("SIGINT", () => shutdown("SIGINT"));
  process.on("SIGUSR2", () => shutdown("SIGUSR2")); // nodemon restart
}

bootstrap().catch((err) => {
  // Use structured logging instead of console.error
  const logger = pino({ level: "error" });
  logger.error({ error: err }, "Failed to start server");
  process.exit(1);
});

process.on("unhandledRejection", (reason, promise) => {
  // Use structured logging instead of console.error
  const logger = pino({ level: "error" });
  logger.error({ reason, promise }, "Unhandled Rejection");
  process.exit(1);
});
