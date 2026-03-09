import Fastify from "fastify";
import pino from "pino";
import "./types/fastify.js";
import { setupPlatform } from "./setup.js";
import { getConfig } from "./config/config.js";
import { authMiddleware } from "./auth/index.js";

const config = getConfig();

async function bootstrap() {
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

  await app.listen({ port: config.PORT, host: "0.0.0.0" });
  app.log.info(`TutorPutor Platform running on http://localhost:${config.PORT}`);
  app.log.info(`Metrics available at http://localhost:${config.PORT}/metrics`);
  app.log.info(`Health checks at http://localhost:${config.PORT}/health`);
}

bootstrap().catch((err) => {
  // Use structured logging instead of console.error
  const logger = pino({ level: 'error' });
  logger.error({ error: err }, "Failed to start server");
  process.exit(1);
});

process.on("unhandledRejection", (reason, promise) => {
  // Use structured logging instead of console.error
  const logger = pino({ level: 'error' });
  logger.error({ reason, promise }, "Unhandled Rejection");
  process.exit(1);
});
