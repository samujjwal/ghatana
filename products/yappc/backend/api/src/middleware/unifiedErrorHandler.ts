/**
 * Unified Error Handler
 * 
 * Provides consistent error handling across all YAPPC backend services.
 * This middleware ensures all errors are formatted according to the
 * StandardApiResponse format.
 * 
 * @doc.type middleware
 * @doc.purpose Unified error handling for all services
 * @doc.layer platform
 * @doc.pattern Error Handling
 */

// Type definitions for Fastify compatibility
type FastifyError = Error & {
  statusCode?: number;
  code?: string;
};

interface FastifyRequest {
  method: string;
  url: string;
  id: string;
}

interface FastifyReply {
  status: (code: number) => FastifyReply;
  send: (payload: unknown) => void;
  header: (name: string, value: string) => FastifyReply;
}

// Import standard error response utilities
import { createErrorResponse, ErrorCodes } from '../../../../platform/contracts/api/StandardApiResponse';

/**
 * Generate request ID for tracing
 */
function generateRequestId(): string {
  return `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Map Fastify errors to standard error codes
 */
function mapFastifyErrorToCode(error: FastifyError): string {
  const statusCode = error.statusCode || 500;
  
  switch (statusCode) {
    case 400: return ErrorCodes.BAD_REQUEST;
    case 401: return ErrorCodes.UNAUTHORIZED;
    case 403: return ErrorCodes.FORBIDDEN;
    case 404: return ErrorCodes.NOT_FOUND;
    case 409: return ErrorCodes.CONFLICT;
    case 422: return ErrorCodes.VALIDATION_ERROR;
    case 429: return ErrorCodes.RATE_LIMITED;
    case 503: return ErrorCodes.SERVICE_UNAVAILABLE;
    case 504: return ErrorCodes.TIMEOUT;
    default: return ErrorCodes.INTERNAL_ERROR;
  }
}

/**
 * Global error handler middleware
 * Use this in all Fastify services
 */
export function unifiedErrorHandler(
  error: FastifyError,
  request: FastifyRequest,
  reply: FastifyReply
): void {
  const requestId = (request as any).requestId || generateRequestId();
  
  // Log error with structured format
  console.error('[ERROR]', {
    requestId,
    method: request.method,
    url: request.url,
    error: error.message,
    code: error.code,
    statusCode: error.statusCode,
    stack: process.env.NODE_ENV === 'development' ? error.stack : undefined,
  });
  
  // Determine status code
  const statusCode = error.statusCode || 500;
  
  // Map to standard error code
  const code = mapFastifyErrorToCode(error);
  
  // Create standardized error response
  const errorResponse = createErrorResponse(
    statusCode,
    error.name || 'Error',
    error.message || 'An unexpected error occurred',
    code,
    requestId,
    process.env.NODE_ENV === 'development' ? { stack: error.stack } : undefined
  );
  
  // Send response
  reply.status(statusCode).send(errorResponse);
}

/**
 * Not found handler
 * For 404 routes
 */
export function notFoundHandler(
  request: FastifyRequest,
  reply: FastifyReply
): void {
  const requestId = (request as any).requestId || generateRequestId();
  
  const errorResponse = createErrorResponse(
    404,
    'Not Found',
    `Route ${request.method} ${request.url} not found`,
    ErrorCodes.NOT_FOUND,
    requestId,
    { availableRoutes: 'See API documentation at /docs' }
  );
  
  reply.status(404).send(errorResponse);
}

/**
 * Request ID middleware
 * Attaches unique request ID to each request for tracing
 */
export function requestIdMiddleware(
  request: FastifyRequest,
  reply: FastifyReply,
  done: () => void
): void {
  (request as any).requestId = generateRequestId();
  reply.header('X-Request-Id', (request as any).requestId);
  done();
}

/**
 * Async error wrapper
 * Wraps async route handlers to catch errors automatically
 */
export function asyncHandler<T>(
  fn: (request: FastifyRequest, reply: FastifyReply) => Promise<T>
): (request: FastifyRequest, reply: FastifyReply) => Promise<void> {
  return async (request: FastifyRequest, reply: FastifyReply): Promise<void> => {
    try {
      await fn(request, reply);
    } catch (error) {
      unifiedErrorHandler(error as FastifyError, request, reply);
    }
  };
}
