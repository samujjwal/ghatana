/**
 * @doc.type class
 * @doc.purpose Typed error hierarchy for Tutorputor platform services.
 * @doc.layer product
 * @doc.pattern Service
 *
 * Usage: throw typed errors so callers and error handlers can distinguish
 * between business-logic failures, validation problems, and system faults
 * without parsing error message strings.
 */

export class TutorputorError extends Error {
  constructor(
    message: string,
    public readonly code: string,
    public readonly statusCode: number,
    public readonly isRetryable: boolean = false,
  ) {
    super(message);
    this.name = this.constructor.name;
    // Restore prototype chain (required for extending built-in Error in TS)
    Object.setPrototypeOf(this, new.target.prototype);
  }
}

// ─── 4xx Client Errors ───────────────────────────────────────────────────────

export class ValidationError extends TutorputorError {
  constructor(message: string, code = "VALIDATION_ERROR") {
    super(message, code, 400, false);
  }
}

export class NotFoundError extends TutorputorError {
  constructor(resource: string, id?: string) {
    const msg = id ? `${resource} not found: ${id}` : `${resource} not found`;
    super(msg, "NOT_FOUND", 404, false);
  }
}

export class AuthorizationError extends TutorputorError {
  constructor(message = "Insufficient permissions") {
    super(message, "FORBIDDEN", 403, false);
  }
}

export class ConflictError extends TutorputorError {
  constructor(message: string, code = "CONFLICT") {
    super(message, code, 409, false);
  }
}

// ─── Payment-Specific Errors ─────────────────────────────────────────────────

export class PaymentError extends TutorputorError {
  constructor(message: string, code = "PAYMENT_FAILED") {
    super(message, code, 402, false);
  }
}

export class SubscriptionError extends TutorputorError {
  constructor(message: string, code = "SUBSCRIPTION_ERROR") {
    super(message, code, 402, false);
  }
}

// ─── 5xx Server / System Errors ──────────────────────────────────────────────

export class ExternalServiceError extends TutorputorError {
  constructor(service: string, cause?: string) {
    const msg = cause
      ? `External service error (${service}): ${cause}`
      : `External service error: ${service}`;
    super(msg, "EXTERNAL_SERVICE_ERROR", 502, true);
  }
}

export class RateLimitError extends TutorputorError {
  constructor(message = "Rate limit exceeded") {
    super(message, "RATE_LIMIT_EXCEEDED", 429, true);
  }
}

// ─── Type Guard ───────────────────────────────────────────────────────────────

export function isTutorputorError(err: any): err is TutorputorError {
  return err instanceof TutorputorError;
}
