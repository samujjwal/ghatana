/**
 * Rate Limiting Middleware
 *
 * Provides configurable rate limiting for API endpoints with special handling for AI operations.
 * Uses token bucket algorithm for flexible rate limiting.
 *
 * @module middleware/RateLimitMiddleware
 * @doc.type class
 * @doc.purpose Rate limiting with AI-specific quotas
 * @doc.layer platform
 * @doc.pattern Middleware
 */

import type { FastifyRequest, FastifyReply } from 'fastify';

/**
 * Rate limit configuration for different endpoint types
 */
export interface RateLimitConfig {
    // General API limits
    api: {
        requestsPerMinute: number;
        requestsPerHour: number;
        requestsPerDay: number;
    };
    // AI-specific limits
    ai: {
        requestsPerMinute: number;
        requestsPerHour: number;
        requestsPerDay: number;
        tokensPerMinute: number;
        tokensPerDay: number;
        costLimitPerDay: number; // in dollars
    };
    // Per-user overrides
    userTiers?: {
        free: Partial<RateLimitConfig['ai']>;
        pro: Partial<RateLimitConfig['ai']>;
        enterprise: Partial<RateLimitConfig['ai']>;
    };
}

/**
 * Default rate limit configuration
 */
const DEFAULT_CONFIG: RateLimitConfig = {
    api: {
        requestsPerMinute: 60,
        requestsPerHour: 1000,
        requestsPerDay: 10000,
    },
    ai: {
        requestsPerMinute: 10,
        requestsPerHour: 100,
        requestsPerDay: 500,
        tokensPerMinute: 100000,
        tokensPerDay: 1000000,
        costLimitPerDay: 10.0,
    },
    userTiers: {
        free: {
            requestsPerMinute: 5,
            requestsPerHour: 50,
            requestsPerDay: 200,
            tokensPerDay: 100000,
            costLimitPerDay: 1.0,
        },
        pro: {
            requestsPerMinute: 20,
            requestsPerHour: 200,
            requestsPerDay: 1000,
            tokensPerDay: 5000000,
            costLimitPerDay: 50.0,
        },
        enterprise: {
            requestsPerMinute: 100,
            requestsPerHour: 1000,
            requestsPerDay: 10000,
            tokensPerDay: 50000000,
            costLimitPerDay: 1000.0,
        },
    },
};

/**
 * Rate limit bucket for tracking usage
 */
interface RateLimitBucket {
    minute: { count: number; tokens: number; resetAt: number };
    hour: { count: number; tokens: number; resetAt: number };
    day: { count: number; tokens: number; cost: number; resetAt: number };
}

/**
 * In-memory store for rate limit buckets
 * In production, use Redis for distributed rate limiting
 */
const buckets = new Map<string, RateLimitBucket>();

/**
 * Clean up expired buckets every 5 minutes
 */
setInterval(() => {
    const now = Date.now();
    for (const [key, bucket] of buckets.entries()) {
        if (
            bucket.minute.resetAt < now &&
            bucket.hour.resetAt < now &&
            bucket.day.resetAt < now
        ) {
            buckets.delete(key);
        }
    }
}, 5 * 60 * 1000);

/**
 * Rate limiter class
 */
export class RateLimiter {
    private config: RateLimitConfig;

    constructor(config: Partial<RateLimitConfig> = {}) {
        this.config = {
            ...DEFAULT_CONFIG,
            ...config,
            userTiers: {
                ...DEFAULT_CONFIG.userTiers,
                ...config.userTiers as unknown,
            },
        };
    }

    /**
     * Get rate limit bucket for a user
     */
    private getBucket(userId: string): RateLimitBucket {
        const existing = buckets.get(userId);
        if (existing) {
            return existing;
        }

        const now = Date.now();
        const bucket: RateLimitBucket = {
            minute: { count: 0, tokens: 0, resetAt: now + 60 * 1000 },
            hour: { count: 0, tokens: 0, resetAt: now + 60 * 60 * 1000 },
            day: { count: 0, tokens: 0, cost: 0, resetAt: now + 24 * 60 * 60 * 1000 },
        };
        buckets.set(userId, bucket);
        return bucket;
    }

    /**
     * Reset bucket counters if time window has passed
     */
    private resetIfExpired(bucket: RateLimitBucket): void {
        const now = Date.now();

        if (bucket.minute.resetAt < now) {
            bucket.minute.count = 0;
            bucket.minute.tokens = 0;
            bucket.minute.resetAt = now + 60 * 1000;
        }

        if (bucket.hour.resetAt < now) {
            bucket.hour.count = 0;
            bucket.hour.tokens = 0;
            bucket.hour.resetAt = now + 60 * 60 * 1000;
        }

        if (bucket.day.resetAt < now) {
            bucket.day.count = 0;
            bucket.day.tokens = 0;
            bucket.day.cost = 0;
            bucket.day.resetAt = now + 24 * 60 * 60 * 1000;
        }
    }

    /**
     * Get user tier limits
     */
    private getUserLimits(
        userTier: 'free' | 'pro' | 'enterprise'
    ): RateLimitConfig['ai'] {
        const tierOverrides = this.config.userTiers?.[userTier] || {};
        return {
            ...this.config.ai,
            ...tierOverrides,
        };
    }

