/**
 * Canonical Error Classes
 *
 * Standardized error types for consistent error handling across the TutorPutor platform.
 * All errors should extend these base classes for proper categorization and response formatting.
 *
 * @doc.type utility
 * @doc.purpose Standardized error handling
 * @doc.layer core
 * @doc.pattern Error Classes
 */

export enum ErrorCode {
  // Authentication & Authorization Errors (400-403)
  UNAUTHORIZED = "UNAUTHORIZED",
  FORBIDDEN = "FORBIDDEN",
  INVALID_TOKEN = "INVALID_TOKEN",
  TOKEN_EXPIRED = "TOKEN_EXPIRED",
  INSUFFICIENT_PERMISSIONS = "INSUFFICIENT_PERMISSIONS",

  // Request Validation Errors (400)
  INVALID_REQUEST = "INVALID_REQUEST",
  MISSING_REQUIRED_FIELD = "MISSING_REQUIRED_FIELD",
  INVALID_FIELD_VALUE = "INVALID_FIELD_VALUE",
  INVALID_QUERY_PARAMS = "INVALID_QUERY_PARAMS",

  // Resource Errors (404-409)
  RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND",
  RESOURCE_ALREADY_EXISTS = "RESOURCE_ALREADY_EXISTS",
  RESOURCE_CONFLICT = "RESOURCE_CONFLICT",
  RESOURCE_LOCKED = "RESOURCE_LOCKED",

  // Business Logic Errors (422)
  BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION",
  QUOTA_EXCEEDED = "QUOTA_EXCEEDED",
  SUBSCRIPTION_REQUIRED = "SUBSCRIPTION_REQUIRED",
  PAYMENT_REQUIRED = "PAYMENT_REQUIRED",

  // External Service Errors (502-504)
  EXTERNAL_SERVICE_ERROR = "EXTERNAL_SERVICE_ERROR",
  EXTERNAL_SERVICE_TIMEOUT = "EXTERNAL_SERVICE_TIMEOUT",
  EXTERNAL_SERVICE_UNAVAILABLE = "EXTERNAL_SERVICE_UNAVAILABLE",

  // Database Errors (500)
  DATABASE_ERROR = "DATABASE_ERROR",
  DATABASE_CONNECTION_ERROR = "DATABASE_CONNECTION_ERROR",
  DATABASE_TIMEOUT = "DATABASE_TIMEOUT",

  // System Errors (500)
  INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR",
  CONFIGURATION_ERROR = "CONFIGURATION_ERROR",
  DEPENDENCY_ERROR = "DEPENDENCY_ERROR",

  // Rate Limiting (429)
  RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED",

  // Content & Learning Errors
  CONTENT_GENERATION_FAILED = "CONTENT_GENERATION_FAILED",
  CONTENT_VALIDATION_FAILED = "CONTENT_VALIDATION_FAILED",
  LEARNING_PATH_NOT_AVAILABLE = "LEARNING_PATH_NOT_AVAILABLE",

  // Integration Errors
  LTI_VALIDATION_FAILED = "LTI_VALIDATION_FAILED",
  LTI_PLATFORM_NOT_FOUND = "LTI_PLATFORM_NOT_FOUND",
  STRIPE_WEBHOOK_INVALID = "STRIPE_WEBHOOK_INVALID",
  PAYMENT_PROCESSING_FAILED = "PAYMENT_PROCESSING_FAILED",
}

export enum ErrorCategory {
  AUTHENTICATION = "AUTHENTICATION",
  AUTHORIZATION = "AUTHORIZATION",
  VALIDATION = "VALIDATION",
  RESOURCE = "RESOURCE",
  BUSINESS = "BUSINESS",
  EXTERNAL = "EXTERNAL",
  DATABASE = "DATABASE",
  SYSTEM = "SYSTEM",
  RATE_LIMIT = "RATE_LIMIT",
  CONTENT = "CONTENT",
  INTEGRATION = "INTEGRATION",
}

