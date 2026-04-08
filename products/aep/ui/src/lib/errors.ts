/**
 * Typed error hierarchy for API responses.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/error-handling
 * after validation in AEP and Data Cloud.
 *
 * @doc.type types
 * @doc.purpose Provide structured error types for better error handling
 * @doc.layer frontend
 */

/**
 * Base API error class
 */
export class ApiError extends Error {
  constructor(
    message: string,
    public status?: number,
    public code?: string,
    public details?: unknown
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * Validation error (422)
 */
export class ValidationError extends ApiError {
  constructor(message: string, details?: unknown) {
    super(message, 422, 'VALIDATION_ERROR', details);
    this.name = 'ValidationError';
  }
}

/**
 * Rate limit error (429)
 */
export class RateLimitError extends ApiError {
  constructor(
    message: string,
    public retryAfter?: number
  ) {
    super(message, 429, 'RATE_LIMIT_ERROR');
    this.name = 'RateLimitError';
  }
}

/**
 * Permission denied error (403)
 */
export class PermissionError extends ApiError {
  constructor(message: string, details?: unknown) {
    super(message, 403, 'PERMISSION_DENIED', details);
    this.name = 'PermissionError';
  }
}

/**
 * Conflict error (409)
 */
export class ConflictError extends ApiError {
  constructor(message: string, details?: unknown) {
    super(message, 409, 'CONFLICT', details);
    this.name = 'ConflictError';
  }
}

/**
 * Not found error (404)
 */
export class NotFoundError extends ApiError {
  constructor(message: string, resource?: string) {
    super(message, 404, 'NOT_FOUND', { resource });
    this.name = 'NotFoundError';
  }
}

/**
 * Unauthorized error (401)
 */
export class UnauthorizedError extends ApiError {
  constructor(message: string = 'Authentication required') {
    super(message, 401, 'UNAUTHORIZED');
    this.name = 'UnauthorizedError';
  }
}

/**
 * Network error (fetch failed)
 */
export class NetworkError extends ApiError {
  constructor(message: string = 'Network error occurred') {
    super(message, undefined, 'NETWORK_ERROR');
    this.name = 'NetworkError';
  }
}

/**
 * Parse error from API response
 */
export function parseErrorResponse(status: number, data: unknown): Error {
  if (typeof data === 'string') {
    return new ApiError(data, status);
  }
  
  const details = typeof data === 'object' && data !== null ? data as Record<string, unknown> : undefined;
  
  if (status === 429) {
    return new RateLimitError(
      details?.message as string ?? 'Rate limit exceeded',
      details?.retryAfter as number | undefined
    );
  }
  if (status === 422) {
    return new ValidationError(
      details?.message as string ?? 'Validation failed',
      details
    );
  }
  if (status === 403) {
    return new PermissionError(
      details?.message as string ?? 'Permission denied',
      details
    );
  }
  if (status === 409) {
    return new ConflictError(
      details?.message as string ?? 'Resource conflict',
      details
    );
  }
  if (status === 404) {
    return new NotFoundError(
      details?.message as string ?? 'Resource not found',
      details?.resource as string | undefined
    );
  }
  if (status === 401) {
    return new UnauthorizedError(details?.message as string);
  }
  
  return new ApiError(
    details?.message as string ?? `HTTP ${status}`,
    status
  );
}
