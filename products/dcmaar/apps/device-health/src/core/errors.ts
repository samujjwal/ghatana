/**
 * Standardized error handling system for the extension
 * Provides consistent error types, codes, and handling patterns
 */

/**
 * Error codes for different error categories
 */
export enum ErrorCode {
  // Authentication & Authorization
  AUTH_REQUIRED = 'AUTH_REQUIRED',
  AUTH_FAILED = 'AUTH_FAILED',
  ADMIN_REQUIRED = 'ADMIN_REQUIRED',
  PERMISSION_DENIED = 'PERMISSION_DENIED',

  // Validation
  INVALID_INPUT = 'INVALID_INPUT',
  INVALID_MANIFEST = 'INVALID_MANIFEST',
  INVALID_CONFIG = 'INVALID_CONFIG',
  SCHEMA_VALIDATION_FAILED = 'SCHEMA_VALIDATION_FAILED',

  // Resource Management
  RESOURCE_NOT_FOUND = 'RESOURCE_NOT_FOUND',
  RESOURCE_ALREADY_EXISTS = 'RESOURCE_ALREADY_EXISTS',
  RESOURCE_LIMIT_EXCEEDED = 'RESOURCE_LIMIT_EXCEEDED',

  // Network & Communication
  NETWORK_ERROR = 'NETWORK_ERROR',
  TIMEOUT = 'TIMEOUT',
  CONNECTION_FAILED = 'CONNECTION_FAILED',
  ENDPOINT_UNAVAILABLE = 'ENDPOINT_UNAVAILABLE',

  // Storage
  STORAGE_ERROR = 'STORAGE_ERROR',
  STORAGE_QUOTA_EXCEEDED = 'STORAGE_QUOTA_EXCEEDED',
  STORAGE_READ_FAILED = 'STORAGE_READ_FAILED',
  STORAGE_WRITE_FAILED = 'STORAGE_WRITE_FAILED',

  // Add-on Management
  ADDON_NOT_FOUND = 'ADDON_NOT_FOUND',
  ADDON_ALREADY_EXISTS = 'ADDON_ALREADY_EXISTS',
  ADDON_INSTALL_FAILED = 'ADDON_INSTALL_FAILED',
  ADDON_LOAD_FAILED = 'ADDON_LOAD_FAILED',
  ADDON_SIGNATURE_INVALID = 'ADDON_SIGNATURE_INVALID',

  // Runtime
  INITIALIZATION_FAILED = 'INITIALIZATION_FAILED',
  OPERATION_FAILED = 'OPERATION_FAILED',
  INTERNAL_ERROR = 'INTERNAL_ERROR',
  NOT_IMPLEMENTED = 'NOT_IMPLEMENTED',

  // Unknown
  UNKNOWN_ERROR = 'UNKNOWN_ERROR',
}

/**
 * Error severity levels
 */
export enum ErrorSeverity {
  /** Informational, no action required */
  INFO = 'INFO',
  /** Warning, operation succeeded but with caveats */
  WARNING = 'WARNING',
  /** Error, operation failed but system is stable */
  ERROR = 'ERROR',
  /** Critical error, system stability may be compromised */
  CRITICAL = 'CRITICAL',
}

/**
 * Base extension error class
 */
export class ExtensionError extends Error {
  public readonly code: ErrorCode;
  public readonly severity: ErrorSeverity;
  public readonly details?: Record<string, unknown>;
  public readonly timestamp: number;
  public readonly cause?: Error;

  constructor(
    code: ErrorCode,
    message: string,
    options?: {
      severity?: ErrorSeverity;
      details?: Record<string, unknown>;
      cause?: Error;
    }
  ) {
    super(message);
    this.name = 'ExtensionError';
    this.code = code;
    this.severity = options?.severity ?? ErrorSeverity.ERROR;
    this.details = options?.details;
    this.timestamp = Date.now();
    this.cause = options?.cause;

    // Maintains proper stack trace for where our error was thrown (only available on V8)
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, ExtensionError);
    }
  }

  /**
   * Convert error to a plain object for serialization
   */
  toJSON(): Record<string, unknown> {
    return {
      name: this.name,
      code: this.code,
      message: this.message,
      severity: this.severity,
      details: this.details,
      timestamp: this.timestamp,
      stack: this.stack,
      cause: this.cause
        ? {
            name: this.cause.name,
            message: this.cause.message,
            stack: this.cause.stack,
          }
        : undefined,
    };
  }

  /**
   * Convert error to a user-friendly message
   */
  toUserMessage(): string {
    switch (this.code) {
      case ErrorCode.AUTH_REQUIRED:
        return 'Authentication is required to perform this action.';
      case ErrorCode.ADMIN_REQUIRED:
        return 'Administrator privileges are required for this operation.';
      case ErrorCode.PERMISSION_DENIED:
        return 'You do not have permission to perform this action.';
      case ErrorCode.INVALID_INPUT:
        return 'The provided input is invalid.';
      case ErrorCode.RESOURCE_NOT_FOUND:
        return 'The requested resource was not found.';
      case ErrorCode.RESOURCE_ALREADY_EXISTS:
        return 'A resource with this identifier already exists.';
      case ErrorCode.NETWORK_ERROR:
        return 'A network error occurred. Please check your connection.';
      case ErrorCode.STORAGE_QUOTA_EXCEEDED:
        return 'Storage quota exceeded. Please free up some space.';
      case ErrorCode.ADDON_NOT_FOUND:
        return 'The requested add-on was not found.';
      case ErrorCode.ADDON_SIGNATURE_INVALID:
        return 'The add-on signature is invalid or untrusted.';
      default:
        return this.message || 'An unexpected error occurred.';
    }
  }
}

