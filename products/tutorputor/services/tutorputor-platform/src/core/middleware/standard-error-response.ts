/**
 * Standard Error Response Middleware
 *
 * Ensures all API modules return identical error envelope structure:
 * { code: string, message: string, requestId: string, details?: any }
 *
 * @doc.type middleware
 * @doc.purpose Standardize error response shapes across all API modules
 * @doc.layer core
 * @doc.pattern Middleware
 */
import type { FastifyRequest, FastifyReply } from "fastify";

export interface StandardErrorResponse {
  code: string;
  message: string;
  requestId: string;
  details?: unknown;
  timestamp: string;
}

export class StandardError extends Error {
  constructor(
    public code: string,
    message: string,
    public statusCode: number = 500,
    public details?: unknown,
  ) {
    super(message);
    this.name = "StandardError";
  }
}

/**
 * Standard error response middleware
 * Catches errors and formats them consistently
 */
export async function standardErrorResponseMiddleware(
  error: Error,
  request: FastifyRequest,
  reply: FastifyReply,
): Promise<void> {
  const requestId = (request.headers["x-request-id"] as string) || generateRequestId();

  // If it's already a StandardError, use it directly
  if (error instanceof StandardError) {
    const response: StandardErrorResponse = {
      code: error.code,
      message: error.message,
      requestId,
      details: error.details,
      timestamp: new Date().toISOString(),
    };

    reply.status(error.statusCode).send(response);
    return;
  }

  // Handle common error types
  if (error.name === "ValidationError") {
    const response: StandardErrorResponse = {
      code: "VALIDATION_ERROR",
      message: error.message,
      requestId,
      timestamp: new Date().toISOString(),
    };
    reply.status(400).send(response);
    return;
  }

  if (error.name === "UnauthorizedError") {
    const response: StandardErrorResponse = {
      code: "UNAUTHORIZED",
      message: "Authentication required",
      requestId,
      timestamp: new Date().toISOString(),
    };
    reply.status(401).send(response);
    return;
  }

  if (error.name === "ForbiddenError") {
    const response: StandardErrorResponse = {
      code: "FORBIDDEN",
      message: error.message || "Access denied",
      requestId,
      timestamp: new Date().toISOString(),
    };
    reply.status(403).send(response);
    return;
  }

  if (error.name === "NotFoundError") {
    const response: StandardErrorResponse = {
      code: "NOT_FOUND",
      message: error.message || "Resource not found",
      requestId,
      timestamp: new Date().toISOString(),
    };
    reply.status(404).send(response);
    return;
  }

  // Generic error fallback
  const response: StandardErrorResponse = {
    code: "INTERNAL_ERROR",
    message: "An unexpected error occurred",
    requestId,
    timestamp: new Date().toISOString(),
  };

  // Log the error for debugging
  request.log.error({ err: error, requestId }, "Unhandled error");

  reply.status(500).send(response);
}

/**
 * Generate a unique request ID
 */
function generateRequestId(): string {
  return `req_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
}

/**
 * Create a standardized error response
 */
export function createStandardErrorResponse(
  code: string,
  message: string,
  statusCode: number = 500,
  details?: unknown,
): StandardErrorResponse {
  return {
    code,
    message,
    requestId: generateRequestId(),
    details,
    timestamp: new Date().toISOString(),
  };
}

/**
 * Pre-request hook to add request ID
 */
export async function addRequestIdHook(
  request: FastifyRequest,
  reply: FastifyReply,
): Promise<void> {
  const requestId = (request.headers["x-request-id"] as string) || generateRequestId();
  (request as any).requestId = requestId;
  reply.header("x-request-id", requestId);
}
