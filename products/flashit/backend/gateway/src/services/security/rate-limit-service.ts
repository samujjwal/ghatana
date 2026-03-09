/**
 * Rate Limiting Service
 * 
 * @doc.type service
 * @doc.purpose Tier-based rate limiting with per-user quotas and graceful degradation
 * @doc.layer product
 * @doc.pattern Service
 * 
 * @description Implements sophisticated rate limiting:
 * - Tier-based quotas (FREE, BASIC, PRO, ENTERPRISE)
 * - Per-minute, per-hour, per-day limits
 * - Endpoint-specific limits
 * - Graceful degradation
 * - Temporary blocking for violations
 * - Rate limit headers
 * 
 * @example
 * ```typescript
 * const rateLimitService = new RateLimitService();
 * 
 * // Check rate limit
 * const result = await rateLimitService.checkRateLimit({
 *   userId: 'user-id',
 *   endpoint: '/api/moments',
 *   ipAddress: '127.0.0.1'
 * });
 * 
 * if (!result.allowed) {
 *   throw new Error(`Rate limit exceeded. Reset in ${result.resetIn} seconds`);
 * }
 * ```
 */

import { prisma } from '../../lib/prisma.js';

export interface RateLimitCheck {
  userId?: string;
  ipAddress?: string;
  endpoint: string;
}

export interface RateLimitResult {
  allowed: boolean;
  limit: number;
  remaining: number;
  resetIn: number; // seconds
  tier: string;
}

export interface TierLimits {
  requestsPerMinute: number;
  requestsPerHour: number;
  requestsPerDay: number;
}

const DEFAULT_TIER_LIMITS: Record<string, TierLimits> = {
  FREE: {
    requestsPerMinute: 10,
    requestsPerHour: 100,
    requestsPerDay: 1000,
  },
  BASIC: {
    requestsPerMinute: 30,
    requestsPerHour: 500,
    requestsPerDay: 5000,
  },
  PRO: {
    requestsPerMinute: 100,
    requestsPerHour: 2000,
    requestsPerDay: 20000,
  },
  ENTERPRISE: {
    requestsPerMinute: 500,
    requestsPerHour: 10000,
    requestsPerDay: 100000,
  },
};

// Endpoint-specific multipliers (some endpoints are more expensive)
const ENDPOINT_COST_MULTIPLIERS: Record<string, number> = {
  '/api/search': 2, // Search is more expensive
  '/api/transcription': 5, // Transcription is very expensive
  '/api/analytics/insights': 3, // AI insights are expensive
  '/api/reports/generate': 4, // Report generation is expensive
};

export class RateLimitService {
  /**
   * Get tier limits for a user
   */
  private async getTierLimits(userId?: string): Promise<{ tier: string; limits: TierLimits }> {
    if (!userId) {
      return { tier: 'FREE', limits: DEFAULT_TIER_LIMITS.FREE };
    }

    const tierSettings = await prisma.userTierSettings.findUnique({
      where: { userId },
      select: {
        tier: true,
        requestsPerMinute: true,
        requestsPerHour: true,
        requestsPerDay: true,
      },
    });

    if (!tierSettings) {
      return { tier: 'FREE', limits: DEFAULT_TIER_LIMITS.FREE };
    }

    return {
      tier: tierSettings.tier,
      limits: {
        requestsPerMinute: tierSettings.requestsPerMinute,
        requestsPerHour: tierSettings.requestsPerHour,
        requestsPerDay: tierSettings.requestsPerDay,
      },
    };
  }

  /**
   * Get cost multiplier for an endpoint
   */
  private getEndpointCost(endpoint: string): number {
    // Check for exact match first
    if (ENDPOINT_COST_MULTIPLIERS[endpoint]) {
      return ENDPOINT_COST_MULTIPLIERS[endpoint];
    }

    // Check for partial match (e.g., /api/search/semantic matches /api/search)
    for (const [pattern, multiplier] of Object.entries(ENDPOINT_COST_MULTIPLIERS)) {
      if (endpoint.startsWith(pattern)) {
        return multiplier;
      }
    }

    return 1; // Default cost
  }

