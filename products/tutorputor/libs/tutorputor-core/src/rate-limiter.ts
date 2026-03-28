/**
 * Rate Limiting Middleware
 * 
 * Provides rate limiting for API endpoints to prevent abuse
 * and protect AI services from overload.
 * 
 * @doc.type utility
 * @doc.purpose Rate limiting and API protection
 * @doc.layer platform
 */

import type { FastifyRequest, FastifyReply } from 'fastify';
import { createStandaloneLogger } from './logger';

const logger = createStandaloneLogger({ component: 'RateLimiter' });

interface RateLimitConfig {
  windowMs: number;
  maxRequests: number;
  keyGenerator?: (request: FastifyRequest) => string;
  skipSuccessfulRequests?: boolean;
  skipFailedRequests?: boolean;
}

interface RateLimitStore {
  increment(key: string): Promise<{ count: number; resetTime: number }>;
  reset(key: string): Promise<void>;
}

/**
 * In-memory rate limit store
 * For production, use Redis-backed store
 */
class MemoryStore implements RateLimitStore {
  private store = new Map<string, { count: number; resetTime: number }>();

  async increment(key: string): Promise<{ count: number; resetTime: number }> {
    const now = Date.now();
    const existing = this.store.get(key);

    if (existing && existing.resetTime > now) {
      existing.count++;
      return existing;
    }

    const resetTime = now + 60000; // 1 minute window
    const entry = { count: 1, resetTime };
    this.store.set(key, entry);
    return entry;
  }

  async reset(key: string): Promise<void> {
    this.store.delete(key);
  }

  // Cleanup expired entries periodically
  cleanup(): void {
    const now = Date.now();
    for (const [key, value] of this.store.entries()) {
      if (value.resetTime <= now) {
        this.store.delete(key);
      }
    }
  }
}

const defaultStore = new MemoryStore();

// Cleanup expired entries every minute
setInterval(() => defaultStore.cleanup(), 60000);

/**
 * Default key generator uses IP address and tenant ID
 */
function defaultKeyGenerator(request: FastifyRequest): string {
  const ip = request.ip || 'unknown';
  const tenantId = (request.params as any)?.tenantId || 'global';
  return `${ip}:${tenantId}`;
}

/**
 * Creates rate limiting middleware
 */
export function createRateLimiter(config: RateLimitConfig, store: RateLimitStore = defaultStore) {
  const {
    windowMs,
    maxRequests,
    keyGenerator = defaultKeyGenerator,
    skipSuccessfulRequests = false,
    skipFailedRequests = false,
  } = config;

  return async (request: FastifyRequest, reply: FastifyReply) => {
    const key = keyGenerator(request);
    const { count, resetTime } = await store.increment(key);

    // Set rate limit headers
    reply.header('X-RateLimit-Limit', maxRequests);
    reply.header('X-RateLimit-Remaining', Math.max(0, maxRequests - count));
    reply.header('X-RateLimit-Reset', new Date(resetTime).toISOString());

    if (count > maxRequests) {
      logger.warn({
        message: 'Rate limit exceeded',
        key,
        count,
        limit: maxRequests,
        ip: request.ip,
        path: request.url,
      });

      reply.code(429).send({
        error: 'Too Many Requests',
        message: `Rate limit exceeded. Maximum ${maxRequests} requests per ${windowMs / 1000} seconds.`,
        retryAfter: Math.ceil((resetTime - Date.now()) / 1000),
      });
      return;
    }

    // Track response status for conditional rate limiting
    if (skipSuccessfulRequests || skipFailedRequests) {
      reply.addHook('onResponse', async (req, rep) => {
        const statusCode = rep.statusCode;
        const shouldSkip =
          (skipSuccessfulRequests && statusCode >= 200 && statusCode < 300) ||
          (skipFailedRequests && statusCode >= 400);

        if (shouldSkip) {
          // Decrement count for skipped requests
          const current = await store.increment(key);
          if (current.count > 0) {
            current.count--;
          }
        }
      });
    }
  };
}

/**
 * Preset configurations for common use cases
 */
export const RateLimitPresets = {
  /**
   * AI endpoints: 20 requests per minute per tenant
   */
  aiEndpoints: {
    windowMs: 60000,
    maxRequests: 20,
    skipFailedRequests: true,
  },

  /**
   * Content generation: 10 requests per minute per tenant
   */
  contentGeneration: {
    windowMs: 60000,
    maxRequests: 10,
    skipFailedRequests: true,
  },

  /**
   * Authentication: 5 requests per minute per IP
   */
  authentication: {
    windowMs: 60000,
    maxRequests: 5,
    skipSuccessfulRequests: true,
    keyGenerator: (request: FastifyRequest) => request.ip || 'unknown',
  },

  /**
   * General API: 100 requests per minute per tenant
   */
  generalApi: {
    windowMs: 60000,
    maxRequests: 100,
  },

  /**
   * Strict: 5 requests per minute per IP
   */
  strict: {
    windowMs: 60000,
    maxRequests: 5,
    keyGenerator: (request: FastifyRequest) => request.ip || 'unknown',
  },
};

/**
 * Redis-backed rate limit store for production
 */
export class RedisStore implements RateLimitStore {
  constructor(private redis: any) {}

  async increment(key: string): Promise<{ count: number; resetTime: number }> {
    const now = Date.now();
    const windowKey = `ratelimit:${key}`;
    
    const count = await this.redis.incr(windowKey);
    
    if (count === 1) {
      // First request in window, set expiration
      await this.redis.pexpire(windowKey, 60000);
    }

    const ttl = await this.redis.pttl(windowKey);
    const resetTime = now + ttl;

    return { count, resetTime };
  }

  async reset(key: string): Promise<void> {
    await this.redis.del(`ratelimit:${key}`);
  }
}

/**
 * Helper to create tenant-specific rate limiter
 */
export function createTenantRateLimiter(
  maxRequests: number,
  windowMs: number = 60000,
) {
  return createRateLimiter({
    windowMs,
    maxRequests,
    keyGenerator: (request) => {
      const tenantId = (request.params as any)?.tenantId || 'global';
      return `tenant:${tenantId}`;
    },
  });
}

/**
 * Helper to create IP-based rate limiter
 */
export function createIpRateLimiter(
  maxRequests: number,
  windowMs: number = 60000,
) {
  return createRateLimiter({
    windowMs,
    maxRequests,
    keyGenerator: (request) => `ip:${request.ip || 'unknown'}`,
  });
}

/**
 * Helper to create user-based rate limiter
 */
export function createUserRateLimiter(
  maxRequests: number,
  windowMs: number = 60000,
) {
  return createRateLimiter({
    windowMs,
    maxRequests,
    keyGenerator: (request) => {
      const userId = (request as any).user?.id || 'anonymous';
      return `user:${userId}`;
    },
  });
}
