/**
 * @fileoverview Standardized error hierarchy for connector operations
 * 
 * This module provides a comprehensive error taxonomy for all connector-related
 * failures. All errors extend ConnectorError base class and include structured
 * metadata for logging, monitoring, and error handling.
 * 
 * **Key Benefits:**
 * - Consistent error structure across all connectors
 * - HTTP status code mapping for API errors
 * - Structured error details for debugging
 * - Retry logic support via error classification
 * - JSON serialization for logging
 * 
 * **Error Categories:**
 * - Connection: Network and connectivity issues
 * - Authentication/Authorization: Access control failures
 * - Configuration/Validation: Setup and input errors
 * - Timeout: Operation timeouts
 * - Rate Limit: Throttling errors
 * - Circuit Breaker: Resilience pattern errors
 * - Resource: Capacity and exhaustion errors
 * 
 * @module errors/ConnectorErrors
 * @since 1.0.0
 */

/**
 * Base error class for all connector-related errors.
 * 
 * Extends native Error with structured metadata including error codes,
 * HTTP status codes, contextual details, and timestamps. All connector
 * errors should extend this class.
 * 
 * **Why this class exists:**\n * Provides consistent error structure for logging, monitoring, and handling.
 * Enables error classification for retry logic and proper HTTP responses.
 * 
 * **Error Properties:**
 * - code: Machine-readable error code (e.g., 'CONNECTION_ERROR')
 * - statusCode: HTTP status code for API responses
 * - details: Structured contextual information
 * - timestamp: Unix timestamp when error occurred
 * - connectorId: Identifier of the connector that threw the error
 * 
 * @class ConnectorError
 * @extends Error
 * 
 * @example
 * ```typescript
 * // Creating custom error
 * throw new ConnectorError(
 *   'Failed to connect to database',
 *   'DB_CONNECTION_ERROR',
 *   503,
 *   { host: 'localhost', port: 5432 },
 *   'postgres-connector'
 * );
 * ```
 * 
 * @example
 * ```typescript
 * // Error handling with logging
 * try {
 *   await connector.execute();
 * } catch (error) {
 *   if (error instanceof ConnectorError) {
 *     logger.error('Connector failed', error.toJSON());
 *     
 *     if (isRetryableError(error)) {
 *       await retry();
 *     }
 *   }
 * }
 * ```
 */
export class ConnectorError extends Error {
  /**
   * Machine-readable error code for classification.
   * @readonly
   */
  public readonly code: string;

  /**
   * HTTP status code for API responses.
   * @readonly
   */
  public readonly statusCode: number;

  /**
   * Structured contextual details about the error.
   * @readonly
   */
  public readonly details?: Record<string, any>;

  /**
   * Unix timestamp when error occurred.
   * @readonly
   */
  public readonly timestamp: number;

  /**
   * Identifier of the connector that threw the error.
   * @readonly
   */
  public readonly connectorId?: string;

  /**
   * Creates a new ConnectorError instance.
   * 
   * @param {string} message - Human-readable error message
   * @param {string} code - Machine-readable error code
   * @param {number} [statusCode=500] - HTTP status code
   * @param {Record<string, any>} [details] - Additional error context
   * @param {string} [connectorId] - Connector identifier
   */
  constructor(
    message: string,
    code: string,
    statusCode: number = 500,
    details?: Record<string, any>,
    connectorId?: string
  ) {
    super(message);
    this.name = this.constructor.name;
    this.code = code;
    this.statusCode = statusCode;
    this.details = details;
    this.timestamp = Date.now();
    this.connectorId = connectorId;
    Error.captureStackTrace(this, this.constructor);
  }

  /**
   * Serializes error to JSON for logging and transmission.
   * 
   * **Why this method exists:**
   * Enables structured logging and error transmission over APIs.
   * Includes all relevant error metadata and stack trace.
   * 
   * @returns {object} JSON representation of the error
   * 
   * @example
   * const error = new ConnectorError('Failed', 'ERROR_CODE', 500);
   * logger.error(error.toJSON());
   */
  toJSON() {
    return {
      name: this.name,
      message: this.message,
      code: this.code,
      statusCode: this.statusCode,
      details: this.details,
      timestamp: this.timestamp,
      connectorId: this.connectorId,
      stack: this.stack,
    };
  }
}