  /**
   * Check if user/IP is temporarily blocked
   */
  private async isBlocked(userId?: string, ipAddress?: string): Promise<boolean> {
    const now = new Date();

    const blocked = await prisma.$queryRaw<Array<{ count: bigint }>>`
      SELECT COUNT(*) as count
      FROM rate_limits
      WHERE (user_id = ${userId}::uuid OR ip_address = ${ipAddress}::inet)
        AND blocked_until > ${now}
      LIMIT 1
    `;

    return Number(blocked[0]?.count || 0) > 0;
  }

  /**
   * Check rate limit for a request
   */
  async checkRateLimit(check: RateLimitCheck): Promise<RateLimitResult> {
    const { tier, limits } = await this.getTierLimits(check.userId);
    const cost = this.getEndpointCost(check.endpoint);

    // Check if temporarily blocked
    const blocked = await this.isBlocked(check.userId, check.ipAddress);
    if (blocked) {
      return {
        allowed: false,
        limit: 0,
        remaining: 0,
        resetIn: 3600, // 1 hour block
        tier,
      };
    }

    // Check each time window (minute, hour, day)
    const windows = [
      { duration: 60, limit: limits.requestsPerMinute, name: 'minute' },
      { duration: 3600, limit: limits.requestsPerHour, name: 'hour' },
      { duration: 86400, limit: limits.requestsPerDay, name: 'day' },
    ];

    for (const window of windows) {
      const windowStart = new Date();
      windowStart.setSeconds(windowStart.getSeconds() - window.duration);

      // Count requests in this window
      const count = await prisma.$queryRaw<Array<{ total: bigint }>>`
        SELECT COALESCE(SUM(request_count), 0) as total
        FROM rate_limits
        WHERE endpoint = ${check.endpoint}
          AND window_start >= ${windowStart}
          AND (
            (user_id IS NOT NULL AND user_id = ${check.userId}::uuid)
            OR (user_id IS NULL AND ip_address = ${check.ipAddress}::inet)
          )
      `;

      const currentCount = Number(count[0]?.total || 0);
      const adjustedLimit = Math.floor(window.limit / cost);

      if (currentCount >= adjustedLimit) {
        // Log rate limit exceeded
        await prisma.securityAuditLog.create({
          data: {
            userId: check.userId,
            eventType: 'RATE_LIMIT_EXCEEDED',
            severity: 'WARNING',
            ipAddress: check.ipAddress,
            success: false,
            details: {
              endpoint: check.endpoint,
              window: window.name,
              limit: adjustedLimit,
              count: currentCount,
              tier,
            },
          },
        });

        // Check for repeated violations - block temporarily
        const recentViolations = await prisma.securityAuditLog.count({
          where: {
            userId: check.userId,
            eventType: 'RATE_LIMIT_EXCEEDED',
            createdAt: { gte: new Date(Date.now() - 5 * 60 * 1000) }, // Last 5 minutes
          },
        });

        if (recentViolations >= 5) {
          // Block for 1 hour
          const blockedUntil = new Date();
          blockedUntil.setHours(blockedUntil.getHours() + 1);

          await prisma.$executeRaw`
            INSERT INTO rate_limits (user_id, ip_address, endpoint, request_count, window_start, window_end, tier, blocked_until)
            VALUES (${check.userId}::uuid, ${check.ipAddress}::inet, ${check.endpoint}, 1, NOW(), NOW() + INTERVAL '1 hour', ${tier}, ${blockedUntil})
          `;

          await prisma.securityAuditLog.create({
            data: {
              userId: check.userId,
              eventType: 'RATE_LIMIT_BLOCKED',
              severity: 'CRITICAL',
              ipAddress: check.ipAddress,
              success: false,
              details: {
                endpoint: check.endpoint,
                violations: recentViolations,
                blockedUntil,
              },
            },
          });
        }

        return {
          allowed: false,
          limit: adjustedLimit,
          remaining: 0,
          resetIn: window.duration - Math.floor((Date.now() - windowStart.getTime()) / 1000),
          tier,
        };
      }
    }

    // Record this request
    await this.recordRequest(check, tier);

    // Calculate remaining for the most restrictive window (minute)
    const minuteWindowStart = new Date();
    minuteWindowStart.setSeconds(minuteWindowStart.getSeconds() - 60);

    const minuteCount = await prisma.$queryRaw<Array<{ total: bigint }>>`
      SELECT COALESCE(SUM(request_count), 0) as total
      FROM rate_limits
      WHERE endpoint = ${check.endpoint}
        AND window_start >= ${minuteWindowStart}
        AND (
          (user_id IS NOT NULL AND user_id = ${check.userId}::uuid)
          OR (user_id IS NULL AND ip_address = ${check.ipAddress}::inet)
        )
    `;

    const currentMinuteCount = Number(minuteCount[0]?.total || 0);
    const adjustedMinuteLimit = Math.floor(limits.requestsPerMinute / cost);

    return {
      allowed: true,
      limit: adjustedMinuteLimit,
      remaining: Math.max(0, adjustedMinuteLimit - currentMinuteCount - 1),
      resetIn: 60,
      tier,
    };
  }

