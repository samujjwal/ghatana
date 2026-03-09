/**
 * Standardized Error Handling
 * 
 * Provides consistent error responses across the API with user-friendly messages
 * and actionable guidance for error resolution.
 * 
 * @doc.type infrastructure
 * @doc.purpose Unified error handling and response formatting
 * @doc.layer infrastructure
 * @doc.pattern ErrorHandler
 */

import type { FastifyError, FastifyReply, FastifyRequest } from 'fastify';
import { Logger } from './logger';

/**
 * Standard error categories
 */
export enum ErrorCategory {
  AUTHENTICATION = 'AUTHENTICATION',
  AUTHORIZATION = 'AUTHORIZATION',
  VALIDATION = 'VALIDATION',
  NOT_FOUND = 'NOT_FOUND',
  CONFLICT = 'CONFLICT',
  RATE_LIMIT = 'RATE_LIMIT',
  EXTERNAL_SERVICE = 'EXTERNAL_SERVICE',
  DATABASE = 'DATABASE',
  INTERNAL = 'INTERNAL',
}

/**
 * Error severity levels
 */
export enum ErrorSeverity {
  LOW = 'low',        // Expected errors (validation, not found)
  MEDIUM = 'medium',  // Recoverable errors (rate limit, conflicts)
  HIGH = 'high',      // Service degradation (external service down)
  CRITICAL = 'critical', // System failures (database down)
}

/**
 * Standardized error response
 */
export interface ErrorResponse {
  error: {
    code: string;
    category: ErrorCategory;
    message: string;
    userMessage: string;
    details?: Record<string, unknown>;
    retryable: boolean;
    retryAfter?: number;
    documentation?: string;
    correlationId?: string;
  };
  timestamp: string;
}

/**
 * Error metadata for internal tracking
 */
interface ErrorMetadata {
  category: ErrorCategory;
  severity: ErrorSeverity;
  userMessage: string;
  retryable: boolean;
  retryAfter?: number;
  documentation?: string;
  statusCode: number;
}

/**
 * Error code to metadata mapping
 */