/**
 * Connection-related errors for network and connectivity failures.
 * 
 * **When to use:**
 * - TCP/UDP connection failures
 * - DNS resolution errors
 * - Network timeouts
 * - Connection refused/reset
 * 
 * **Status Code:** 503 Service Unavailable
 * **Error Code:** CONNECTION_ERROR
 * **Retryable:** Yes
 * 
 * @class ConnectionError
 * @extends ConnectorError
 * 
 * @example
 * throw new ConnectionError(
 *   'Failed to connect to database',
 *   { host: 'localhost', port: 5432 },
 *   'postgres-connector'
 * );
 */
export class ConnectionError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'CONNECTION_ERROR', 503, details, connectorId);
  }
}

/**
 * Authentication errors for identity verification failures.
 * 
 * **When to use:**
 * - Invalid credentials
 * - Expired tokens
 * - Missing authentication
 * - Invalid API keys
 * 
 * **Status Code:** 401 Unauthorized
 * **Error Code:** AUTHENTICATION_ERROR
 * **Retryable:** No
 * 
 * @class AuthenticationError
 * @extends ConnectorError
 * 
 * @example
 * throw new AuthenticationError(
 *   'Invalid API key',
 *   { keyPrefix: 'sk_test_...' },
 *   'api-connector'
 * );
 */
export class AuthenticationError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'AUTHENTICATION_ERROR', 401, details, connectorId);
  }
}

/**
 * Authorization errors for permission and access control failures.
 * 
 * **When to use:**
 * - Insufficient permissions
 * - Access denied to resource
 * - Role/scope violations
 * 
 * **Status Code:** 403 Forbidden
 * **Error Code:** AUTHORIZATION_ERROR
 * **Retryable:** No
 * 
 * @class AuthorizationError
 * @extends ConnectorError
 * 
 * @example
 * throw new AuthorizationError(
 *   'Insufficient permissions to access resource',
 *   { resource: '/admin', requiredRole: 'admin' }
 * );
 */
export class AuthorizationError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'AUTHORIZATION_ERROR', 403, details, connectorId);
  }
}

/**
 * Configuration errors for setup and initialization failures.
 * 
 * **When to use:**
 * - Invalid configuration
 * - Missing required settings
 * - Incompatible options
 * 
 * **Status Code:** 400 Bad Request
 * **Error Code:** CONFIGURATION_ERROR
 * **Retryable:** No
 * 
 * @class ConfigurationError
 * @extends ConnectorError
 * 
 * @example
 * throw new ConfigurationError(
 *   'Missing required config: apiKey',
 *   { provided: ['host', 'port'], required: ['host', 'port', 'apiKey'] }
 * );
 */
export class ConfigurationError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'CONFIGURATION_ERROR', 400, details, connectorId);
  }
}

/**
 * Validation errors for input data failures.
 * 
 * **When to use:**
 * - Invalid input format
 * - Schema validation failures
 * - Constraint violations
 * 
 * **Status Code:** 400 Bad Request
 * **Error Code:** VALIDATION_ERROR
 * **Retryable:** No
 * 
 * @class ValidationError
 * @extends ConnectorError
 * 
 * @example
 * throw new ValidationError(
 *   'Invalid email format',
 *   { field: 'email', value: 'invalid', expected: 'email format' }
 * );
 */
export class ValidationError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'VALIDATION_ERROR', 400, details, connectorId);
  }
}

/**
 * Timeout errors for operations exceeding time limits.
 * 
 * **When to use:**
 * - Request timeouts
 * - Operation timeouts
 * - Deadline exceeded
 * 
 * **Status Code:** 408 Request Timeout
 * **Error Code:** TIMEOUT_ERROR
 * **Retryable:** Yes
 * 
 * @class TimeoutError
 * @extends ConnectorError
 * 
 * @example
 * throw new TimeoutError(
 *   'Request timeout after 30s',
 *   { timeout: 30000, elapsed: 30100 },
 *   'http-connector'
 * );
 */
export class TimeoutError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'TIMEOUT_ERROR', 408, details, connectorId);
  }
}

