/**
 * Standard Error Response Middleware
 *
 * Ensures all API modules return identical error envelope structure:
 * { code: string, message: string, requestId: string, details?: unknown }
 *
 * @doc.type middleware
 * @doc.purpose Standardize error response shapes across all API modules
 * @doc.layer core
 * @doc.pattern Middleware
 */
import type { FastifyRequest, FastifyReply } from "fastify";
import {
  createErrorEnvelope,
  getRequestTraceId,
  type CanonicalErrorEnvelope,
} from "../http/error-envelope.js";

export type StandardErrorResponse = CanonicalErrorEnvelope;
const requestIds = new WeakMap<FastifyRequest, string>();

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
    const response = createErrorEnvelope({
      code: error.code,
      message: error.message,
      details: error.details,
      statusCode: error.statusCode,
      traceId: requestId,
    });

    reply.status(error.statusCode).send(response);
    return;
  }

  // Handle common error types
  if (error.name === "ValidationError") {
    const response = createErrorEnvelope({
      code: "VALIDATION_ERROR",
      message: error.message,
      statusCode: 400,
      traceId: requestId,
    });
    reply.status(400).send(response);
    return;
  }

  if (error.name === "UnauthorizedError") {
    const response = createErrorEnvelope({
      code: "UNAUTHORIZED",
      message: "Authentication required",
      statusCode: 401,
      traceId: requestId,
    });
    reply.status(401).send(response);
    return;
  }

  if (error.name === "ForbiddenError") {
    const response = createErrorEnvelope({
      code: "FORBIDDEN",
      message: error.message || "Access denied",
      statusCode: 403,
      traceId: requestId,
    });
    reply.status(403).send(response);
    return;
  }

  if (error.name === "NotFoundError") {
    const response = createErrorEnvelope({
      code: "NOT_FOUND",
      message: error.message || "Resource not found",
      statusCode: 404,
      traceId: requestId,
    });
    reply.status(404).send(response);
    return;
  }

  // Generic error fallback
  const response = createErrorEnvelope({
    code: "INTERNAL_ERROR",
    message: "An unexpected error occurred",
    statusCode: 500,
    traceId: requestId,
  });

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
    ...createErrorEnvelope({ code, message, statusCode, details }),
  };
}

/**
 * Pre-request hook to add request ID
 */
export async function addRequestIdHook(
  request: FastifyRequest,
  reply: FastifyReply,
): Promise<void> {
  const requestId = getRequestTraceId(request);
  requestIds.set(request, requestId);
  reply.header("x-request-id", requestId);
}

export function getStoredRequestId(request: FastifyRequest): string | undefined {
  return requestIds.get(request);
}