export interface ErrorContext {
  requestId?: string;
  userId?: string;
  tenantId?: string;
  resource?: string;
  action?: string;
  metadata?: Record<string, unknown>;
}

export interface ErrorDetails {
  code: ErrorCode;
  message: string;
  category: ErrorCategory;
  statusCode: number;
  context?: ErrorContext;
  cause?: Error;
  timestamp: string;
}

/**
 * Base error class for all TutorPutor errors
 */
export abstract class TutorPutorError extends Error {
  public readonly code: ErrorCode;
  public readonly category: ErrorCategory;
  public readonly statusCode: number;
  public readonly context?: ErrorContext;
  public readonly timestamp: string;
  public readonly cause?: Error;

  constructor(
    code: ErrorCode,
    message: string,
    category: ErrorCategory,
    statusCode: number,
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(message);
    this.name = this.constructor.name;
    this.code = code;
    this.category = category;
    this.statusCode = statusCode;
    this.context = context;
    this.cause = cause;
    this.timestamp = new Date().toISOString();

    // Maintains proper stack trace for where our error was thrown (only available on V8)
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, this.constructor);
    }
  }

  /**
   * Convert error to a standardized JSON response
   */
  public override toJSON(): ErrorDetails {
    return {
      code: this.code,
      message: this.message,
      category: this.category,
      statusCode: this.statusCode,
      context: this.context,
      timestamp: this.timestamp,
    };
  }

  /**
   * Check if this error is of a specific category
   */
  isCategory(category: ErrorCategory): boolean {
    return this.category === category;
  }

  /**
   * Check if this error has a specific code
   */
  isCode(code: ErrorCode): boolean {
    return this.code === code;
  }
}

/**
 * Authentication Errors (401)
 */
export class UnauthorizedError extends TutorPutorError {
  constructor(
    message = "Authentication required",
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.UNAUTHORIZED,
      message,
      ErrorCategory.AUTHENTICATION,
      401,
      context,
      cause,
    );
  }
}

export class InvalidTokenError extends TutorPutorError {
  constructor(
    message = "Invalid or expired token",
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.INVALID_TOKEN,
      message,
      ErrorCategory.AUTHENTICATION,
      401,
      context,
      cause,
    );
  }
}

export class TokenExpiredError extends TutorPutorError {
  constructor(
    message = "Token has expired",
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.TOKEN_EXPIRED,
      message,
      ErrorCategory.AUTHENTICATION,
      401,
      context,
      cause,
    );
  }
}

/**
 * Authorization Errors (403)
 */
export class ForbiddenError extends TutorPutorError {
  constructor(
    message = "Access forbidden",
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.FORBIDDEN,
      message,
      ErrorCategory.AUTHORIZATION,
      403,
      context,
      cause,
    );
  }
}

export class InsufficientPermissionsError extends TutorPutorError {
  constructor(
    message = "Insufficient permissions",
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.INSUFFICIENT_PERMISSIONS,
      message,
      ErrorCategory.AUTHORIZATION,
      403,
      context,
      cause,
    );
  }
}

/**
 * Validation Errors (400)
 */
export class InvalidRequestError extends TutorPutorError {
  constructor(
    message = "Invalid request",
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.INVALID_REQUEST,
      message,
      ErrorCategory.VALIDATION,
      400,
      context,
      cause,
    );
  }
}

export class MissingRequiredFieldError extends TutorPutorError {
  constructor(field: string, context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.MISSING_REQUIRED_FIELD,
      `Missing required field: ${field}`,
      ErrorCategory.VALIDATION,
      400,
      context,
      cause,
    );
  }
}

export class InvalidFieldValueError extends TutorPutorError {
  constructor(
    field: string,
    value: unknown,
    expectedType: string,
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.INVALID_FIELD_VALUE,
      `Invalid value for field ${field}: ${JSON.stringify(value)}. Expected: ${expectedType}`,
      ErrorCategory.VALIDATION,
      400,
      context,
      cause,
    );
  }
}