const ERROR_METADATA: Record<string, ErrorMetadata> = {
  // Authentication errors
  'INVALID_CREDENTIALS': {
    category: ErrorCategory.AUTHENTICATION,
    severity: ErrorSeverity.LOW,
    userMessage: 'The email or password you entered is incorrect. Please try again.',
    retryable: true,
    statusCode: 401,
    documentation: '/docs/authentication',
  },
  'TOKEN_EXPIRED': {
    category: ErrorCategory.AUTHENTICATION,
    severity: ErrorSeverity.LOW,
    userMessage: 'Your session has expired. Please sign in again to continue.',
    retryable: true,
    statusCode: 401,
    documentation: '/docs/authentication#token-refresh',
  },
  'TOKEN_INVALID': {
    category: ErrorCategory.AUTHENTICATION,
    severity: ErrorSeverity.LOW,
    userMessage: 'Your session is invalid. Please sign in again.',
    retryable: false,
    statusCode: 401,
  },
  'MFA_REQUIRED': {
    category: ErrorCategory.AUTHENTICATION,
    severity: ErrorSeverity.LOW,
    userMessage: 'Two-factor authentication is required. Please enter your verification code.',
    retryable: false,
    statusCode: 401,
    documentation: '/docs/security/2fa',
  },

  // Authorization errors
  'INSUFFICIENT_PERMISSIONS': {
    category: ErrorCategory.AUTHORIZATION,
    severity: ErrorSeverity.LOW,
    userMessage: 'You don\'t have permission to perform this action. Contact the sphere owner for access.',
    retryable: false,
    statusCode: 403,
  },
  'SUBSCRIPTION_REQUIRED': {
    category: ErrorCategory.AUTHORIZATION,
    severity: ErrorSeverity.LOW,
    userMessage: 'This feature requires a Pro or Teams subscription. Upgrade your plan to continue.',
    retryable: false,
    statusCode: 403,
    documentation: '/docs/pricing',
  },
  'LIMIT_REACHED': {
    category: ErrorCategory.AUTHORIZATION,
    severity: ErrorSeverity.MEDIUM,
    userMessage: 'You\'ve reached your plan limit. Upgrade to continue or wait until your limit resets.',
    retryable: true,
    retryAfter: 3600, // 1 hour
    statusCode: 429,
    documentation: '/docs/limits',
  },

  // Validation errors
  'VALIDATION_ERROR': {
    category: ErrorCategory.VALIDATION,
    severity: ErrorSeverity.LOW,
    userMessage: 'The information you provided is invalid. Please check the details and try again.',
    retryable: true,
    statusCode: 400,
  },
  'MISSING_FIELD': {
    category: ErrorCategory.VALIDATION,
    severity: ErrorSeverity.LOW,
    userMessage: 'Required information is missing. Please fill in all required fields.',
    retryable: true,
    statusCode: 400,
  },
  'INVALID_FORMAT': {
    category: ErrorCategory.VALIDATION,
    severity: ErrorSeverity.LOW,
    userMessage: 'The format of your input is incorrect. Please check and try again.',
    retryable: true,
    statusCode: 400,
  },

  // Not found errors
  'RESOURCE_NOT_FOUND': {
    category: ErrorCategory.NOT_FOUND,
    severity: ErrorSeverity.LOW,
    userMessage: 'The item you\'re looking for doesn\'t exist or has been deleted.',
    retryable: false,
    statusCode: 404,
  },
  'MOMENT_NOT_FOUND': {
    category: ErrorCategory.NOT_FOUND,
    severity: ErrorSeverity.LOW,
    userMessage: 'This moment doesn\'t exist or you don\'t have access to it.',
    retryable: false,
    statusCode: 404,
  },
  'SPHERE_NOT_FOUND': {
    category: ErrorCategory.NOT_FOUND,
    severity: ErrorSeverity.LOW,
    userMessage: 'This sphere doesn\'t exist or you don\'t have access to it.',
    retryable: false,
    statusCode: 404,
  },

  // Conflict errors
  'RESOURCE_CONFLICT': {
    category: ErrorCategory.CONFLICT,
    severity: ErrorSeverity.MEDIUM,
    userMessage: 'This action conflicts with existing data. Please refresh and try again.',
    retryable: true,
    statusCode: 409,
  },
  'EMAIL_ALREADY_EXISTS': {
    category: ErrorCategory.CONFLICT,
    severity: ErrorSeverity.LOW,
    userMessage: 'An account with this email already exists. Try signing in instead.',
    retryable: false,
    statusCode: 409,
  },

  // Rate limit errors
  'RATE_LIMIT_EXCEEDED': {
    category: ErrorCategory.RATE_LIMIT,
    severity: ErrorSeverity.MEDIUM,
    userMessage: 'You\'re making too many requests. Please wait a moment and try again.',
    retryable: true,
    retryAfter: 60, // 1 minute
    statusCode: 429,
  },

  // External service errors
  'EXTERNAL_SERVICE_ERROR': {
    category: ErrorCategory.EXTERNAL_SERVICE,
    severity: ErrorSeverity.HIGH,
    userMessage: 'We\'re experiencing issues with an external service. Please try again in a few minutes.',
    retryable: true,
    retryAfter: 300, // 5 minutes
    statusCode: 502,
  },
  'AI_SERVICE_UNAVAILABLE': {
    category: ErrorCategory.EXTERNAL_SERVICE,
    severity: ErrorSeverity.HIGH,
    userMessage: 'AI features are temporarily unavailable. Your content is saved and will be processed when the service returns.',
    retryable: true,
    retryAfter: 180, // 3 minutes
    statusCode: 503,
  },
  'STRIPE_ERROR': {
    category: ErrorCategory.EXTERNAL_SERVICE,
    severity: ErrorSeverity.HIGH,
    userMessage: 'Payment processing is temporarily unavailable. Please try again shortly.',
    retryable: true,
    retryAfter: 120, // 2 minutes
    statusCode: 502,
  },

  // Database errors
  'DATABASE_ERROR': {
    category: ErrorCategory.DATABASE,
    severity: ErrorSeverity.CRITICAL,
    userMessage: 'We\'re experiencing database issues. Our team has been notified. Please try again shortly.',
    retryable: true,
    retryAfter: 60,
    statusCode: 503,
  },
  'DATABASE_TIMEOUT': {
    category: ErrorCategory.DATABASE,
    severity: ErrorSeverity.HIGH,
    userMessage: 'The operation took too long. Please try again with a smaller request.',
    retryable: true,
    statusCode: 504,
  },

  // Internal errors
  'INTERNAL_ERROR': {
    category: ErrorCategory.INTERNAL,
    severity: ErrorSeverity.CRITICAL,
    userMessage: 'Something went wrong on our end. Our team has been notified and is investigating.',
    retryable: true,
    retryAfter: 60,
    statusCode: 500,
  },
};

/**
 * Application error class with standardized metadata
 */
export class AppError extends Error {
  constructor(
    public readonly code: string,
    message?: string,
    public readonly details?: Record<string, unknown>
  ) {
    super(message || code);
    this.name = 'AppError';
    Error.captureStackTrace(this, this.constructor);
  }

  getMetadata(): ErrorMetadata {
    return ERROR_METADATA[this.code] || ERROR_METADATA['INTERNAL_ERROR'];
  }
}

/**
 * Format error for API response
 */
export function formatErrorResponse(
  error: Error | AppError | FastifyError,
  correlationId?: string
): ErrorResponse {
  let metadata: ErrorMetadata;
  let code: string;
  let details: Record<string, unknown> | undefined;

  if (error instanceof AppError) {
    metadata = error.getMetadata();
    code = error.code;
    details = error.details;
  } else if ('validation' in error && error.validation) {
    // Fastify validation error
    metadata = ERROR_METADATA['VALIDATION_ERROR'];
    code = 'VALIDATION_ERROR';
    details = { validation: error.validation };
  } else if ('statusCode' in error && error.statusCode === 429) {
    metadata = ERROR_METADATA['RATE_LIMIT_EXCEEDED'];
    code = 'RATE_LIMIT_EXCEEDED';
  } else {
    // Unknown error - treat as internal error
    metadata = ERROR_METADATA['INTERNAL_ERROR'];
    code = 'INTERNAL_ERROR';
  }

  return {
    error: {
      code,
      category: metadata.category,
      message: error.message,
      userMessage: metadata.userMessage,
      details,
      retryable: metadata.retryable,
      retryAfter: metadata.retryAfter,
      documentation: metadata.documentation,
      correlationId,
    },
    timestamp: new Date().toISOString(),
  };
}

