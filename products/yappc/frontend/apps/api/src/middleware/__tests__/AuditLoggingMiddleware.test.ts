/**
 * Unit Tests for AuditLoggingMiddleware
 *
 * <p><b>Purpose</b><br>
 * Tests the audit logging middleware including request/response logging,
 * sensitive data redaction, severity determination, and audit trail creation.
 *
 * <p><b>Test Coverage</b><br>
 * - Request/response logging
 * - Sensitive data redaction
 * - Severity level calculation
 * - Actor extraction
 * - IP address tracking
 * - Health check route exclusion
 *
 * @doc.type test
 * @doc.purpose Unit tests for audit middleware
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { AuditLoggingMiddleware } from '../AuditLoggingMiddleware';
import { Request, Response, NextFunction } from 'express';

describe('AuditLoggingMiddleware', () => {
  let middleware: AuditLoggingMiddleware;
  let mockRequest: Partial<Request>;
  let mockResponse: Partial<Response>;
  let mockNext: NextFunction;
  let mockAuditRepository: unknown;

  beforeEach(() => {
    // GIVEN: Fresh mocks and middleware instance
    mockAuditRepository = {
      create: jest.fn().mockResolvedValue({ id: 'log-123' }),
    };

    middleware = new AuditLoggingMiddleware(mockAuditRepository);

    mockRequest = {
      method: 'POST',
      path: '/api/compliance/assessments',
      headers: {
        'user-id': 'user-123',
        'x-forwarded-for': '192.168.1.1',
        'user-agent': 'Mozilla/5.0',
      },
      body: { name: 'Assessment', password: 'secret123' },
    };

    mockResponse = {
      statusCode: 200,
      json: jest.fn().mockReturnThis(),
    };

    mockNext = jest.fn();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('middleware function', () => {
    it('should log successful request', async () => {
      // GIVEN: Valid request
      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should call next()
      expect(mockNext).toHaveBeenCalled();
    });

    it('should create audit log entry', async () => {
      // GIVEN: Valid request
      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should create audit log
      expect(mockAuditRepository.create).toHaveBeenCalledWith(
        expect.objectContaining({
          method: 'POST',
          path: '/api/compliance/assessments',
          actor: 'user-123',
          ipAddress: '192.168.1.1',
        })
      );
    });

    it('should extract actor from user-id header', async () => {
      // GIVEN: Request with user-id header
      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should extract actor correctly
      expect(mockAuditRepository.create).toHaveBeenCalledWith(
        expect.objectContaining({
          actor: 'user-123',
        })
      );
    });

    it('should extract IP address from x-forwarded-for header', async () => {
      // GIVEN: Request with x-forwarded-for header
      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should extract IP correctly
      expect(mockAuditRepository.create).toHaveBeenCalledWith(
        expect.objectContaining({
          ipAddress: '192.168.1.1',
        })
      );
    });

    it('should infer action from HTTP method and path', async () => {
      // GIVEN: POST request to assessments endpoint
      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should infer action as 'create_assessment'
      expect(mockAuditRepository.create).toHaveBeenCalledWith(
        expect.objectContaining({
          action: expect.stringMatching(/create|post/i),
        })
      );
    });

    it('should exclude health check routes from logging', async () => {
      // GIVEN: Health check request
      mockRequest.path = '/health';

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should not create audit log
      expect(mockAuditRepository.create).not.toHaveBeenCalled();
      expect(mockNext).toHaveBeenCalled();
    });

    it('should exclude /metrics routes from logging', async () => {
      // GIVEN: Metrics request
      mockRequest.path = '/metrics';

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should not create audit log
      expect(mockAuditRepository.create).not.toHaveBeenCalled();
    });
  });

  describe('sensitive data redaction', () => {
    it('should redact passwords from request body', async () => {
      // GIVEN: Request with password
      mockRequest.body = { username: 'user', password: 'secret123' };

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Password should be redacted in audit log
      const capturedCall = mockAuditRepository.create.mock.calls[0][0];
      expect(capturedCall.requestBody).not.toContain('secret123');
      expect(capturedCall.requestBody).toContain('[REDACTED]');
    });

    it('should redact tokens from headers', async () => {
      // GIVEN: Request with authorization token
      mockRequest.headers = {
        'authorization': 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...',
      };

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Token should be redacted
      const capturedCall = mockAuditRepository.create.mock.calls[0][0];
      expect(capturedCall.headers).toContain('[REDACTED]');
    });

    it('should redact credit card numbers', async () => {
      // GIVEN: Request with credit card
      mockRequest.body = { cardNumber: '4532123456789010' };

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Card number should be redacted
      const capturedCall = mockAuditRepository.create.mock.calls[0][0];
      expect(capturedCall.requestBody).not.toContain('4532123456789010');
    });

    it('should redact SSN', async () => {
      // GIVEN: Request with SSN
      mockRequest.body = { ssn: '123-45-6789' };

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: SSN should be redacted
      const capturedCall = mockAuditRepository.create.mock.calls[0][0];
      expect(capturedCall.requestBody).not.toContain('123-45-6789');
    });
  });

  describe('severity determination', () => {
    it('should set critical severity for 5xx errors', async () => {
      // GIVEN: Server error response
      mockResponse.statusCode = 500;

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should have critical severity
      expect(mockAuditRepository.create).toHaveBeenCalledWith(
        expect.objectContaining({
          severity: 'critical',
        })
      );
    });

    it('should set warning severity for 4xx errors', async () => {
      // GIVEN: Client error response
      mockResponse.statusCode = 401;

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should have warning severity
      expect(mockAuditRepository.create).toHaveBeenCalledWith(
        expect.objectContaining({
          severity: 'warning',
        })
      );
    });

    it('should set info severity for successful responses', async () => {
      // GIVEN: Successful response
      mockResponse.statusCode = 200;

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should have info severity
      expect(mockAuditRepository.create).toHaveBeenCalledWith(
        expect.objectContaining({
          severity: 'info',
        })
      );
    });
  });

  describe('resource inference', () => {
    it('should infer resource from path for compliance endpoints', async () => {
      // GIVEN: Compliance control assessment request
      mockRequest.path = '/api/compliance/assessments/123';

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should identify resource as assessment
      expect(mockAuditRepository.create).toHaveBeenCalledWith(
        expect.objectContaining({
          resource: expect.stringMatching(/assessment/i),
        })
      );
    });

    it('should infer resource from path for rate limit endpoints', async () => {
      // GIVEN: Rate limit config request
      mockRequest.path = '/api/rate-limit/config/user-123';

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should identify resource as rate_limit_config
      expect(mockAuditRepository.create).toHaveBeenCalledWith(
        expect.objectContaining({
          resource: expect.stringMatching(/rate.limit/i),
        })
      );
    });
  });

  describe('error handling', () => {
    it('should handle missing headers gracefully', async () => {
      // GIVEN: Request with no user-id header
      mockRequest.headers = {};

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should still log with default values
      expect(mockAuditRepository.create).toHaveBeenCalled();
      expect(mockNext).toHaveBeenCalled();
    });

    it('should handle audit repository errors gracefully', async () => {
      // GIVEN: Audit repository throws error
      mockAuditRepository.create.mockRejectedValue(new Error('DB error'));

      // WHEN: Middleware processes request
      // THEN: Should not break the request chain
      await expect(
        middleware.middleware(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        )
      ).resolves.not.toThrow();
    });

    it('should handle undefined request body', async () => {
      // GIVEN: Request with no body
      mockRequest.body = undefined;

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should handle gracefully
      expect(mockAuditRepository.create).toHaveBeenCalled();
    });
  });

  describe('response time tracking', () => {
    it('should track request response time', async () => {
      // GIVEN: Request
      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should record response time
      expect(mockAuditRepository.create).toHaveBeenCalledWith(
        expect.objectContaining({
          responseTime: expect.any(Number),
        })
      );
    });
  });
});

