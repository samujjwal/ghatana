import { describe, expect, it, vi } from "vitest";
import type { Logger } from "pino";

const { buildContentWorkerConfig, initializeContentWorker } = await import(
  "./startup/content-worker-init.js"
);

function makeLogger(): Logger {
  return {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
  } as unknown as Logger;
}

describe("buildContentWorkerConfig", () => {
  it("parses redis credentials and db index from the URL", () => {
    const logger = makeLogger();
    const prisma = {} as never;

    const config = buildContentWorkerConfig(
      "redis://:secret@cache.internal:6380/4",
      "grpc.internal:50051",
      true,
      logger,
      prisma,
    );

    expect(config).toMatchObject({
      redis: {
        host: "cache.internal",
        port: 6380,
        password: "secret",
        db: 4,
      },
      grpc: {
        serverAddress: "grpc.internal:50051",
        useTls: true,
      },
      logger,
      prisma,
    });
  });
});

describe("initializeContentWorker", () => {
  it("returns null when worker startup is disabled", async () => {
    const logger = makeLogger();

    const result = await initializeContentWorker({
      shouldStart: false,
      redisUrl: "redis://localhost:6379",
      grpcServerAddress: "localhost:50051",
      grpcUseTls: false,
      logger,
      prisma: {} as never,
    });

    expect(result).toBeNull();
    expect(logger.info).toHaveBeenCalledWith("Content worker startup disabled");
  });

  it("returns a healthy worker and logs success", async () => {
    const logger = makeLogger();
    const worker = {
      healthCheck: vi.fn().mockResolvedValue(true),
      close: vi.fn().mockResolvedValue(undefined),
    };

    const result = await initializeContentWorker({
      shouldStart: true,
      redisUrl: "redis://localhost:6379",
      grpcServerAddress: "localhost:50051",
      grpcUseTls: false,
      logger,
      prisma: {} as never,
      workerFactory: () => worker,
    });

    expect(result).toBe(worker);
    expect(worker.healthCheck).toHaveBeenCalledTimes(1);
    expect(logger.info).toHaveBeenCalledWith(
      "Content worker initialized and healthy",
    );
  });

  it("falls back to degraded mode when the worker is optional", async () => {
    const logger = makeLogger();

    const result = await initializeContentWorker({
      shouldStart: true,
      redisUrl: "redis://localhost:6379",
      grpcServerAddress: "localhost:50051",
      grpcUseTls: false,
      logger,
      prisma: {} as never,
      requireContentWorker: false,
      workerFactory: () => {
        throw new Error("redis unavailable");
      },
    });

    expect(result).toBeNull();
    expect(logger.error).toHaveBeenCalled();
    expect(logger.warn).toHaveBeenCalledWith(
      "Continuing without content worker - content generation will be unavailable",
    );
  });

  it("fails startup when the worker is required", async () => {
    const logger = makeLogger();

    await expect(
      initializeContentWorker({
        shouldStart: true,
        redisUrl: "redis://localhost:6379",
        grpcServerAddress: "localhost:50051",
        grpcUseTls: false,
        logger,
        prisma: {} as never,
        requireContentWorker: true,
        workerFactory: () => {
          throw new Error("grpc handshake failed");
        },
      }),
    ).rejects.toThrow(
      "Content worker required but failed to initialize: grpc handshake failed",
    );
  });
});