/**
 * Global error handler for Fastify
 */
export function createErrorHandler() {
  return async (
    error: Error | AppError | FastifyError,
    request: FastifyRequest & { logger?: Logger },
    reply: FastifyReply
  ) => {
    const logger = request.logger || Logger.fromRequest(request);
    const correlationId = logger.context.correlationId;

    // Determine error metadata
    let metadata: ErrorMetadata;
    if (error instanceof AppError) {
      metadata = error.getMetadata();
    } else if ('validation' in error && error.validation) {
      metadata = ERROR_METADATA['VALIDATION_ERROR'];
    } else if ('statusCode' in error) {
      // Try to map status code to error type
      if (error.statusCode === 429) {
        metadata = ERROR_METADATA['RATE_LIMIT_EXCEEDED'];
      } else if (error.statusCode === 401) {
        metadata = ERROR_METADATA['TOKEN_INVALID'];
      } else if (error.statusCode === 403) {
        metadata = ERROR_METADATA['INSUFFICIENT_PERMISSIONS'];
      } else if (error.statusCode === 404) {
        metadata = ERROR_METADATA['RESOURCE_NOT_FOUND'];
      } else {
        metadata = ERROR_METADATA['INTERNAL_ERROR'];
      }
    } else {
      metadata = ERROR_METADATA['INTERNAL_ERROR'];
    }

    // Log based on severity
    if (metadata.severity === ErrorSeverity.CRITICAL || metadata.severity === ErrorSeverity.HIGH) {
      logger.error('Request failed with error', error, {
        category: metadata.category,
        severity: metadata.severity,
        statusCode: metadata.statusCode,
      });
    } else {
      logger.warn('Request failed', {
        message: error.message,
        category: metadata.category,
        statusCode: metadata.statusCode,
      });
    }

    // Format response
    const errorResponse = formatErrorResponse(error, correlationId);

    // Set retry-after header if applicable
    if (errorResponse.error.retryAfter) {
      reply.header('Retry-After', errorResponse.error.retryAfter.toString());
    }

    // Send response
    return reply.code(metadata.statusCode).send(errorResponse);
  };
}

/**
 * Helper functions for throwing common errors
 */
export const throwError = {
  invalidCredentials: () => {
    throw new AppError('INVALID_CREDENTIALS');
  },
  tokenExpired: () => {
    throw new AppError('TOKEN_EXPIRED');
  },
  tokenInvalid: () => {
    throw new AppError('TOKEN_INVALID');
  },
  mfaRequired: () => {
    throw new AppError('MFA_REQUIRED');
  },
  insufficientPermissions: (details?: Record<string, unknown>) => {
    throw new AppError('INSUFFICIENT_PERMISSIONS', undefined, details);
  },
  subscriptionRequired: (feature: string) => {
    throw new AppError('SUBSCRIPTION_REQUIRED', undefined, { feature });
  },
  limitReached: (limitType: string, limit: number) => {
    throw new AppError('LIMIT_REACHED', undefined, { limitType, limit });
  },
  validationError: (details: Record<string, unknown>) => {
    throw new AppError('VALIDATION_ERROR', undefined, details);
  },
  notFound: (resource: string, id?: string) => {
    throw new AppError('RESOURCE_NOT_FOUND', undefined, { resource, id });
  },
  momentNotFound: (momentId: string) => {
    throw new AppError('MOMENT_NOT_FOUND', undefined, { momentId });
  },
  sphereNotFound: (sphereId: string) => {
    throw new AppError('SPHERE_NOT_FOUND', undefined, { sphereId });
  },
  conflict: (message: string, details?: Record<string, unknown>) => {
    throw new AppError('RESOURCE_CONFLICT', message, details);
  },
  emailExists: (email: string) => {
    throw new AppError('EMAIL_ALREADY_EXISTS', undefined, { email });
  },
  rateLimitExceeded: () => {
    throw new AppError('RATE_LIMIT_EXCEEDED');
  },
  externalServiceError: (service: string) => {
    throw new AppError('EXTERNAL_SERVICE_ERROR', undefined, { service });
  },
  aiServiceUnavailable: () => {
    throw new AppError('AI_SERVICE_UNAVAILABLE');
  },
  stripeError: (message: string) => {
    throw new AppError('STRIPE_ERROR', message);
  },
  databaseError: (operation: string) => {
    throw new AppError('DATABASE_ERROR', undefined, { operation });
  },
  internalError: (message?: string) => {
    throw new AppError('INTERNAL_ERROR', message);
  },
};