/**
 * Rate limit errors for throttling and quota violations.
 * 
 * **When to use:**
 * - API rate limits exceeded
 * - Quota exhausted
 * - Too many requests
 * 
 * **Status Code:** 429 Too Many Requests
 * **Error Code:** RATE_LIMIT_ERROR
 * **Retryable:** Yes (with backoff)
 * 
 * @class RateLimitError
 * @extends ConnectorError
 * 
 * @example
 * throw new RateLimitError(
 *   'Rate limit exceeded',
 *   { limit: 1000, remaining: 0, resetAt: Date.now() + 60000 }
 * );
 */
export class RateLimitError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'RATE_LIMIT_ERROR', 429, details, connectorId);
  }
}

/**
 * Circuit breaker errors when circuit is open.
 * 
 * **When to use:**
 * - Circuit breaker is open
 * - Service is degraded
 * - Preventing cascading failures
 * 
 * **Status Code:** 503 Service Unavailable
 * **Error Code:** CIRCUIT_BREAKER_OPEN
 * **Retryable:** Yes (after cooldown)
 * 
 * @class CircuitBreakerError
 * @extends ConnectorError
 * 
 * @example
 * throw new CircuitBreakerError(
 *   'Circuit breaker open',
 *   { failures: 10, threshold: 5, state: 'open' }
 * );
 * 
 * @see {@link CircuitBreaker}
 */
export class CircuitBreakerError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'CIRCUIT_BREAKER_OPEN', 503, details, connectorId);
  }
}

/**
 * Network errors for general network failures.
 * 
 * **When to use:**
 * - Network unreachable
 * - Host unreachable
 * - General network issues
 * 
 * **Status Code:** 503 Service Unavailable
 * **Error Code:** NETWORK_ERROR
 * **Retryable:** Yes
 * 
 * @class NetworkError
 * @extends ConnectorError
 */
export class NetworkError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'NETWORK_ERROR', 503, details, connectorId);
  }
}

/**
 * Protocol errors for communication protocol violations.
 * 
 * **When to use:**
 * - Invalid protocol messages
 * - Protocol version mismatch
 * - Malformed responses
 * 
 * **Status Code:** 502 Bad Gateway
 * **Error Code:** PROTOCOL_ERROR
 * **Retryable:** No
 * 
 * @class ProtocolError
 * @extends ConnectorError
 */
export class ProtocolError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'PROTOCOL_ERROR', 502, details, connectorId);
  }
}

/**
 * Serialization errors for data encoding/decoding failures.
 * 
 * **When to use:**
 * - JSON parse errors
 * - Encoding failures
 * - Invalid data format
 * 
 * **Status Code:** 500 Internal Server Error
 * **Error Code:** SERIALIZATION_ERROR
 * **Retryable:** No
 * 
 * @class SerializationError
 * @extends ConnectorError
 */
export class SerializationError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'SERIALIZATION_ERROR', 500, details, connectorId);
  }
}

/**
 * Resource exhaustion errors for capacity limits.
 * 
 * **When to use:**
 * - Connection pool exhausted
 * - Memory limits reached
 * - Queue full
 * - Thread pool exhausted
 * 
 * **Status Code:** 503 Service Unavailable
 * **Error Code:** RESOURCE_EXHAUSTED
 * **Retryable:** Yes
 * 
 * @class ResourceExhaustedError
 * @extends ConnectorError
 * 
 * @example
 * throw new ResourceExhaustedError(
 *   'Connection pool exhausted',
 *   { poolSize: 10, waiting: 5 },
 *   'db-connector'
 * );
 * 
 * @see {@link ConnectionPool}
 */
export class ResourceExhaustedError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'RESOURCE_EXHAUSTED', 503, details, connectorId);
  }
}

/**
 * Not found errors for missing resources.
 * 
 * **When to use:**
 * - Resource not found
 * - Endpoint not found
 * - Record not found
 * 
 * **Status Code:** 404 Not Found
 * **Error Code:** NOT_FOUND
 * **Retryable:** No
 * 
 * @class NotFoundError
 * @extends ConnectorError
 */
export class NotFoundError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'NOT_FOUND', 404, details, connectorId);
  }
}

/**
 * Conflict errors for resource conflicts.
 * 
 * **When to use:**
 * - Duplicate resource
 * - Version conflicts
 * - Concurrent modification
 * 
 * **Status Code:** 409 Conflict
 * **Error Code:** CONFLICT
 * **Retryable:** No
 * 
 * @class ConflictError
 * @extends ConnectorError
 */
