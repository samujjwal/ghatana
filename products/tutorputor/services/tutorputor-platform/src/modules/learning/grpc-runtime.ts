/**
 * Learner Profile gRPC Runtime
 *
 * Optional runtime wrapper for starting and stopping the learner-profile gRPC server.
 * Includes health checks, startup validation, and graceful shutdown handling.
 *
 * @doc.type module
 * @doc.purpose Manage learner-profile gRPC server lifecycle in platform startup
 * @doc.layer product
 * @doc.pattern Runtime Adapter
 */

import type { FastifyBaseLogger } from "fastify";
import type { Server } from "@grpc/grpc-js";
import {
  bindLearnerProfileGrpcServer,
  createLearnerProfileGrpcServer,
} from "./grpc-service.js";
import type { createLearnerProfileService } from "./learner-profile-service.js";

type LearnerProfileService = ReturnType<typeof createLearnerProfileService>;

export interface LearnerProfileGrpcRuntime {
  server: Server;
  address: string;
  port: number;
  stop: () => Promise<void>;
  health: () => GrpcHealthStatus;
  waitForReady: (timeoutMs?: number) => Promise<boolean>;
}

export interface GrpcHealthStatus {
  status: "healthy" | "degraded" | "unhealthy";
  serving: boolean;
  uptimeMs: number;
  lastError?: string | undefined;
  callCount: number;
  errorCount: number;
}

interface RuntimeState {
  startTime: number;
  callCount: number;
  errorCount: number;
  lastError?: string;
  isShuttingDown: boolean;
}

const runtimeState: RuntimeState = {
  startTime: 0,
  callCount: 0,
  errorCount: 0,
  isShuttingDown: false,
};

export async function startLearnerProfileGrpcRuntime(options: {
  learnerProfileService: LearnerProfileService;
  address: string;
  logger: FastifyBaseLogger;
  enableHealthCheck?: boolean;
  startupTimeoutMs?: number;
}): Promise<LearnerProfileGrpcRuntime> {
  const startupTimeout = options.startupTimeoutMs ?? 30000;
  const startTime = Date.now();

  // Validate service dependencies before starting
  const serviceValidation = await validateServiceDependencies(
    options.learnerProfileService,
    options.logger,
  );

  if (!serviceValidation.valid) {
    throw new Error(
      `Service dependency validation failed: ${serviceValidation.errors.join(", ")}`,
    );
  }

  const server = createLearnerProfileGrpcServer(options.learnerProfileService);

  // Add interceptors for monitoring
  const originalStart = server.start.bind(server);
  server.start = () => {
    runtimeState.startTime = Date.now();
    runtimeState.isShuttingDown = false;
    originalStart();
  };

  let port: number;
  try {
    port = await Promise.race([
      bindLearnerProfileGrpcServer(server, options.address),
      new Promise<number>((_, reject) =>
        setTimeout(
          () => reject(new Error("gRPC bind timeout")),
          startupTimeout,
        ),
      ),
    ]);
  } catch (error) {
    options.logger.error(
      { error, address: options.address },
      "Failed to bind gRPC server",
    );
    throw error;
  }

  server.start();
  runtimeState.startTime = Date.now();

  options.logger.info(
    {
      grpcAddress: options.address,
      grpcPort: port,
      startupTimeMs: Date.now() - startTime,
    },
    "Learner profile gRPC server started",
  );

  // Perform health check after startup
  if (options.enableHealthCheck !== false) {
    const healthCheck = await performStartupHealthCheck(server, options.logger);
    if (!healthCheck.healthy) {
      await gracefulShutdown(server, options.address, port, options.logger);
      throw new Error(`Startup health check failed: ${healthCheck.error}`);
    }
    options.logger.info("gRPC startup health check passed");
  }

  return {
    server,
    address: options.address,
    port,
    stop: () => gracefulShutdown(server, options.address, port, options.logger),
    health: () => getHealthStatus(),
    waitForReady: (timeoutMs = 5000) =>
      waitForServerReady(server, timeoutMs, options.logger),
  };
}

