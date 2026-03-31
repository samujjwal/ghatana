/**
 * @doc.type module
 * @doc.purpose Canonical TutorPutor domain errors shared across services
 * @doc.layer platform
 * @doc.pattern Error Handling
 */

export interface ErrorDetails {
  [key: string]: unknown;
}

export class DomainError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly statusCode = 500,
    public readonly details?: ErrorDetails,
  ) {
    super(message);
    this.name = new.target.name;

    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, new.target);
    }
  }

  toJSON(): Record<string, unknown> {
    return {
      code: this.code,
      message: this.message,
      statusCode: this.statusCode,
      details: this.details,
      name: this.name,
    };
  }
}

export class NotFoundError extends DomainError {
  constructor(resource: string, id: string, details?: ErrorDetails) {
    super("NOT_FOUND", `${resource} not found: ${id}`, 404, {
      resource,
      id,
      ...details,
    });
  }
}

export class ValidationError extends DomainError {
  constructor(message: string, field?: string, details?: ErrorDetails) {
    super("VALIDATION_ERROR", message, 400, field ? { field, ...details } : details);
  }
}

export class ConflictError extends DomainError {
  constructor(message: string, details?: ErrorDetails) {
    super("CONFLICT", message, 409, details);
  }
}

export class AuthorizationError extends DomainError {
  constructor(message = "Unauthorized", details?: ErrorDetails) {
    super("UNAUTHORIZED", message, 401, details);
  }
}

export class ForbiddenError extends DomainError {
  constructor(message = "Forbidden", details?: ErrorDetails) {
    super("FORBIDDEN", message, 403, details);
  }
}

export class RateLimitError extends DomainError {
  constructor(message = "Rate limit exceeded", details?: ErrorDetails) {
    super("RATE_LIMIT", message, 429, details);
  }
}

export class ServiceUnavailableError extends DomainError {
  constructor(message = "Service temporarily unavailable", details?: ErrorDetails) {
    super("SERVICE_UNAVAILABLE", message, 503, details);
  }
}

export function isDomainError(error: unknown): error is DomainError {
  return error instanceof DomainError;
}

export type ErrorCode =
  | "NOT_FOUND"
  | "VALIDATION_ERROR"
  | "CONFLICT"
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "RATE_LIMIT"
  | "SERVICE_UNAVAILABLE"
  | "INTERNAL_ERROR";
