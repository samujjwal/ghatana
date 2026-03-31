/**
 * Standardized Error Handling Utility
 * 
 * Provides consistent error handling patterns across all services
 * with proper error types, logging, and response formatting.
 * 
 * @doc.type utility
 * @doc.purpose Standardized error handling patterns
 * @doc.layer platform
 */

import { createStandaloneLogger } from './logger';
import {
  DomainError as BaseDomainError,
  ValidationError as BaseValidationError,
  NotFoundError as BaseNotFoundError,
  AuthorizationError as BaseUnauthorizedError,
  ForbiddenError as BaseForbiddenError,
  ConflictError as BaseConflictError,
  RateLimitError as BaseRateLimitError,
  ServiceUnavailableError as BaseExternalServiceError,
} from "./errors/index.js";

const logger = createStandaloneLogger({ component: 'ErrorHandling' });

// =============================================================================
// Error Types
// =============================================================================

export class DomainError extends BaseDomainError {
  constructor(
    message: string,
    code: string,
    statusCode: number = 400,
    details?: Record<string, unknown>,
  ) {
    super(code, message, statusCode, details);
  }
}

export class ValidationError extends BaseValidationError {
  constructor(message: string, details?: Record<string, unknown>) {
    super(message, undefined, details);
  }
}

export class NotFoundError extends BaseNotFoundError {
  constructor(resource: string, identifier?: string) {
    super(resource, identifier ?? resource, identifier ? undefined : { resource });
  }
}

export class UnauthorizedError extends BaseUnauthorizedError {}
export class ForbiddenError extends BaseForbiddenError {}
export class ConflictError extends BaseConflictError {}

export class RateLimitError extends BaseRateLimitError {
  constructor(message: string = 'Rate limit exceeded', retryAfter?: number) {
    super(message, retryAfter !== undefined ? { retryAfter } : undefined);
  }
}

export class ExternalServiceError extends BaseExternalServiceError {
  constructor(service: string, message: string, details?: Record<string, unknown>) {
    super(`External service error: ${service} - ${message}`, {
      service,
      ...details,
    });
  }
}

export class DatabaseError extends DomainError {
  constructor(message: string, details?: Record<string, unknown>) {
    super(message, 'DATABASE_ERROR', 500, details);
    this.name = 'DatabaseError';
  }
}

// =============================================================================
// Error Handler
// =============================================================================

export interface ErrorResponse {
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
    requestId?: string;
    timestamp: string;
  };
}

/**
 * Handles errors and returns standardized error response
 */
export function handleError(
  error: unknown,
  context?: { requestId?: string; userId?: string; tenantId?: string },
): { statusCode: number; body: ErrorResponse } {
  // Log error with context
  logger.error({
    message: 'Error occurred',
    error: error instanceof Error ? error.message : String(error),
    stack: error instanceof Error ? error.stack : undefined,
    ...context,
  });

  // Handle domain errors
  if (error instanceof DomainError) {
    return {
      statusCode: error.statusCode,
      body: {
        error: {
          code: error.code,
          message: error.message,
          details: error.details,
          requestId: context?.requestId,
          timestamp: new Date().toISOString(),
        },
      },
    };
  }

  // Handle Prisma errors
  if (error && typeof error === 'object' && 'code' in error) {
    const prismaError = error as { code: string; meta?: Record<string, unknown> };
    
    if (prismaError.code === 'P2002') {
      return {
        statusCode: 409,
        body: {
          error: {
            code: 'UNIQUE_CONSTRAINT_VIOLATION',
            message: 'A record with this value already exists',
            details: prismaError.meta,
            requestId: context?.requestId,
            timestamp: new Date().toISOString(),
          },
        },
      };
    }

    if (prismaError.code === 'P2025') {
      return {
        statusCode: 404,
        body: {
          error: {
            code: 'RECORD_NOT_FOUND',
            message: 'Record not found',
            details: prismaError.meta,
            requestId: context?.requestId,
            timestamp: new Date().toISOString(),
          },
        },
      };
    }

    return {
      statusCode: 500,
      body: {
        error: {
          code: 'DATABASE_ERROR',
          message: 'Database operation failed',
          details: { prismaCode: prismaError.code },
          requestId: context?.requestId,
          timestamp: new Date().toISOString(),
        },
      },
    };
  }

  // Handle generic errors
  return {
    statusCode: 500,
    body: {
      error: {
        code: 'INTERNAL_SERVER_ERROR',
        message: error instanceof Error ? error.message : 'An unexpected error occurred',
        requestId: context?.requestId,
        timestamp: new Date().toISOString(),
      },
    },
  };
}

/**
 * Wraps an async function with error handling
 */
export function withErrorHandling<T extends (...args: any[]) => Promise<unknown>>(
  fn: T,
  context?: { component?: string },
): T {
  return (async (...args: Parameters<T>) => {
    try {
      return await fn(...args);
    } catch (error) {
      logger.error({
        message: 'Error in wrapped function',
        component: context?.component,
        error: error instanceof Error ? error.message : String(error),
        stack: error instanceof Error ? error.stack : undefined,
      });
      throw error;
    }
  }) as T;
}

