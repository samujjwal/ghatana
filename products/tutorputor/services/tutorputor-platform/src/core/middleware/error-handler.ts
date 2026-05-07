/**
 * Error Handling Middleware
 *
 * Centralized error handling middleware that converts canonical errors to proper HTTP responses
 * and provides consistent error logging across the TutorPutor platform.
 *
 * @doc.type middleware
 * @doc.purpose Centralized error handling
 * @doc.layer core
 * @doc.pattern Error Handling
 */

import type { FastifyError, FastifyReply, FastifyRequest } from "fastify";
import type { Logger } from "pino";
import { isDomainError, type DomainError } from "@tutorputor/core/errors";
import { createErrorEnvelope } from "../http/error-envelope.js";

/**
 * Error handler middleware for Fastify
 */
export function errorHandler(
  error: FastifyError,
  request: FastifyRequest,
  reply: FastifyReply,
): void {
  const logger = request.log as Logger;

  if (isDomainError(error)) {
    handleCanonicalError(error, reply, logger, request);
    return;
  }

  if (error.validation) {
    handleValidationError(error, reply, logger, request);
    return;
  }

  handleUnknownError(error, reply, logger, request);
}

function handleCanonicalError(
  error: DomainError,
  reply: FastifyReply,
  logger: Logger,
  request: FastifyRequest,
): void {
  logger.warn(
    {
      err: error,
      requestId: request.id,
      path: request.url,
      method: request.method,
      code: error.code,
    },
    "Domain error occurred",
  );

  reply.code(error.statusCode).send(createErrorEnvelope({
    code: error.code,
    message: error.message,
    details: error.details,
    statusCode: error.statusCode,
  }, request));
}

function handleValidationError(
  error: FastifyError,
  reply: FastifyReply,
  logger: Logger,
  request: FastifyRequest,
): void {
  logger.warn(
    {
      err: error,
      requestId: request.id,
      path: request.url,
      validation: error.validation,
    },
    "Request validation failed",
  );

  reply.code(400).send(createErrorEnvelope({
    code: "VALIDATION_ERROR",
    message: "Request validation failed",
    details: { validation: error.validation },
    statusCode: 400,
  }, request));
}

function handleUnknownError(
  error: FastifyError,
  reply: FastifyReply,
  logger: Logger,
  request: FastifyRequest,
): void {
  logger.error(
    {
      err: error,
      requestId: request.id,
      path: request.url,
    },
    "Unexpected error occurred",
  );

  const isDevelopment = process.env.NODE_ENV === "development";
  const statusCode = error.statusCode || 500;
  reply.code(statusCode).send(createErrorEnvelope({
    code: "INTERNAL_ERROR",
    message: "An internal error occurred",
    statusCode,
    ...(isDevelopment ? { details: { stack: error.stack } } : {}),
  }, request));
}

export function asyncHandler<T extends FastifyRequest>(
  handler: (request: T, reply: FastifyReply) => Promise<unknown>,
) {
  return async (request: T, reply: FastifyReply): Promise<void> => {
    try {
      await handler(request, reply);
    } catch (error) {
      errorHandler(error as FastifyError, request, reply);
    }
  };
}

export class ErrorMonitor {
  private errorCounts: Map<string, number> = new Map();
  private lastReset = Date.now();
  private readonly resetInterval = 60000;

  constructor(private readonly logger: Logger) {
    setInterval(() => this.resetCounters(), this.resetInterval);
  }

  recordError(error: DomainError): void {
    const key = error.code;
    const currentCount = this.errorCounts.get(key) ?? 0;
    const nextCount = currentCount + 1;
    this.errorCounts.set(key, nextCount);

    const rate = this.calculateErrorRate(key);
    if (rate > 10) {
      this.logger.warn(
        {
          errorKey: key,
          rate,
          count: nextCount,
        },
        "High error rate detected",
      );
    }
  }

  private calculateErrorRate(key: string): number {
    const count = this.errorCounts.get(key) ?? 0;
    const timeSinceReset = Date.now() - this.lastReset;
    return (count / timeSinceReset) * this.resetInterval;
  }

  private resetCounters(): void {
    this.errorCounts.clear();
    this.lastReset = Date.now();
  }

  getErrorStats(): Record<string, { count: number; rate: number }> {
    const stats: Record<string, { count: number; rate: number }> = {};

    for (const [key, count] of this.errorCounts.entries()) {
      stats[key] = {
        count,
        rate: this.calculateErrorRate(key),
      };
    }

    return stats;
  }
}