/**
 * Resource Errors (404-409)
 */
export class ResourceNotFoundError extends TutorPutorError {
  constructor(resource: string, context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.RESOURCE_NOT_FOUND,
      `${resource} not found`,
      ErrorCategory.RESOURCE,
      404,
      context,
      cause,
    );
  }
}

export class ResourceAlreadyExistsError extends TutorPutorError {
  constructor(resource: string, context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.RESOURCE_ALREADY_EXISTS,
      `${resource} already exists`,
      ErrorCategory.RESOURCE,
      409,
      context,
      cause,
    );
  }
}

export class ResourceConflictError extends TutorPutorError {
  constructor(message: string, context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.RESOURCE_CONFLICT,
      message,
      ErrorCategory.RESOURCE,
      409,
      context,
      cause,
    );
  }
}

/**
 * Business Logic Errors (422)
 */
export class BusinessRuleViolationError extends TutorPutorError {
  constructor(rule: string, context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.BUSINESS_RULE_VIOLATION,
      `Business rule violation: ${rule}`,
      ErrorCategory.BUSINESS,
      422,
      context,
      cause,
    );
  }
}

export class QuotaExceededError extends TutorPutorError {
  constructor(quota: string, context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.QUOTA_EXCEEDED,
      `Quota exceeded: ${quota}`,
      ErrorCategory.BUSINESS,
      422,
      context,
      cause,
    );
  }
}

export class SubscriptionRequiredError extends TutorPutorError {
  constructor(feature: string, context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.SUBSCRIPTION_REQUIRED,
      `Subscription required for: ${feature}`,
      ErrorCategory.BUSINESS,
      422,
      context,
      cause,
    );
  }
}

/**
 * External Service Errors (502-504)
 */
export class ExternalServiceError extends TutorPutorError {
  constructor(
    service: string,
    message?: string,
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.EXTERNAL_SERVICE_ERROR,
      message || `External service error: ${service}`,
      ErrorCategory.EXTERNAL,
      502,
      context,
      cause,
    );
  }
}

export class ExternalServiceTimeoutError extends TutorPutorError {
  constructor(
    service: string,
    timeout?: number,
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.EXTERNAL_SERVICE_TIMEOUT,
      `External service timeout: ${service}${timeout ? ` (${timeout}ms)` : ""}`,
      ErrorCategory.EXTERNAL,
      504,
      context,
      cause,
    );
  }
}

/**
 * Database Errors (500)
 */
export class DatabaseError extends TutorPutorError {
  constructor(operation: string, context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.DATABASE_ERROR,
      `Database error during ${operation}`,
      ErrorCategory.DATABASE,
      500,
      context,
      cause,
    );
  }
}

export class DatabaseConnectionError extends TutorPutorError {
  constructor(context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.DATABASE_CONNECTION_ERROR,
      "Database connection error",
      ErrorCategory.DATABASE,
      500,
      context,
      cause,
    );
  }
}

export class DatabaseTimeoutError extends TutorPutorError {
  constructor(
    operation: string,
    timeout?: number,
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.DATABASE_TIMEOUT,
      `Database timeout during ${operation}${timeout ? ` (${timeout}ms)` : ""}`,
      ErrorCategory.DATABASE,
      500,
      context,
      cause,
    );
  }
}

/**
 * System Errors (500)
 */
export class InternalServerError extends TutorPutorError {
  constructor(
    message = "Internal server error",
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.INTERNAL_SERVER_ERROR,
      message,
      ErrorCategory.SYSTEM,
      500,
      context,
      cause,
    );
  }
}

export class ConfigurationError extends TutorPutorError {
  constructor(config: string, context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.CONFIGURATION_ERROR,
      `Configuration error: ${config}`,
      ErrorCategory.SYSTEM,
      500,
      context,
      cause,
    );
  }
}

/**
 * Rate Limiting Errors (429)
 */