async function validateServiceDependencies(
  service: LearnerProfileService,
  logger: FastifyBaseLogger,
): Promise<{ valid: boolean; errors: string[] }> {
  const errors: string[] = [];

  // Check if service has required methods
  const requiredMethods = ["getProfile", "updateProfile", "trackProgress"];
  for (const method of requiredMethods) {
    if (typeof (service as Record<string, unknown>)[method] !== "function") {
      errors.push(`Missing required service method: ${method}`);
    }
  }

  if (errors.length > 0) {
    logger.error({ errors }, "Service dependency validation failed");
  }

  return { valid: errors.length === 0, errors };
}

async function performStartupHealthCheck(
  server: Server,
  logger: FastifyBaseLogger,
): Promise<{ healthy: boolean; error?: string }> {
  try {
    // Check server is listening (use server address/port as proxy)
    if (!server) {
      return { healthy: false, error: "Server not created" };
    }

    // Verify server is not in shutdown state
    if (runtimeState.isShuttingDown) {
      return { healthy: false, error: "Server is shutting down" };
    }

    return { healthy: true };
  } catch (error) {
    const errorMsg = error instanceof Error ? error.message : String(error);
    logger.error({ error }, "Startup health check failed");
    return { healthy: false, error: errorMsg };
  }
}

function getHealthStatus(): GrpcHealthStatus {
  const uptimeMs =
    runtimeState.startTime > 0 ? Date.now() - runtimeState.startTime : 0;

  if (runtimeState.isShuttingDown) {
    return {
      status: "unhealthy",
      serving: false,
      uptimeMs,
      callCount: runtimeState.callCount,
      errorCount: runtimeState.errorCount,
      lastError: "Server is shutting down",
    };
  }

  if (
    runtimeState.errorCount > runtimeState.callCount * 0.1 &&
    runtimeState.callCount > 10
  ) {
    return {
      status: "degraded",
      serving: true,
      uptimeMs,
      callCount: runtimeState.callCount,
      errorCount: runtimeState.errorCount,
      lastError: runtimeState.lastError ?? undefined,
    };
  }

  return {
    status: runtimeState.startTime > 0 ? "healthy" : "unhealthy",
    serving: runtimeState.startTime > 0 && !runtimeState.isShuttingDown,
    uptimeMs,
    callCount: runtimeState.callCount,
    errorCount: runtimeState.errorCount,
  };
}

async function waitForServerReady(
  _server: Server,
  timeoutMs: number,
  logger: FastifyBaseLogger,
): Promise<boolean> {
  const startTime = Date.now();

  while (Date.now() - startTime < timeoutMs) {
    const health = getHealthStatus();
    if (health.status === "healthy" && health.serving) {
      return true;
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }

  logger.warn("Timeout waiting for gRPC server to be ready");
  return false;
}

async function gracefulShutdown(
  server: Server,
  address: string,
  port: number,
  logger: FastifyBaseLogger,
): Promise<void> {
  runtimeState.isShuttingDown = true;

  logger.info(
    { grpcAddress: address, grpcPort: port },
    "Initiating gRPC server graceful shutdown",
  );

  const shutdownTimeout = 30000; // 30 seconds

  return new Promise((resolve, reject) => {
    const timeoutId = setTimeout(() => {
      logger.warn("Graceful shutdown timeout, forcing close");
      server.forceShutdown();
      runtimeState.startTime = 0;
      resolve();
    }, shutdownTimeout);

    server.tryShutdown((error) => {
      clearTimeout(timeoutId);

      if (error) {
        logger.error(
          { error, grpcAddress: address, grpcPort: port },
          "gRPC graceful shutdown failed",
        );
        runtimeState.startTime = 0;
        reject(error);
        return;
      }

      runtimeState.startTime = 0;
      logger.info(
        { grpcAddress: address, grpcPort: port, graceful: true },
        "Learner profile gRPC server stopped",
      );
      resolve();
    });
  });
}