    /**
     * Check if request is allowed (general API)
     */
    checkAPILimit(userId: string): {
        allowed: boolean;
        remaining: number;
        resetAt: number;
        retryAfter?: number;
    } {
        const bucket = this.getBucket(userId);
        this.resetIfExpired(bucket);

        const limits = this.config.api;

        // Check minute limit
        if (bucket.minute.count >= limits.requestsPerMinute) {
            return {
                allowed: false,
                remaining: 0,
                resetAt: bucket.minute.resetAt,
                retryAfter: Math.ceil((bucket.minute.resetAt - Date.now()) / 1000),
            };
        }

        // Check hour limit
        if (bucket.hour.count >= limits.requestsPerHour) {
            return {
                allowed: false,
                remaining: 0,
                resetAt: bucket.hour.resetAt,
                retryAfter: Math.ceil((bucket.hour.resetAt - Date.now()) / 1000),
            };
        }

        // Check day limit
        if (bucket.day.count >= limits.requestsPerDay) {
            return {
                allowed: false,
                remaining: 0,
                resetAt: bucket.day.resetAt,
                retryAfter: Math.ceil((bucket.day.resetAt - Date.now()) / 1000),
            };
        }

        // Increment counters
        bucket.minute.count++;
        bucket.hour.count++;
        bucket.day.count++;

        return {
            allowed: true,
            remaining: Math.min(
                limits.requestsPerMinute - bucket.minute.count,
                limits.requestsPerHour - bucket.hour.count,
                limits.requestsPerDay - bucket.day.count
            ),
            resetAt: bucket.minute.resetAt,
        };
    }

    /**
     * Check if AI request is allowed
     */
    checkAILimit(
        userId: string,
        userTier: 'free' | 'pro' | 'enterprise' = 'free',
        estimatedTokens: number = 0,
        estimatedCost: number = 0
    ): {
        allowed: boolean;
        reason?: string;
        remaining: {
            requests: number;
            tokens: number;
            cost: number;
        };
        resetAt: number;
        retryAfter?: number;
    } {
        const bucket = this.getBucket(userId);
        this.resetIfExpired(bucket);

        const limits = this.getUserLimits(userTier);

        // Check minute request limit
        if (bucket.minute.count >= limits.requestsPerMinute) {
            return {
                allowed: false,
                reason: 'Rate limit exceeded: requests per minute',
                remaining: {
                    requests: 0,
                    tokens: limits.tokensPerMinute - bucket.minute.tokens,
                    cost: limits.costLimitPerDay - bucket.day.cost,
                },
                resetAt: bucket.minute.resetAt,
                retryAfter: Math.ceil((bucket.minute.resetAt - Date.now()) / 1000),
            };
        }

        // Check hour request limit
        if (bucket.hour.count >= limits.requestsPerHour) {
            return {
                allowed: false,
                reason: 'Rate limit exceeded: requests per hour',
                remaining: {
                    requests: 0,
                    tokens: limits.tokensPerDay - bucket.day.tokens,
                    cost: limits.costLimitPerDay - bucket.day.cost,
                },
                resetAt: bucket.hour.resetAt,
                retryAfter: Math.ceil((bucket.hour.resetAt - Date.now()) / 1000),
            };
        }

        // Check day request limit
        if (bucket.day.count >= limits.requestsPerDay) {
            return {
                allowed: false,
                reason: 'Rate limit exceeded: requests per day',
                remaining: {
                    requests: 0,
                    tokens: limits.tokensPerDay - bucket.day.tokens,
                    cost: limits.costLimitPerDay - bucket.day.cost,
                },
                resetAt: bucket.day.resetAt,
                retryAfter: Math.ceil((bucket.day.resetAt - Date.now()) / 1000),
            };
        }

        // Check token limits
        if (bucket.minute.tokens + estimatedTokens > limits.tokensPerMinute) {
            return {
                allowed: false,
                reason: 'Token quota exceeded: tokens per minute',
                remaining: {
                    requests: limits.requestsPerDay - bucket.day.count,
                    tokens: 0,
                    cost: limits.costLimitPerDay - bucket.day.cost,
                },
                resetAt: bucket.minute.resetAt,
                retryAfter: Math.ceil((bucket.minute.resetAt - Date.now()) / 1000),
            };
        }

        if (bucket.day.tokens + estimatedTokens > limits.tokensPerDay) {
            return {
                allowed: false,
                reason: 'Token quota exceeded: tokens per day',
                remaining: {
                    requests: limits.requestsPerDay - bucket.day.count,
                    tokens: 0,
                    cost: limits.costLimitPerDay - bucket.day.cost,
                },
                resetAt: bucket.day.resetAt,
                retryAfter: Math.ceil((bucket.day.resetAt - Date.now()) / 1000),
            };
        }

        // Check cost limit
        if (bucket.day.cost + estimatedCost > limits.costLimitPerDay) {
            return {
                allowed: false,
                reason: 'Cost quota exceeded: daily budget limit',
                remaining: {
                    requests: limits.requestsPerDay - bucket.day.count,
                    tokens: limits.tokensPerDay - bucket.day.tokens,
                    cost: 0,
                },
                resetAt: bucket.day.resetAt,
                retryAfter: Math.ceil((bucket.day.resetAt - Date.now()) / 1000),
            };
        }

        // Increment counters
        bucket.minute.count++;
        bucket.minute.tokens += estimatedTokens;
        bucket.hour.count++;
        bucket.hour.tokens += estimatedTokens;
        bucket.day.count++;
        bucket.day.tokens += estimatedTokens;
        bucket.day.cost += estimatedCost;

        return {
            allowed: true,
            remaining: {
                requests: Math.min(
                    limits.requestsPerMinute - bucket.minute.count,
                    limits.requestsPerHour - bucket.hour.count,
                    limits.requestsPerDay - bucket.day.count
                ),
                tokens: Math.min(
                    limits.tokensPerMinute - bucket.minute.tokens,
                    limits.tokensPerDay - bucket.day.tokens
                ),
                cost: limits.costLimitPerDay - bucket.day.cost,
            },
            resetAt: bucket.minute.resetAt,
        };
    }