export class RateLimitExceededError extends TutorPutorError {
  constructor(limit: string, context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.RATE_LIMIT_EXCEEDED,
      `Rate limit exceeded: ${limit}`,
      ErrorCategory.RATE_LIMIT,
      429,
      context,
      cause,
    );
  }
}

/**
 * Content & Learning Errors
 */
export class ContentGenerationFailedError extends TutorPutorError {
  constructor(
    contentType: string,
    reason?: string,
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.CONTENT_GENERATION_FAILED,
      `Content generation failed for ${contentType}${reason ? `: ${reason}` : ""}`,
      ErrorCategory.CONTENT,
      500,
      context,
      cause,
    );
  }
}

export class ContentValidationError extends TutorPutorError {
  constructor(
    contentType: string,
    validation: string,
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.CONTENT_VALIDATION_FAILED,
      `Content validation failed for ${contentType}: ${validation}`,
      ErrorCategory.CONTENT,
      422,
      context,
      cause,
    );
  }
}

/**
 * Integration Errors
 */
export class LTIValidationError extends TutorPutorError {
  constructor(reason: string, context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.LTI_VALIDATION_FAILED,
      `LTI validation failed: ${reason}`,
      ErrorCategory.INTEGRATION,
      400,
      context,
      cause,
    );
  }
}

export class LTIPlatformNotFoundError extends TutorPutorError {
  constructor(platformId: string, context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.LTI_PLATFORM_NOT_FOUND,
      `LTI platform not found: ${platformId}`,
      ErrorCategory.INTEGRATION,
      404,
      context,
      cause,
    );
  }
}

export class StripeWebhookInvalidError extends TutorPutorError {
  constructor(reason: string, context?: ErrorContext, cause?: Error) {
    super(
      ErrorCode.STRIPE_WEBHOOK_INVALID,
      `Invalid Stripe webhook: ${reason}`,
      ErrorCategory.INTEGRATION,
      400,
      context,
      cause,
    );
  }
}

export class PaymentProcessingFailedError extends TutorPutorError {
  constructor(
    paymentId: string,
    reason: string,
    context?: ErrorContext,
    cause?: Error,
  ) {
    super(
      ErrorCode.PAYMENT_PROCESSING_FAILED,
      `Payment processing failed for ${paymentId}: ${reason}`,
      ErrorCategory.INTEGRATION,
      500,
      context,
      cause,
    );
  }
}

/**
 * Error factory functions for common scenarios
 */
export class ErrorFactory {
  static fromUnknown(error: unknown, context?: ErrorContext): TutorPutorError {
    if (error instanceof TutorPutorError) {
      return error;
    }

    if (error instanceof Error) {
      return new InternalServerError(error.message, context, error);
    }

    return new InternalServerError(String(error), context);
  }

  static resourceNotFound(
    resource: string,
    id?: string,
    context?: ErrorContext,
  ): ResourceNotFoundError {
    const message = id
      ? `${resource} with id ${id} not found`
      : `${resource} not found`;
    return new ResourceNotFoundError(message, context);
  }

  static validationFailed(
    field: string,
    value: unknown,
    expectedType: string,
    context?: ErrorContext,
  ): InvalidFieldValueError {
    return new InvalidFieldValueError(field, value, expectedType, context);
  }

  static externalServiceDown(
    service: string,
    context?: ErrorContext,
  ): ExternalServiceError {
    return new ExternalServiceError(
      service,
      `${service} is currently unavailable`,
      context,
    );
  }
}

/**
 * Utility functions for error handling
 */
export function isTutorPutorError(error: unknown): error is TutorPutorError {
  return error instanceof TutorPutorError;
}

export function getErrorCode(error: unknown): ErrorCode | null {
  return isTutorPutorError(error) ? error.code : null;
}

export function getErrorCategory(error: unknown): ErrorCategory | null {
  return isTutorPutorError(error) ? error.category : null;
}

export function getErrorStatusCode(error: unknown): number {
  return isTutorPutorError(error) ? error.statusCode : 500;
}