  /**
   * Record a request in rate limit tracking
   */
  private async recordRequest(check: RateLimitCheck, tier: string): Promise<void> {
    const now = new Date();
    const windowEnd = new Date(now.getTime() + 60 * 1000); // 1 minute window

    await prisma.$executeRaw`
      INSERT INTO rate_limits (user_id, ip_address, endpoint, request_count, window_start, window_end, tier)
      VALUES (${check.userId}::uuid, ${check.ipAddress}::inet, ${check.endpoint}, 1, ${now}, ${windowEnd}, ${tier})
    `;
  }

  /**
   * Get rate limit status for a user
   */
  async getRateLimitStatus(userId: string) {
    const { tier, limits } = await this.getTierLimits(userId);

    // Get current usage for each window
    const windows = [
      { duration: 60, limit: limits.requestsPerMinute, name: 'minute' },
      { duration: 3600, limit: limits.requestsPerHour, name: 'hour' },
      { duration: 86400, limit: limits.requestsPerDay, name: 'day' },
    ];

    const usage = await Promise.all(
      windows.map(async (window) => {
        const windowStart = new Date();
        windowStart.setSeconds(windowStart.getSeconds() - window.duration);

        const count = await prisma.$queryRaw<Array<{ total: bigint }>>`
          SELECT COALESCE(SUM(request_count), 0) as total
          FROM rate_limits
          WHERE user_id = ${userId}::uuid
            AND window_start >= ${windowStart}
        `;

        const currentCount = Number(count[0]?.total || 0);

        return {
          window: window.name,
          limit: window.limit,
          used: currentCount,
          remaining: Math.max(0, window.limit - currentCount),
          percentage: Math.min(100, Math.round((currentCount / window.limit) * 100)),
        };
      })
    );

    return {
      tier,
      limits,
      usage,
    };
  }

  /**
   * Clean up old rate limit records
   * Should be run periodically (e.g., hourly cron job)
   */
  async cleanupOldRecords(): Promise<number> {
    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);

    const result = await prisma.$executeRaw`
      DELETE FROM rate_limits
      WHERE window_end < ${sevenDaysAgo}
    `;

    return Number(result);
  }

  /**
   * Unblock a user/IP manually
   */
  async unblock(userId?: string, ipAddress?: string): Promise<void> {
    await prisma.$executeRaw`
      UPDATE rate_limits
      SET blocked_until = NULL
      WHERE (user_id = ${userId}::uuid OR ip_address = ${ipAddress}::inet)
        AND blocked_until IS NOT NULL
    `;

    if (userId) {
      await prisma.securityAuditLog.create({
        data: {
          userId,
          eventType: 'RATE_LIMIT_UNBLOCKED',
          severity: 'INFO',
          success: true,
        },
      });
    }
  }
}

export const rateLimitService = new RateLimitService();