    /**
     * Update bucket with actual usage (after request completes)
     */
    recordActualUsage(
        userId: string,
        actualTokens: number,
        actualCost: number
    ): void {
        const bucket = this.getBucket(userId);
        this.resetIfExpired(bucket);

        // Adjust token and cost tracking
        bucket.day.tokens = Math.max(0, bucket.day.tokens + actualTokens);
        bucket.day.cost = Math.max(0, bucket.day.cost + actualCost);
    }

    /**
     * Get current usage stats for a user
     */
    getUsageStats(
        userId: string,
        userTier: 'free' | 'pro' | 'enterprise' = 'free'
    ): {
        requests: { used: number; limit: number; resetAt: number };
        tokens: { used: number; limit: number; resetAt: number };
        cost: { used: number; limit: number; resetAt: number };
    } {
        const bucket = this.getBucket(userId);
        this.resetIfExpired(bucket);
        const limits = this.getUserLimits(userTier);

        return {
            requests: {
                used: bucket.day.count,
                limit: limits.requestsPerDay,
                resetAt: bucket.day.resetAt,
            },
            tokens: {
                used: bucket.day.tokens,
                limit: limits.tokensPerDay,
                resetAt: bucket.day.resetAt,
            },
            cost: {
                used: bucket.day.cost,
                limit: limits.costLimitPerDay,
                resetAt: bucket.day.resetAt,
            },
        };
    }

    /**
     * Clear all buckets (for testing)
     */
    clearAll(): void {
        buckets.clear();
    }
}

/**
 * Default rate limiter instance
 */
export const defaultRateLimiter = new RateLimiter();

/**
 * Fastify middleware for API rate limiting
 */
export function apiRateLimitMiddleware(
    request: FastifyRequest,
    reply: FastifyReply,
    done: () => void
): void {
    const userId = ((request as unknown).user as { id?: string })?.id || request.ip;
    const result = defaultRateLimiter.checkAPILimit(userId);

    reply.header('X-RateLimit-Limit', '60');
    reply.header('X-RateLimit-Remaining', result.remaining.toString());
    reply.header(
        'X-RateLimit-Reset',
        new Date(result.resetAt).toISOString()
    );

    if (!result.allowed) {
        reply.header('Retry-After', result.retryAfter!.toString());
        reply.status(429).send({
            error: 'Too Many Requests',
            message: 'Rate limit exceeded. Please try again later.',
            retryAfter: result.retryAfter,
        });
        return;
    }

    done();
}

/**
 * Fastify middleware for AI rate limiting
 */
export function aiRateLimitMiddleware(
    request: FastifyRequest,
    reply: FastifyReply,
    done: () => void
): void {
    const userId = ((request as unknown).user as { id?: string })?.id || request.ip;
    const userTier =
        (((request as unknown).user as { tier?: string })?.tier as 'free' | 'pro' | 'enterprise') ||
        'free';

    // Estimate tokens based on request body (rough heuristic)
    const bodyStr = JSON.stringify(request.body || {});
    const estimatedTokens = Math.ceil(bodyStr.length / 4);
    const estimatedCost = estimatedTokens * 0.00001; // Rough estimate

    const result = defaultRateLimiter.checkAILimit(
        userId,
        userTier,
        estimatedTokens,
        estimatedCost
    );

    reply.header('X-RateLimit-AI-Requests-Remaining', result.remaining.requests.toString());
    reply.header('X-RateLimit-AI-Tokens-Remaining', result.remaining.tokens.toString());
    reply.header('X-RateLimit-AI-Cost-Remaining', result.remaining.cost.toFixed(2));
    reply.header('X-RateLimit-Reset', new Date(result.resetAt).toISOString());

    if (!result.allowed) {
        reply.header('Retry-After', result.retryAfter!.toString());
        reply.status(429).send({
            error: 'AI Rate Limit Exceeded',
            message: result.reason,
            retryAfter: result.retryAfter,
            remaining: result.remaining,
        });
        return;
    }

    done();
}
