/**
 * Global error handling middleware with Sentry integration and structured logging.
 *
 * <p><b>Purpose</b><br>
 * Provides centralized error handling for all Express routes, capturing exceptions
 * in Sentry, logging structured error details, tracking error metrics, and returning
 * consistent error responses to clients with appropriate status codes.
 *
 * <p><b>Error Handling Strategy</b><br>
 * - Validation errors: 400 Bad Request with field-level details
 * - HTTP errors: Status code from error object (401, 403, 404, etc.)
 * - Unexpected errors: 500 Internal Server Error with generic message
 * - Production: Hides internal error details from clients
 * - Development: Includes stack traces for debugging
 *
 * <p><b>Integration</b><br>
 * Captures exceptions in Sentry for monitoring, logs errors with request context,
 * increments errorCount metric, and builds consistent JSON error responses.
 *
 * <p><b>Error Response Format</b><br>
 * <pre>{@code
 * {
 *   "error": "Human-readable error message",
 *   "code": "validation_error | auth_failed | not_found",
 *   "details": { ... } // Field-level validation errors or debug info
 * }
 * }</pre>
 *
 * @doc.type middleware
 * @doc.purpose Centralized error handling with monitoring and logging
 * @doc.layer backend
 * @doc.pattern Middleware
 */
import type { ErrorRequestHandler, Request, Response, NextFunction } from 'express';
import { captureException } from '../utils/sentry';
import { logError } from '../utils/logger';
import * as metrics from '../utils/metrics';
import { RequestValidationError } from './validation.middleware';

export interface HttpError extends Error {
  status?: number;
  code?: string;
  expose?: boolean;
  details?: unknown;
}

function buildErrorResponse(
  err: HttpError | RequestValidationError,
  req: Request
): { error: string; code?: string; details?: unknown } {
  if (err instanceof RequestValidationError) {
    return {
      error: err.message,
      code: 'validation_error',
      details: err.details,
    };
  }

  return {
    error:
      process.env.NODE_ENV === 'development' || err.expose
        ? err.message
        : 'Internal server error',
    code: err.code,
  };
}

export const errorHandler: ErrorRequestHandler = (
  err: HttpError,
  req: Request,
  res: Response,
  _next: NextFunction
) => {
  const status = err instanceof RequestValidationError ? 400 : err.status ?? 500;

  logError(err, {
    path: req.path,
    method: req.method,
    userId: (req as any).userId,
  });

  captureException(err, {
    path: req.path,
    method: req.method,
    query: req.query,
    body: req.body,
  });

  metrics.applicationErrors.inc({
    type: err.name || 'unknown',
    severity: status >= 500 ? 'high' : 'medium',
  });

  const payload = buildErrorResponse(err, req);

  if (process.env.NODE_ENV === 'development' && status >= 500) {
    (payload as any).stack = err.stack;
  }

  res.status(status).json(payload);
};