export class ConflictError extends ConnectorError {
  constructor(message: string, details?: Record<string, any>, connectorId?: string) {
    super(message, 'CONFLICT', 409, details, connectorId);
  }
}

/**
 * Determines if an error is retryable.
 * 
 * **How it works:**
 * Checks if error is a ConnectorError with a retryable error code.
 * Retryable errors are typically transient failures that may succeed on retry.
 * 
 * **Retryable error codes:**
 * - CONNECTION_ERROR: Network connectivity issues
 * - TIMEOUT_ERROR: Operation timeouts
 * - NETWORK_ERROR: General network failures
 * - RESOURCE_EXHAUSTED: Temporary capacity issues
 * 
 * **Why this function exists:**
 * Enables intelligent retry logic by classifying errors as transient or permanent.
 * 
 * @param {Error} error - Error to check
 * @returns {boolean} True if error is retryable
 * 
 * @example
 * try {
 *   await connector.execute();
 * } catch (error) {
 *   if (isRetryableError(error)) {
 *     await retry(operation, { maxAttempts: 3 });
 *   } else {
 *     throw error; // Don't retry permanent failures
 *   }
 * }
 * 
 * @see {@link RetryPolicy}
 */
export function isRetryableError(error: Error): boolean {
  if (error instanceof ConnectorError) {
    return [
      'CONNECTION_ERROR',
      'TIMEOUT_ERROR',
      'NETWORK_ERROR',
      'RESOURCE_EXHAUSTED',
    ].includes(error.code);
  }
  return false;
}

/**
 * Normalizes any error into a ConnectorError instance.
 * 
 * This utility function converts unknown errors (from third-party libraries,
 * native errors, or unexpected values) into standardized ConnectorError instances.
 * It attempts to classify the error based on its message and properties.
 * 
 * **Error classification logic:**
 * 1. If already a ConnectorError, return as-is
 * 2. If contains 'timeout', create TimeoutError
 * 3. If contains 'ECONNREFUSED' or 'ENOTFOUND', create ConnectionError
 * 4. If contains 'authentication' or 'unauthorized', create AuthenticationError
 * 5. Otherwise, create generic ConnectorError with UNKNOWN_ERROR code
 * 
 * **Why this function exists:**
 * - Provides consistent error handling across all connectors
 * - Preserves original error information while adding structure
 * - Enables proper error classification for retry logic
 * - Improves error logging and debugging
 * 
 * @param {unknown} error - The error to normalize (can be any type)
 * @param {string} [connectorId] - Optional connector ID for context
 * @returns {ConnectorError} Normalized ConnectorError instance
 * 
 * @example
 * ```typescript
 * try {
 *   await fetch('https://api.example.com');
 * } catch (error) {
 *   // Convert fetch error to ConnectorError
 *   const normalized = normalizeError(error, 'http-connector');
 *   logger.error('Request failed', normalized.toJSON());
 *   
 *   // Check if retryable
 *   if (isRetryableError(normalized)) {
 *     await retry();
 *   }
 * }
 * ```
 */
export function normalizeError(error: unknown, connectorId?: string): ConnectorError {
  if (error instanceof ConnectorError) {
    return error;
  }

  if (error instanceof Error) {
    // Map common error types (case-insensitive)
    const lowerMessage = error.message.toLowerCase();
    if (lowerMessage.includes('timeout')) {
      return new TimeoutError(error.message, { originalError: error.message }, connectorId);
    }
    if (lowerMessage.includes('econnrefused') || lowerMessage.includes('enotfound')) {
      return new ConnectionError(error.message, { originalError: error.message }, connectorId);
    }
    if (lowerMessage.includes('authentication') || lowerMessage.includes('unauthorized')) {
      return new AuthenticationError(error.message, { originalError: error.message }, connectorId);
    }
    
    return new ConnectorError(
      error.message,
      'UNKNOWN_ERROR',
      500,
      { originalError: error.message },
      connectorId
    );
  }

  return new ConnectorError(
    String(error),
    'UNKNOWN_ERROR',
    500,
    { originalError: String(error) },
    connectorId
  );
}
