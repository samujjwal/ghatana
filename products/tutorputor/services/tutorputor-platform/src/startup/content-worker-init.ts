import type { Logger } from "pino";
import { PrismaClient } from "@tutorputor/core/db";

import {
  ContentWorkerService,
  type ContentWorkerConfig,
} from "../workers/content/index.js";

export interface ContentWorkerController {
  healthCheck: () => Promise<boolean>;
  close: () => Promise<void>;
}

interface InitializeContentWorkerOptions {
  shouldStart: boolean;
  redisUrl: string;
  grpcServerAddress: string;
  grpcUseTls: boolean;
  logger: Logger;
  prisma: PrismaClient;
  requireContentWorker?: boolean;
  workerFactory?: (config: ContentWorkerConfig) => ContentWorkerController;
}

export function buildContentWorkerConfig(
  redisUrl: string,
  grpcServerAddress: string,
  grpcUseTls: boolean,
  logger: Logger,
  prisma: PrismaClient,
): ContentWorkerConfig {
  const redisUrlObj = new URL(redisUrl);
  const redisDb = redisUrlObj.pathname?.slice(1);

  return {
    redis: {
      host: redisUrlObj.hostname,
      port: parseInt(redisUrlObj.port || "6379", 10),
      ...(redisUrlObj.password ? { password: redisUrlObj.password } : {}),
      db: redisDb ? parseInt(redisDb, 10) || 0 : 0,
    },
    grpc: {
      serverAddress: grpcServerAddress,
      useTls: grpcUseTls,
    },
    logger,
    prisma,
  };
}

export async function initializeContentWorker({
  shouldStart,
  redisUrl,
  grpcServerAddress,
  grpcUseTls,
  logger,
  prisma,
  requireContentWorker = process.env.REQUIRE_CONTENT_WORKER === "true",
  workerFactory = (config) => new ContentWorkerService(config),
}: InitializeContentWorkerOptions): Promise<ContentWorkerController | null> {
  if (!shouldStart) {
    logger.info("Content worker startup disabled");
    return null;
  }

  try {
    const worker = workerFactory(
      buildContentWorkerConfig(
        redisUrl,
        grpcServerAddress,
        grpcUseTls,
        logger,
        prisma,
      ),
    );

    await worker.healthCheck();
    logger.info("Content worker initialized and healthy");
    return worker;
  } catch (error) {
    logger.error({ err: error }, "Content worker initialization failed");

    if (requireContentWorker) {
      const message =
        error instanceof Error ? error.message : String(error);
      throw new Error(
        `Content worker required but failed to initialize: ${message}`,
      );
    }

    logger.warn(
      "Continuing without content worker - content generation will be unavailable",
    );
    return null;
  }
}
