/**
 * HTTP Response Caching Middleware
 *
 * Caches HTTP responses for GET requests to reduce load on backend services.
 * Uses Redis for distributed caching with configurable TTL per route.
 *
 * @doc.type middleware
 * @doc.purpose HTTP response caching for performance optimization
 * @doc.layer platform
 * @doc.pattern Middleware
 */

import type { FastifyRequest, FastifyReply } from 'fastify';
import type Redis from 'ioredis';
import crypto from 'crypto';

interface CacheOptions {
  ttl?: number; // Time to live in seconds
  skipCache?: (req: FastifyRequest) => boolean;
  cacheKeyGenerator?: (req: FastifyRequest) => string;
}

const DEFAULT_TTL = 300; // 5 minutes
const requestCacheMetadata = new WeakMap<
  FastifyRequest,
  { cacheKey: string; ttl: number }
>();

export class ResponseCacheMiddleware {
  private redis: Redis;
  private defaultTTL: number;

  constructor(redis: Redis, defaultTTL = DEFAULT_TTL) {
    this.redis = redis;
    this.defaultTTL = defaultTTL;
  }

  /**
   * Generate cache key for HTTP request
   */
  public generateCacheKey(req: FastifyRequest): string {
    const url = req.url;
    const method = req.method;
    const headers = req.headers;
    
    // Create a hash of the request for consistent key generation
    const keyString = `${method}:${url}:${JSON.stringify(headers)}`;
    const hash = crypto.createHash('sha256').update(keyString).digest('hex');
    return `http:cache:${method}:${hash}`;
  }

  /**
   * Check if request should be cached
   */
  private shouldCache(req: FastifyRequest): boolean {
    // Only cache GET requests
    if (req.method !== 'GET') {
      return false;
    }

    // Don't cache if authorization header is present (user-specific data)
    if (req.headers.authorization) {
      return false;
    }

    // Don't cache if there's a cache-control: no-cache header
    if (req.headers['cache-control'] === 'no-cache') {
      return false;
    }

    return true;
  }

  /**
   * Middleware function
   */
  middleware(options: CacheOptions = {}) {
    return async (req: FastifyRequest, reply: FastifyReply) => {
      const ttl = options.ttl || this.defaultTTL;
      const skipCache = options.skipCache || (() => false);
      const cacheKeyGenerator = options.cacheKeyGenerator || this.generateCacheKey.bind(this);

      // Skip cache if configured or request shouldn't be cached
      if (skipCache(req) || !this.shouldCache(req)) {
        return reply;
      }

      const cacheKey = cacheKeyGenerator(req);

      try {
        // Try to get cached response
        const cached = await this.redis.get(cacheKey);
        
        if (cached) {
          const cachedResponse = JSON.parse(cached);
          
          reply.header('X-Cache', 'HIT');
          reply.header('Cache-Control', `public, max-age=${ttl}`);
          
          return reply
            .code(cachedResponse.statusCode)
            .headers(cachedResponse.headers)
            .send(cachedResponse.body);
        }

        // Cache miss - set header to indicate miss
        reply.header('X-Cache', 'MISS');
        
        // Store cache key on request for onSend hook
        requestCacheMetadata.set(req, { cacheKey, ttl });

        return reply;
      } catch (error) {
        // On Redis error, bypass cache and continue
        console.error('Cache middleware error:', error);
        reply.header('X-Cache', 'BYPASS');
        return reply;
      }
    };
  }

  /**
   * OnSend hook to cache response
   * This should be registered on the Fastify instance
   */
  async onSendHook(req: FastifyRequest, reply: FastifyReply, payload: unknown) {
    const metadata = requestCacheMetadata.get(req);

    if (!metadata) {
      return payload;
    }

    try {
      const responsePayload = typeof payload === 'string' ? payload : JSON.stringify(payload);
      
      await this.redis.set(
        metadata.cacheKey,
        JSON.stringify({
          statusCode: reply.statusCode,
          headers: reply.getHeaders(),
          body: responsePayload,
        }),
        'EX',
        metadata.ttl,
      );
    } catch (error) {
      // Log error but don't fail the request
      console.error('Failed to cache response:', error);
    }
    
    return payload;
  }

  /**
   * Invalidate cache for a specific URL pattern
   */
  async invalidatePattern(pattern: string): Promise<void> {
    try {
      const keys = await this.redis.keys(`http:cache:*`);
      
      for (const key of keys) {
        // Simple pattern matching - in production, consider using a more sophisticated pattern library
        if (key.includes(pattern)) {
          await this.redis.del(key);
        }
      }
    } catch (error) {
      console.error('Failed to invalidate cache pattern:', error);
    }
  }

  /**
   * Clear all HTTP cache
   */
  async clearAll(): Promise<void> {
    try {
      const keys = await this.redis.keys('http:cache:*');
      
      if (keys.length > 0) {
        for (const key of keys) {
          await this.redis.del(key);
        }
      }
    } catch (error) {
      console.error('Failed to clear HTTP cache:', error);
    }
  }
}

export function createCacheMiddleware(redis: Redis, defaultTTL?: number) {
  const middleware = new ResponseCacheMiddleware(redis, defaultTTL);
  return middleware.middleware.bind(middleware);
}