/**
 * Validates required fields and throws ValidationError if missing
 */
export function validateRequired(
  data: Record<string, unknown>,
  fields: string[],
): void {
  const missing = fields.filter(field => {
    const value = data[field];
    return value === undefined || value === null || value === '';
  });

  if (missing.length > 0) {
    throw new ValidationError('Missing required fields', { missing });
  }
}

/**
 * Validates field types
 */
export function validateTypes(
  data: Record<string, unknown>,
  schema: Record<string, 'string' | 'number' | 'boolean' | 'object' | 'array'>,
): void {
  const errors: Record<string, string> = {};

  for (const [field, expectedType] of Object.entries(schema)) {
    const value = data[field];
    if (value === undefined || value === null) continue;

    const actualType = Array.isArray(value) ? 'array' : typeof value;
    if (actualType !== expectedType) {
      errors[field] = `Expected ${expectedType}, got ${actualType}`;
    }
  }

  if (Object.keys(errors).length > 0) {
    throw new ValidationError('Type validation failed', { errors });
  }
}

/**
 * Validates string length
 */
export function validateLength(
  value: string,
  field: string,
  options: { min?: number; max?: number },
): void {
  if (options.min !== undefined && value.length < options.min) {
    throw new ValidationError(`${field} must be at least ${options.min} characters`, {
      field,
      actual: value.length,
      min: options.min,
    });
  }

  if (options.max !== undefined && value.length > options.max) {
    throw new ValidationError(`${field} must be at most ${options.max} characters`, {
      field,
      actual: value.length,
      max: options.max,
    });
  }
}

/**
 * Validates email format
 */
export function validateEmail(email: string): void {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) {
    throw new ValidationError('Invalid email format', { email });
  }
}

/**
 * Validates URL format
 */
export function validateUrl(url: string): void {
  try {
    new URL(url);
  } catch {
    throw new ValidationError('Invalid URL format', { url });
  }
}

/**
 * Validates enum value
 */
export function validateEnum<T extends string>(
  value: string,
  field: string,
  allowedValues: readonly T[],
): void {
  if (!allowedValues.includes(value as T)) {
    throw new ValidationError(`Invalid ${field}`, {
      field,
      value,
      allowed: allowedValues,
    });
  }
}

/**
 * Retries a function with exponential backoff
 */
export async function withRetry<T>(
  fn: () => Promise<T>,
  options: {
    maxAttempts?: number;
    initialDelay?: number;
    maxDelay?: number;
    backoffMultiplier?: number;
    retryableErrors?: Array<new (...args: any[]) => Error>;
  } = {},
): Promise<T> {
  const {
    maxAttempts = 3,
    initialDelay = 100,
    maxDelay = 5000,
    backoffMultiplier = 2,
    retryableErrors = [ExternalServiceError, DatabaseError],
  } = options;

  let lastError: unknown;

  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error;

      // Check if error is retryable
      const isRetryable = retryableErrors.some(
        ErrorClass => error instanceof ErrorClass,
      );

      if (!isRetryable || attempt === maxAttempts - 1) {
        throw error;
      }

      // Calculate delay with exponential backoff
      const delay = Math.min(
        initialDelay * Math.pow(backoffMultiplier, attempt),
        maxDelay,
      );

      logger.warn({
        message: 'Retrying after error',
        attempt: attempt + 1,
        maxAttempts,
        delay,
        error: error instanceof Error ? error.message : String(error),
      });

      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }

  throw lastError;
}

/**
 * Executes function with timeout
 */
export async function withTimeout<T>(
  fn: () => Promise<T>,
  timeoutMs: number,
  timeoutError?: Error,
): Promise<T> {
  return Promise.race([
    fn(),
    new Promise<never>((_, reject) =>
      setTimeout(
        () => reject(timeoutError || new Error(`Operation timed out after ${timeoutMs}ms`)),
        timeoutMs,
      ),
    ),
  ]);
}

/**
 * Safely parses JSON with error handling
 */
export function safeJsonParse<T = unknown>(
  json: string,
  defaultValue?: T,
): T {
  try {
    return JSON.parse(json) as T;
  } catch (error) {
    if (defaultValue !== undefined) {
      return defaultValue;
    }
    throw new ValidationError('Invalid JSON format', {
      error: error instanceof Error ? error.message : String(error),
    });
  }
}

/**
 * Asserts a condition and throws error if false
 */
export function assert(
  condition: boolean,
  message: string,
  ErrorClass: new (message: string) => Error = Error,
): asserts condition {
  if (!condition) {
    throw new ErrorClass(message);
  }
}

/**
 * Creates a standardized error handler for Fastify routes
 */
export function createRouteErrorHandler(component: string) {
  return (error: unknown, request: any, reply: any) => {
    const { statusCode, body } = handleError(error, {
      requestId: request.id,
      userId: request.user?.id,
      tenantId: request.tenant?.id,
    });

    reply.code(statusCode).send(body);
  };
}