/**
 * Authentication error
 */
export class AuthenticationError extends ExtensionError {
  constructor(message: string, details?: Record<string, unknown>) {
    super(ErrorCode.AUTH_FAILED, message, {
      severity: ErrorSeverity.ERROR,
      details,
    });
    this.name = 'AuthenticationError';
  }
}

/**
 * Authorization error
 */
export class AuthorizationError extends ExtensionError {
  constructor(message: string, details?: Record<string, unknown>) {
    super(ErrorCode.PERMISSION_DENIED, message, {
      severity: ErrorSeverity.ERROR,
      details,
    });
    this.name = 'AuthorizationError';
  }
}

/**
 * Validation error
 */
export class ValidationError extends ExtensionError {
  constructor(message: string, details?: Record<string, unknown>) {
    super(ErrorCode.INVALID_INPUT, message, {
      severity: ErrorSeverity.ERROR,
      details,
    });
    this.name = 'ValidationError';
  }
}

/**
 * Network error
 */
export class NetworkError extends ExtensionError {
  constructor(message: string, details?: Record<string, unknown>, cause?: Error) {
    super(ErrorCode.NETWORK_ERROR, message, {
      severity: ErrorSeverity.ERROR,
      details,
      cause,
    });
    this.name = 'NetworkError';
  }
}

/**
 * Storage error
 */
export class StorageError extends ExtensionError {
  constructor(
    code: ErrorCode,
    message: string,
    details?: Record<string, unknown>,
    cause?: Error
  ) {
    super(code, message, {
      severity: ErrorSeverity.ERROR,
      details,
      cause,
    });
    this.name = 'StorageError';
  }
}

/**
 * Add-on error
 */
export class AddonError extends ExtensionError {
  constructor(
    code: ErrorCode,
    message: string,
    details?: Record<string, unknown>,
    cause?: Error
  ) {
    super(code, message, {
      severity: ErrorSeverity.ERROR,
      details,
      cause,
    });
    this.name = 'AddonError';
  }
}

/**
 * Error handler utility
 */
export class ErrorHandler {
  /**
   * Handle an error and convert it to an ExtensionError
   */
  static handle(error: unknown, defaultCode = ErrorCode.UNKNOWN_ERROR): ExtensionError {
    if (error instanceof ExtensionError) {
      return error;
    }

    if (error instanceof Error) {
      return new ExtensionError(defaultCode, error.message, {
        cause: error,
      });
    }

    return new ExtensionError(defaultCode, String(error));
  }

  /**
   * Log an error with appropriate severity
   */
  static log(error: ExtensionError, context?: string): void {
    const prefix = context ? `[${context}]` : '';
    const logData = {
      ...error.toJSON(),
      context,
    };

    switch (error.severity) {
      case ErrorSeverity.INFO:
        console.info(`${prefix} INFO:`, logData);
        break;
      case ErrorSeverity.WARNING:
        console.warn(`${prefix} WARNING:`, logData);
        break;
      case ErrorSeverity.ERROR:
        console.error(`${prefix} ERROR:`, logData);
        break;
      case ErrorSeverity.CRITICAL:
        console.error(`${prefix} CRITICAL:`, logData);
        break;
    }
  }

  /**
   * Create a safe error response for external communication
   */
  static toResponse(error: ExtensionError): {
    ok: false;
    error: string;
    code: string;
    details?: Record<string, unknown>;
  } {
    return {
      ok: false,
      error: error.toUserMessage(),
      code: error.code,
      details: error.details,
    };
  }
}

/**
 * Retry utility for operations that may fail temporarily
 */
export class RetryHandler {
  /**
   * Retry an operation with exponential backoff
   */
  static async retry<T>(
    operation: () => Promise<T>,
    options: {
      maxAttempts?: number;
      baseDelayMs?: number;
      maxDelayMs?: number;
      factor?: number;
      shouldRetry?: (error: unknown) => boolean;
    } = {}
  ): Promise<T> {
    const {
      maxAttempts = 3,
      baseDelayMs = 100,
      maxDelayMs = 5000,
      factor = 2,
      shouldRetry = () => true,
    } = options;

    let lastError: unknown;

    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return await operation();
      } catch (error) {
        lastError = error;

        if (attempt === maxAttempts || !shouldRetry(error)) {
          throw error;
        }

        const delay = Math.min(baseDelayMs * Math.pow(factor, attempt - 1), maxDelayMs);
        await new Promise((resolve) => setTimeout(resolve, delay));
      }
    }

    throw lastError;
  }
}
