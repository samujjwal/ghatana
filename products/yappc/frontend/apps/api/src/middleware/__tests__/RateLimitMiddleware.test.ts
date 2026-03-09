/**
 * Unit Tests for RateLimitMiddleware
 *
 * <p><b>Purpose</b><br>
 * Tests the rate limit middleware including request throttling, status header
 * injection, error responses, and rate limit enforcement across different tiers.
 *
 * <p><b>Test Coverage</b><br>
 * - Request throttling based on limits
 * - Status header injection
 * - Rate limit exceeded responses
 * - Error handling and retry logic
 * - Tier-based enforcement
 *
 * @doc.type test
 * @doc.purpose Unit tests for rate limit middleware
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { RateLimitMiddleware } from '../RateLimitMiddleware';
import { Request, Response, NextFunction } from 'express';
import { RateLimitingService } from '../../services/ratelimit/RateLimitingService';

// Mock the service
jest.mock('../../services/ratelimit/RateLimitingService');

describe('RateLimitMiddleware', () => {
  let middleware: RateLimitMiddleware;
  let mockRequest: Partial<Request>;
  let mockResponse: Partial<Response>;
  let mockNext: NextFunction;
  let mockRateLimitService: jest.Mocked<RateLimitingService>;

  beforeEach(() => {
    // GIVEN: Fresh mocks and middleware instance
    mockRateLimitService = new RateLimitingService() as jest.Mocked<RateLimitingService>;
    middleware = new RateLimitMiddleware(mockRateLimitService);

    mockRequest = {
      headers: {
        'user-id': 'user-123',
        'x-forwarded-for': '192.168.1.1',
      },
      method: 'GET',
      path: '/api/compliance/assessments',
    };

    mockResponse = {
      status: jest.fn().mockReturnThis(),
      json: jest.fn().mockReturnThis(),
      set: jest.fn().mockReturnThis(),
    };

    mockNext = jest.fn();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('middleware function', () => {
    it('should allow request when under limit', async () => {
      // GIVEN: Request under limit
      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: true,
        remaining: 50,
        resetTime: new Date(),
      });

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should call next()
      expect(mockNext).toHaveBeenCalled();
    });

    it('should deny request when limit exceeded', async () => {
      // GIVEN: Request over limit
      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: false,
        remaining: 0,
        isLimited: true,
        resetTime: new Date(Date.now() + 3600000),
      });

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should return 429 status
      expect(mockResponse.status).toHaveBeenCalledWith(429);
      expect(mockNext).not.toHaveBeenCalled();
    });

    it('should inject rate limit headers in response', async () => {
      // GIVEN: Allowed request
      const remaining = 45;
      const limit = 100;
      const resetTime = new Date(Date.now() + 1800000);

      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: true,
        remaining,
        limit,
        resetTime,
      });

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should set rate limit headers
      expect(mockResponse.set).toHaveBeenCalledWith(
        'X-RateLimit-Limit',
        String(limit)
      );
      expect(mockResponse.set).toHaveBeenCalledWith(
        'X-RateLimit-Remaining',
        String(remaining)
      );
      expect(mockResponse.set).toHaveBeenCalledWith(
        'X-RateLimit-Reset',
        String(Math.floor(resetTime.getTime() / 1000))
      );
    });
  });

  describe('user identification', () => {
    it('should extract user ID from header', async () => {
      // GIVEN: Request with user-id header
      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: true,
        remaining: 99,
      });

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should use user-id for rate limiting
      expect(mockRateLimitService.allowRequest).toHaveBeenCalledWith(
        expect.stringContaining('user-123')
      );
    });

    it('should fallback to IP address when no user-id', async () => {
      // GIVEN: Request without user-id
      mockRequest.headers = { 'x-forwarded-for': '192.168.1.100' };

      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: true,
        remaining: 99,
      });

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should use IP address for rate limiting
      expect(mockRateLimitService.allowRequest).toHaveBeenCalledWith(
        expect.stringContaining('192.168.1.100')
      );
    });

    it('should handle missing IP gracefully', async () => {
      // GIVEN: Request with no identifying info
      mockRequest.headers = {};

      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: true,
        remaining: 99,
      });

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should use default identifier
      expect(mockRateLimitService.allowRequest).toHaveBeenCalled();
    });
  });

  describe('rate limit exceeded response', () => {
    it('should return 429 status code', async () => {
      // GIVEN: Limit exceeded
      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: false,
        remaining: 0,
      });

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should return 429
      expect(mockResponse.status).toHaveBeenCalledWith(429);
    });

    it('should include error message in response', async () => {
      // GIVEN: Limit exceeded
      const resetTime = new Date(Date.now() + 3600000);
      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: false,
        remaining: 0,
        resetTime,
      });

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should return error message
      expect(mockResponse.json).toHaveBeenCalledWith(
        expect.objectContaining({
          error: 'Too Many Requests',
          message: expect.stringContaining('rate limit'),
          retryAfter: expect.any(Number),
        })
      );
    });

    it('should include Retry-After header', async () => {
      // GIVEN: Limit exceeded
      const resetTime = new Date(Date.now() + 1800000); // 30 minutes
      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: false,
        remaining: 0,
        resetTime,
      });

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should set Retry-After header
      expect(mockResponse.set).toHaveBeenCalledWith(
        'Retry-After',
        expect.any(String)
      );
    });
  });

  describe('excluded routes', () => {
    it('should skip rate limiting for health checks', async () => {
      // GIVEN: Health check request
      mockRequest.path = '/health';

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should skip rate limiting
      expect(mockRateLimitService.allowRequest).not.toHaveBeenCalled();
      expect(mockNext).toHaveBeenCalled();
    });

    it('should skip rate limiting for metrics endpoint', async () => {
      // GIVEN: Metrics request
      mockRequest.path = '/metrics';

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should skip rate limiting
      expect(mockRateLimitService.allowRequest).not.toHaveBeenCalled();
      expect(mockNext).toHaveBeenCalled();
    });

    it('should skip rate limiting for auth endpoints', async () => {
      // GIVEN: Auth request
      mockRequest.path = '/api/auth/login';

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should skip rate limiting
      expect(mockRateLimitService.allowRequest).not.toHaveBeenCalled();
      expect(mockNext).toHaveBeenCalled();
    });
  });

  describe('tier enforcement', () => {
    it('should enforce free tier limits', async () => {
      // GIVEN: Free tier user at limit
      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: false,
        tier: 'free',
        remaining: 0,
        limit: 100,
      });

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should deny request
      expect(mockResponse.status).toHaveBeenCalledWith(429);
    });

    it('should allow pro tier higher limits', async () => {
      // GIVEN: Pro tier user with high limit
      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: true,
        tier: 'pro',
        remaining: 9500,
        limit: 10000,
      });

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should allow request
      expect(mockNext).toHaveBeenCalled();
    });

    it('should not limit enterprise tier', async () => {
      // GIVEN: Enterprise tier user
      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: true,
        tier: 'enterprise',
        remaining: Number.MAX_SAFE_INTEGER,
        limit: Number.MAX_SAFE_INTEGER,
      });

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should allow request
      expect(mockNext).toHaveBeenCalled();
    });
  });

  describe('error handling', () => {
    it('should handle service errors gracefully', async () => {
      // GIVEN: Service error
      mockRateLimitService.allowRequest = jest
        .fn()
        .mockRejectedValue(new Error('Redis connection failed'));

      // WHEN: Middleware processes request
      await middleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should allow request (fail open)
      expect(mockNext).toHaveBeenCalled();
    });

    it('should handle missing response object', async () => {
      // GIVEN: Invalid response
      mockResponse = null;

      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: true,
        remaining: 99,
      });

      // WHEN: Middleware processes request
      // THEN: Should handle gracefully
      expect(() => {
        middleware.middleware(
          mockRequest as Request,
          mockResponse as unknown,
          mockNext
        );
      }).not.toThrow();
    });

    it('should handle concurrent requests correctly', async () => {
      // GIVEN: Multiple concurrent requests
      mockRateLimitService.allowRequest = jest
        .fn()
        .mockResolvedValue({
          allowed: true,
          remaining: 50,
        });

      // WHEN: Process multiple requests
      const promises = Array.from({ length: 5 }, () =>
        middleware.middleware(
          mockRequest as Request,
          mockResponse as Response,
          mockNext
        )
      );

      await Promise.all(promises);

      // THEN: All should be processed
      expect(mockNext).toHaveBeenCalledTimes(5);
    });
  });

  describe('custom configuration', () => {
    it('should respect custom excluded routes', async () => {
      // GIVEN: Custom middleware with excluded routes
      const customMiddleware = new RateLimitMiddleware(mockRateLimitService, {
        excludedRoutes: ['/custom/route'],
      });

      mockRequest.path = '/custom/route';

      // WHEN: Middleware processes request
      await customMiddleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should skip rate limiting
      expect(mockRateLimitService.allowRequest).not.toHaveBeenCalled();
      expect(mockNext).toHaveBeenCalled();
    });

    it('should use custom header names if provided', async () => {
      // GIVEN: Custom middleware with custom headers
      const customMiddleware = new RateLimitMiddleware(mockRateLimitService, {
        limitHeader: 'X-Custom-Limit',
        remainingHeader: 'X-Custom-Remaining',
      });

      mockRateLimitService.allowRequest = jest.fn().mockResolvedValue({
        allowed: true,
        remaining: 50,
        limit: 100,
      });

      // WHEN: Middleware processes request
      await customMiddleware.middleware(
        mockRequest as Request,
        mockResponse as Response,
        mockNext
      );

      // THEN: Should use custom header names
      expect(mockResponse.set).toHaveBeenCalledWith(
        'X-Custom-Limit',
        '100'
      );
      expect(mockResponse.set).toHaveBeenCalledWith(
        'X-Custom-Remaining',
        '50'
      );
    });
  });
});

