/**
 * HTTP Response Caching Middleware Tests
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ResponseCacheMiddleware } from '../cache-response';
import Redis from 'ioredis';

describe('ResponseCacheMiddleware', () => {
  let mockRedis: Redis;
  let middleware: ResponseCacheMiddleware;
  let mockRequest: any;
  let mockReply: any;

  beforeEach(() => {
    mockRedis = {
      get: vi.fn(),
      set: vi.fn(),
      del: vi.fn(),
      keys: vi.fn(),
    } as unknown as Redis;

    middleware = new ResponseCacheMiddleware(mockRedis, 60);

    mockRequest = {
      method: 'GET',
      url: '/api/modules',
      headers: {},
    };

    mockReply = {
      header: vi.fn(),
      code: vi.fn().mockReturnThis(),
      headers: vi.fn().mockReturnThis(),
      send: vi.fn().mockReturnThis(),
      getHeaders: vi.fn().mockReturnValue({}),
    };
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('shouldCache', () => {
    it('should cache GET requests', () => {
      mockRequest.method = 'GET';
      expect(middleware['shouldCache'](mockRequest)).toBe(true);
    });

    it('should not cache POST requests', () => {
      mockRequest.method = 'POST';
      expect(middleware['shouldCache'](mockRequest)).toBe(false);
    });

    it('should not cache requests with authorization header', () => {
      mockRequest.headers.authorization = 'Bearer token';
      expect(middleware['shouldCache'](mockRequest)).toBe(false);
    });
  });

  describe('generateCacheKey', () => {
    it('should generate consistent cache key', () => {
      const key = middleware.generateCacheKey(mockRequest);
      expect(key).toContain('http:cache:');
      expect(key).toContain('GET');
    });
  });

  describe('middleware', () => {
    it('should return cached response on hit', async () => {
      const cachedResponse = {
        statusCode: 200,
        headers: { 'content-type': 'application/json' },
        body: '{"data": "test"}',
      };
      (mockRedis.get as any).mockResolvedValue(JSON.stringify(cachedResponse));

      const handler = middleware.middleware();
      await handler(mockRequest, mockReply);

      expect(mockReply.header).toHaveBeenCalledWith('X-Cache', 'HIT');
      expect(mockReply.code).toHaveBeenCalledWith(200);
      expect(mockReply.send).toHaveBeenCalledWith('{"data": "test"}');
    });

    it('should set cache miss header on miss', async () => {
      (mockRedis.get as any).mockResolvedValue(null);

      const handler = middleware.middleware();
      await handler(mockRequest, mockReply);

      expect(mockReply.header).toHaveBeenCalledWith('X-Cache', 'MISS');
    });

    it('should bypass cache on error', async () => {
      (mockRedis.get as any).mockRejectedValue(new Error('Redis error'));

      const handler = middleware.middleware();
      await handler(mockRequest, mockReply);

      expect(mockReply.header).toHaveBeenCalledWith('X-Cache', 'BYPASS');
    });
  });

  describe('invalidatePattern', () => {
    it('should invalidate matching cache keys', async () => {
      const keys = ['http:cache:/api/modules', 'http:cache:/api/modules/123'];
      (mockRedis.keys as any).mockResolvedValue(keys);
      (mockRedis.del as any).mockResolvedValue(1);

      await middleware.invalidatePattern('modules');

      expect(mockRedis.keys).toHaveBeenCalledWith('http:cache:*');
      expect(mockRedis.del).toHaveBeenCalled();
    });
  });
});
