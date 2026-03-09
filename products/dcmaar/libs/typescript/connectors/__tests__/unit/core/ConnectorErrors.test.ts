/**
 * @fileoverview Comprehensive unit tests for ConnectorErrors
 *
 * Tests cover:
 * - All error class constructors
 * - Error serialization (toJSON)
 * - Error classification (isRetryableError)
 * - Error normalization (normalizeError)
 * - HTTP status codes
 * - Error codes
 * - Metadata handling
 */

import {
  ConnectorError,
  ConnectionError,
  AuthenticationError,
  AuthorizationError,
  ConfigurationError,
  ValidationError,
  TimeoutError,
  RateLimitError,
  CircuitBreakerError,
  NetworkError,
  ProtocolError,
  SerializationError,
  ResourceExhaustedError,
  NotFoundError,
  ConflictError,
  isRetryableError,
  normalizeError,
} from '../../../src/errors/ConnectorErrors';

describe('Connector Errors', () => {
  describe('ConnectorError', () => {
    it('should create error with all properties', () => {
      const error = new ConnectorError(
        'Test error',
        'TEST_CODE',
        500,
        { key: 'value' },
        'test-connector'
      );

      expect(error.message).toBe('Test error');
      expect(error.code).toBe('TEST_CODE');
      expect(error.statusCode).toBe(500);
      expect(error.details).toEqual({ key: 'value' });
      expect(error.connectorId).toBe('test-connector');
      expect(error.timestamp).toBeDefined();
      expect(error.timestamp).toBeGreaterThan(0);
    });

    it('should default statusCode to 500', () => {
      const error = new ConnectorError('Test error', 'TEST_CODE');

      expect(error.statusCode).toBe(500);
    });

    it('should work without details', () => {
      const error = new ConnectorError('Test error', 'TEST_CODE', 400);

      expect(error.details).toBeUndefined();
    });

    it('should work without connectorId', () => {
      const error = new ConnectorError('Test error', 'TEST_CODE');

      expect(error.connectorId).toBeUndefined();
    });

    it('should have correct name', () => {
      const error = new ConnectorError('Test error', 'TEST_CODE');

      expect(error.name).toBe('ConnectorError');
    });

    it('should be instanceof Error', () => {
      const error = new ConnectorError('Test error', 'TEST_CODE');

      expect(error).toBeInstanceOf(Error);
    });

    it('should be instanceof ConnectorError', () => {
      const error = new ConnectorError('Test error', 'TEST_CODE');

      expect(error).toBeInstanceOf(ConnectorError);
    });

    it('should have stack trace', () => {
      const error = new ConnectorError('Test error', 'TEST_CODE');

      expect(error.stack).toBeDefined();
      expect(error.stack).toContain('ConnectorError');
    });

    describe('toJSON()', () => {
      it('should serialize all properties', () => {
        const error = new ConnectorError(
          'Test error',
          'TEST_CODE',
          500,
          { key: 'value' },
          'test-connector'
        );

        const json = error.toJSON();

        expect(json).toMatchObject({
          name: 'ConnectorError',
          message: 'Test error',
          code: 'TEST_CODE',
          statusCode: 500,
          details: { key: 'value' },
          connectorId: 'test-connector',
        });
        expect(json.timestamp).toBeDefined();
        expect(json.stack).toBeDefined();
      });

      it('should serialize without optional fields', () => {
        const error = new ConnectorError('Test error', 'TEST_CODE');

        const json = error.toJSON();

        expect(json).toMatchObject({
          name: 'ConnectorError',
          message: 'Test error',
          code: 'TEST_CODE',
          statusCode: 500,
        });
        expect(json.details).toBeUndefined();
        expect(json.connectorId).toBeUndefined();
      });

      it('should include stack trace', () => {
        const error = new ConnectorError('Test error', 'TEST_CODE');

        const json = error.toJSON();

        expect(json.stack).toContain('ConnectorError');
      });

      it('should be JSON stringifiable', () => {
        const error = new ConnectorError('Test error', 'TEST_CODE');

        expect(() => JSON.stringify(error)).not.toThrow();
      });
    });
  });

  describe('ConnectionError', () => {
    it('should create with correct defaults', () => {
      const error = new ConnectionError('Connection failed');

      expect(error.message).toBe('Connection failed');
      expect(error.code).toBe('CONNECTION_ERROR');
      expect(error.statusCode).toBe(503);
      expect(error.name).toBe('ConnectionError');
    });

    it('should accept details', () => {
      const error = new ConnectionError(
        'Connection failed',
        { host: 'localhost', port: 5432 }
      );

      expect(error.details).toEqual({ host: 'localhost', port: 5432 });
    });

    it('should accept connectorId', () => {
      const error = new ConnectionError(
        'Connection failed',
        undefined,
        'db-connector'
      );

      expect(error.connectorId).toBe('db-connector');
    });

    it('should be instanceof ConnectorError', () => {
      const error = new ConnectionError('Connection failed');

      expect(error).toBeInstanceOf(ConnectorError);
    });
  });

  describe('AuthenticationError', () => {
    it('should create with correct defaults', () => {
      const error = new AuthenticationError('Invalid credentials');

      expect(error.message).toBe('Invalid credentials');
      expect(error.code).toBe('AUTHENTICATION_ERROR');
      expect(error.statusCode).toBe(401);
      expect(error.name).toBe('AuthenticationError');
    });

    it('should accept details and connectorId', () => {
      const error = new AuthenticationError(
        'Invalid API key',
        { keyPrefix: 'sk_test' },
        'api-connector'
      );

      expect(error.details).toEqual({ keyPrefix: 'sk_test' });
      expect(error.connectorId).toBe('api-connector');
    });
  });

  describe('AuthorizationError', () => {
    it('should create with correct defaults', () => {
      const error = new AuthorizationError('Access denied');

      expect(error.message).toBe('Access denied');
      expect(error.code).toBe('AUTHORIZATION_ERROR');
      expect(error.statusCode).toBe(403);
      expect(error.name).toBe('AuthorizationError');
    });

    it('should accept details and connectorId', () => {
      const error = new AuthorizationError(
        'Insufficient permissions',
        { required: 'admin', actual: 'user' },
        'auth-connector'
      );

      expect(error.details).toEqual({ required: 'admin', actual: 'user' });
      expect(error.connectorId).toBe('auth-connector');
    });
  });

  describe('ConfigurationError', () => {
    it('should create with correct defaults', () => {
      const error = new ConfigurationError('Invalid config');

      expect(error.message).toBe('Invalid config');
      expect(error.code).toBe('CONFIGURATION_ERROR');
      expect(error.statusCode).toBe(400);
      expect(error.name).toBe('ConfigurationError');
    });

    it('should accept details and connectorId', () => {
      const error = new ConfigurationError(
        'Missing required field',
        { field: 'apiKey', provided: false },
        'config-connector'
      );

      expect(error.details).toEqual({ field: 'apiKey', provided: false });
      expect(error.connectorId).toBe('config-connector');
    });
  });

  describe('ValidationError', () => {
    it('should create with correct defaults', () => {
      const error = new ValidationError('Validation failed');

      expect(error.message).toBe('Validation failed');
      expect(error.code).toBe('VALIDATION_ERROR');
      expect(error.statusCode).toBe(400);
      expect(error.name).toBe('ValidationError');
    });

    it('should accept details and connectorId', () => {
      const error = new ValidationError(
        'Invalid email',
        { field: 'email', value: 'invalid' },
        'validator'
      );

      expect(error.details).toEqual({ field: 'email', value: 'invalid' });
      expect(error.connectorId).toBe('validator');
    });
  });

  describe('TimeoutError', () => {
    it('should create with correct defaults', () => {
      const error = new TimeoutError('Request timeout');

      expect(error.message).toBe('Request timeout');
      expect(error.code).toBe('TIMEOUT_ERROR');
      expect(error.statusCode).toBe(408);
      expect(error.name).toBe('TimeoutError');
    });

    it('should accept details and connectorId', () => {
      const error = new TimeoutError(
        'Operation timeout',
        { timeout: 5000, elapsed: 5100 },
        'http-connector'
      );

      expect(error.details).toEqual({ timeout: 5000, elapsed: 5100 });
      expect(error.connectorId).toBe('http-connector');
    });
  });

  describe('RateLimitError', () => {
    it('should create with correct defaults', () => {
      const error = new RateLimitError('Rate limit exceeded');

      expect(error.message).toBe('Rate limit exceeded');
      expect(error.code).toBe('RATE_LIMIT_ERROR');
      expect(error.statusCode).toBe(429);
      expect(error.name).toBe('RateLimitError');
    });

    it('should accept details and connectorId', () => {
      const error = new RateLimitError(
        'Too many requests',
        { limit: 1000, remaining: 0, resetAt: Date.now() + 60000 },
        'api-connector'
      );

      expect(error.details?.limit).toBe(1000);
      expect(error.details?.remaining).toBe(0);
      expect(error.connectorId).toBe('api-connector');
    });
  });

  describe('CircuitBreakerError', () => {
    it('should create with correct defaults', () => {
      const error = new CircuitBreakerError('Circuit breaker open');

      expect(error.message).toBe('Circuit breaker open');
      expect(error.code).toBe('CIRCUIT_BREAKER_OPEN');
      expect(error.statusCode).toBe(503);
      expect(error.name).toBe('CircuitBreakerError');
    });

    it('should accept details and connectorId', () => {
      const error = new CircuitBreakerError(
        'Circuit open',
        { failures: 10, threshold: 5, state: 'open' },
        'protected-service'
      );

      expect(error.details).toEqual({ failures: 10, threshold: 5, state: 'open' });
      expect(error.connectorId).toBe('protected-service');
    });
  });

  describe('NetworkError', () => {
    it('should create with correct defaults', () => {
      const error = new NetworkError('Network unreachable');

      expect(error.message).toBe('Network unreachable');
      expect(error.code).toBe('NETWORK_ERROR');
      expect(error.statusCode).toBe(503);
      expect(error.name).toBe('NetworkError');
    });

    it('should accept details and connectorId', () => {
      const error = new NetworkError(
        'Host unreachable',
        { host: '192.168.1.1', error: 'EHOSTUNREACH' },
        'network-connector'
      );

      expect(error.details).toEqual({ host: '192.168.1.1', error: 'EHOSTUNREACH' });
      expect(error.connectorId).toBe('network-connector');
    });
  });

  describe('ProtocolError', () => {
    it('should create with correct defaults', () => {
      const error = new ProtocolError('Protocol error');

      expect(error.message).toBe('Protocol error');
      expect(error.code).toBe('PROTOCOL_ERROR');
      expect(error.statusCode).toBe(502);
      expect(error.name).toBe('ProtocolError');
    });

    it('should accept details and connectorId', () => {
      const error = new ProtocolError(
        'Invalid protocol version',
        { expected: 'v2', received: 'v1' },
        'protocol-connector'
      );

      expect(error.details).toEqual({ expected: 'v2', received: 'v1' });
      expect(error.connectorId).toBe('protocol-connector');
    });
  });

  describe('SerializationError', () => {
    it('should create with correct defaults', () => {
      const error = new SerializationError('Serialization failed');

      expect(error.message).toBe('Serialization failed');
      expect(error.code).toBe('SERIALIZATION_ERROR');
      expect(error.statusCode).toBe(500);
      expect(error.name).toBe('SerializationError');
    });

    it('should accept details and connectorId', () => {
      const error = new SerializationError(
        'JSON parse error',
        { input: 'invalid json', error: 'Unexpected token' },
        'json-connector'
      );

      expect(error.details?.error).toBe('Unexpected token');
      expect(error.connectorId).toBe('json-connector');
    });
  });

  describe('ResourceExhaustedError', () => {
    it('should create with correct defaults', () => {
      const error = new ResourceExhaustedError('Resource exhausted');

      expect(error.message).toBe('Resource exhausted');
      expect(error.code).toBe('RESOURCE_EXHAUSTED');
      expect(error.statusCode).toBe(503);
      expect(error.name).toBe('ResourceExhaustedError');
    });

    it('should accept details and connectorId', () => {
      const error = new ResourceExhaustedError(
        'Connection pool exhausted',
        { poolSize: 10, waiting: 5 },
        'pool-connector'
      );

      expect(error.details).toEqual({ poolSize: 10, waiting: 5 });
      expect(error.connectorId).toBe('pool-connector');
    });
  });

  describe('NotFoundError', () => {
    it('should create with correct defaults', () => {
      const error = new NotFoundError('Resource not found');

      expect(error.message).toBe('Resource not found');
      expect(error.code).toBe('NOT_FOUND');
      expect(error.statusCode).toBe(404);
      expect(error.name).toBe('NotFoundError');
    });

    it('should accept details and connectorId', () => {
      const error = new NotFoundError(
        'User not found',
        { userId: '123' },
        'user-connector'
      );

      expect(error.details).toEqual({ userId: '123' });
      expect(error.connectorId).toBe('user-connector');
    });
  });

  describe('ConflictError', () => {
    it('should create with correct defaults', () => {
      const error = new ConflictError('Resource conflict');

      expect(error.message).toBe('Resource conflict');
      expect(error.code).toBe('CONFLICT');
      expect(error.statusCode).toBe(409);
      expect(error.name).toBe('ConflictError');
    });

    it('should accept details and connectorId', () => {
      const error = new ConflictError(
        'Duplicate key',
        { key: 'email', value: 'test@example.com' },
        'db-connector'
      );

      expect(error.details).toEqual({ key: 'email', value: 'test@example.com' });
      expect(error.connectorId).toBe('db-connector');
    });
  });

  describe('isRetryableError()', () => {
    describe('Retryable Errors', () => {
      it('should return true for ConnectionError', () => {
        const error = new ConnectionError('Connection failed');

        expect(isRetryableError(error)).toBe(true);
      });

      it('should return true for TimeoutError', () => {
        const error = new TimeoutError('Request timeout');

        expect(isRetryableError(error)).toBe(true);
      });

      it('should return true for NetworkError', () => {
        const error = new NetworkError('Network unreachable');

        expect(isRetryableError(error)).toBe(true);
      });

      it('should return true for ResourceExhaustedError', () => {
        const error = new ResourceExhaustedError('Pool exhausted');

        expect(isRetryableError(error)).toBe(true);
      });
    });

    describe('Non-Retryable Errors', () => {
      it('should return false for AuthenticationError', () => {
        const error = new AuthenticationError('Invalid credentials');

        expect(isRetryableError(error)).toBe(false);
      });

      it('should return false for AuthorizationError', () => {
        const error = new AuthorizationError('Access denied');

        expect(isRetryableError(error)).toBe(false);
      });

      it('should return false for ConfigurationError', () => {
        const error = new ConfigurationError('Invalid config');

        expect(isRetryableError(error)).toBe(false);
      });

      it('should return false for ValidationError', () => {
        const error = new ValidationError('Validation failed');

        expect(isRetryableError(error)).toBe(false);
      });

      it('should return false for RateLimitError', () => {
        const error = new RateLimitError('Rate limit exceeded');

        expect(isRetryableError(error)).toBe(false);
      });

      it('should return false for CircuitBreakerError', () => {
        const error = new CircuitBreakerError('Circuit open');

        expect(isRetryableError(error)).toBe(false);
      });

      it('should return false for ProtocolError', () => {
        const error = new ProtocolError('Protocol error');

        expect(isRetryableError(error)).toBe(false);
      });

      it('should return false for SerializationError', () => {
        const error = new SerializationError('Serialization failed');

        expect(isRetryableError(error)).toBe(false);
      });

      it('should return false for NotFoundError', () => {
        const error = new NotFoundError('Not found');

        expect(isRetryableError(error)).toBe(false);
      });

      it('should return false for ConflictError', () => {
        const error = new ConflictError('Conflict');

        expect(isRetryableError(error)).toBe(false);
      });

      it('should return false for standard Error', () => {
        const error = new Error('Generic error');

        expect(isRetryableError(error)).toBe(false);
      });

      it('should return false for non-Error objects', () => {
        expect(isRetryableError('error' as any)).toBe(false);
        expect(isRetryableError(null as any)).toBe(false);
        expect(isRetryableError(undefined as any)).toBe(false);
        expect(isRetryableError(123 as any)).toBe(false);
      });
    });
  });

  describe('normalizeError()', () => {
    describe('ConnectorError Input', () => {
      it('should return ConnectorError as-is', () => {
        const error = new ConnectionError('Connection failed');

        const normalized = normalizeError(error);

        expect(normalized).toBe(error);
        expect(normalized).toBeInstanceOf(ConnectionError);
      });

      it('should preserve all error properties', () => {
        const error = new ConnectionError(
          'Connection failed',
          { host: 'localhost' },
          'test-connector'
        );

        const normalized = normalizeError(error);

        expect(normalized.message).toBe('Connection failed');
        expect(normalized.code).toBe('CONNECTION_ERROR');
        expect(normalized.details).toEqual({ host: 'localhost' });
        expect(normalized.connectorId).toBe('test-connector');
      });
    });

    describe('Standard Error Input', () => {
      it('should convert timeout error message to TimeoutError', () => {
        const error = new Error('Request timeout after 5s');

        const normalized = normalizeError(error, 'test-connector');

        expect(normalized).toBeInstanceOf(TimeoutError);
        expect(normalized.message).toBe('Request timeout after 5s');
        expect(normalized.code).toBe('TIMEOUT_ERROR');
        expect(normalized.connectorId).toBe('test-connector');
      });

      it('should convert ECONNREFUSED error to ConnectionError', () => {
        const error = new Error('connect ECONNREFUSED 127.0.0.1:5432');

        const normalized = normalizeError(error, 'db-connector');

        expect(normalized).toBeInstanceOf(ConnectionError);
        expect(normalized.message).toContain('ECONNREFUSED');
        expect(normalized.code).toBe('CONNECTION_ERROR');
        expect(normalized.connectorId).toBe('db-connector');
      });

      it('should convert ENOTFOUND error to ConnectionError', () => {
        const error = new Error('getaddrinfo ENOTFOUND example.com');

        const normalized = normalizeError(error);

        expect(normalized).toBeInstanceOf(ConnectionError);
        expect(normalized.message).toContain('ENOTFOUND');
        expect(normalized.code).toBe('CONNECTION_ERROR');
      });

      it('should convert authentication error message to AuthenticationError', () => {
        const error = new Error('authentication failed: invalid token');

        const normalized = normalizeError(error);

        expect(normalized).toBeInstanceOf(AuthenticationError);
        expect(normalized.message).toContain('authentication');
        expect(normalized.code).toBe('AUTHENTICATION_ERROR');
      });

      it('should convert unauthorized error message to AuthenticationError', () => {
        const error = new Error('unauthorized access');

        const normalized = normalizeError(error);

        expect(normalized).toBeInstanceOf(AuthenticationError);
        expect(normalized.message).toContain('unauthorized');
        expect(normalized.code).toBe('AUTHENTICATION_ERROR');
      });

      it('should convert unknown Error to ConnectorError', () => {
        const error = new Error('Unknown error occurred');

        const normalized = normalizeError(error, 'test-connector');

        expect(normalized).toBeInstanceOf(ConnectorError);
        expect(normalized.message).toBe('Unknown error occurred');
        expect(normalized.code).toBe('UNKNOWN_ERROR');
        expect(normalized.statusCode).toBe(500);
        expect(normalized.connectorId).toBe('test-connector');
      });

      it('should preserve original error message in details', () => {
        const error = new Error('Original message');

        const normalized = normalizeError(error);

        expect(normalized.details).toEqual({ originalError: 'Original message' });
      });
    });

    describe('Non-Error Input', () => {
      it('should convert string to ConnectorError', () => {
        const normalized = normalizeError('Error string', 'test-connector');

        expect(normalized).toBeInstanceOf(ConnectorError);
        expect(normalized.message).toBe('Error string');
        expect(normalized.code).toBe('UNKNOWN_ERROR');
        expect(normalized.connectorId).toBe('test-connector');
      });

      it('should convert number to ConnectorError', () => {
        const normalized = normalizeError(404);

        expect(normalized).toBeInstanceOf(ConnectorError);
        expect(normalized.message).toBe('404');
        expect(normalized.code).toBe('UNKNOWN_ERROR');
      });

      it('should convert null to ConnectorError', () => {
        const normalized = normalizeError(null);

        expect(normalized).toBeInstanceOf(ConnectorError);
        expect(normalized.message).toBe('null');
        expect(normalized.code).toBe('UNKNOWN_ERROR');
      });

      it('should convert undefined to ConnectorError', () => {
        const normalized = normalizeError(undefined);

        expect(normalized).toBeInstanceOf(ConnectorError);
        expect(normalized.message).toBe('undefined');
        expect(normalized.code).toBe('UNKNOWN_ERROR');
      });

      it('should convert object to ConnectorError', () => {
        const obj = { error: 'something went wrong' };
        const normalized = normalizeError(obj);

        expect(normalized).toBeInstanceOf(ConnectorError);
        expect(normalized.message).toBe('[object Object]');
        expect(normalized.code).toBe('UNKNOWN_ERROR');
      });

      it('should convert array to ConnectorError', () => {
        const normalized = normalizeError(['error1', 'error2']);

        expect(normalized).toBeInstanceOf(ConnectorError);
        expect(normalized.code).toBe('UNKNOWN_ERROR');
      });
    });

    describe('ConnectorId Handling', () => {
      it('should accept connectorId parameter', () => {
        const error = new Error('Test error');

        const normalized = normalizeError(error, 'custom-connector');

        expect(normalized.connectorId).toBe('custom-connector');
      });

      it('should work without connectorId parameter', () => {
        const error = new Error('Test error');

        const normalized = normalizeError(error);

        expect(normalized.connectorId).toBeUndefined();
      });
    });

    describe('Edge Cases', () => {
      it('should handle Error with empty message', () => {
        const error = new Error('');

        const normalized = normalizeError(error);

        expect(normalized).toBeInstanceOf(ConnectorError);
        expect(normalized.message).toBe('');
      });

      it('should handle Error with very long message', () => {
        const longMessage = 'a'.repeat(10000);
        const error = new Error(longMessage);

        const normalized = normalizeError(error);

        expect(normalized.message).toBe(longMessage);
      });

      it('should handle Error with special characters in message', () => {
        const error = new Error('Error: \n\t特殊文字 emoji 😀');

        const normalized = normalizeError(error);

        expect(normalized.message).toContain('特殊文字');
        expect(normalized.message).toContain('😀');
      });

      it('should handle case-insensitive timeout detection', () => {
        const error1 = new Error('TIMEOUT occurred');
        const error2 = new Error('Timeout occurred');
        const error3 = new Error('timeout occurred');

        expect(normalizeError(error1)).toBeInstanceOf(TimeoutError);
        expect(normalizeError(error2)).toBeInstanceOf(TimeoutError);
        expect(normalizeError(error3)).toBeInstanceOf(TimeoutError);
      });

      it('should handle case-insensitive authentication detection', () => {
        const error1 = new Error('AUTHENTICATION failed');
        const error2 = new Error('Authentication failed');
        const error3 = new Error('authentication failed');

        expect(normalizeError(error1)).toBeInstanceOf(AuthenticationError);
        expect(normalizeError(error2)).toBeInstanceOf(AuthenticationError);
        expect(normalizeError(error3)).toBeInstanceOf(AuthenticationError);
      });
    });
  });

  describe('Error Hierarchy', () => {
    it('should maintain instanceof relationships', () => {
      const errors = [
        new ConnectionError('test'),
        new AuthenticationError('test'),
        new TimeoutError('test'),
        new NetworkError('test'),
      ];

      errors.forEach(error => {
        expect(error).toBeInstanceOf(Error);
        expect(error).toBeInstanceOf(ConnectorError);
      });
    });

    it('should distinguish between error types', () => {
      const connectionError = new ConnectionError('test');
      const authError = new AuthenticationError('test');

      expect(connectionError).toBeInstanceOf(ConnectionError);
      expect(connectionError).not.toBeInstanceOf(AuthenticationError);

      expect(authError).toBeInstanceOf(AuthenticationError);
      expect(authError).not.toBeInstanceOf(ConnectionError);
    });
  });
});
