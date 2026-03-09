/**
 * Rate Limit GraphQL Resolver
 *
 * <p><b>Purpose</b><br>
 * GraphQL resolvers for rate limit queries and mutations including status
 * tracking, tier management, and upgrade requests.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const resolvers = new RateLimitResolver(rateLimitService, prisma);
 * const result = await resolvers.Query.myRateLimitStatus(_, {}, context);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose GraphQL resolvers for rate limiting
 * @doc.layer product
 * @doc.pattern Resolver
 */

import { PrismaClient } from '@prisma/client';
import { RateLimitingService, RATE_LIMIT_TIERS } from '../../services/ratelimit/RateLimitingService';

/**
 * RateLimitResolver handles rate limit GraphQL operations
 */
export class RateLimitResolver {
  /**
   * Creates a new RateLimitResolver instance.
   *
   * @param rateLimitService - Rate limiting service
   * @param prisma - Prisma client
   */
  constructor(
    private rateLimitService: RateLimitingService,
    private prisma: PrismaClient
  ) { }

  /**
   * Query resolvers object
   */
  Query = {
    /**
     * Get current user's rate limit status
     */
    myRateLimitStatus: async (
      _: unknown,
      __: unknown,
      context: unknown
    ) => {
      try {
        const userId = context.userId || context.user?.id;
        if (!userId) {
          throw new Error('User not authenticated');
        }

        const status = await this.rateLimitService.getRateLimitStatus(`user:${userId}`);
        return {
          identifier: status.identifier,
          tier: status.tier,
          used: status.totalRequests - status.remainingRequests,
          limit: status.totalRequests,
          remaining: status.remainingRequests,
          percentage: ((status.totalRequests - status.remainingRequests) / status.totalRequests) * 100,
          resetTime: status.resetTime,
          isLimited: status.isLimited,
          lastRequestAt: status.lastRequestAt,
          statusColor: this.getStatusColor((status.totalRequests - status.remainingRequests) / status.totalRequests),
          statusLabel: this.getStatusLabel((status.totalRequests - status.remainingRequests) / status.totalRequests),
        };
      } catch (error) {
        throw new Error(
          `Failed to fetch rate limit status: ${error instanceof Error ? error.message : 'unknown error'
          }`
        );
      }
    },

    /**
     * Get rate limit configuration for a user
     */
    rateLimitConfig: async (
      _: unknown,
      { userId }: { userId: string }
    ) => {
      try {
        const config = await this.prisma.rateLimitConfig.findUnique({
          where: { userId },
        });

        if (!config) {
          return null;
        }

        const tierConfig = RATE_LIMIT_TIERS[config.tier] || RATE_LIMIT_TIERS['free'];

        return {
          userId: config.userId,
          tier: config.tier,
          requestsPerHour: tierConfig.requestsPerHour,
          requestsPerDay: tierConfig.requestsPerDay,
          remainingHourly: 0,
          remainingDaily: 0,
          resetTime: new Date(),
          upgradedAt: config.upgradedAt,
        };
      } catch (error) {
        throw new Error(
          `Failed to fetch rate limit config: ${error instanceof Error ? error.message : 'unknown error'
          }`
        );
      }
    },

    /**
     * Get rate limit status for identifier
     */
    rateLimitStatus: async (
      _: unknown,
      { identifier }: { identifier: string }
    ) => {
      try {
        const status = await this.rateLimitService.getRateLimitStatus(identifier);
        return {
          identifier: status.identifier,
          tier: status.tier,
          used: status.totalRequests - status.remainingRequests,
          limit: status.totalRequests,
          remaining: status.remainingRequests,
          percentage: ((status.totalRequests - status.remainingRequests) / status.totalRequests) * 100,
          resetTime: status.resetTime,
          isLimited: status.isLimited,
          lastRequestAt: status.lastRequestAt,
          statusColor: this.getStatusColor((status.totalRequests - status.remainingRequests) / status.totalRequests),
          statusLabel: this.getStatusLabel((status.totalRequests - status.remainingRequests) / status.totalRequests),
        };
      } catch (error) {
        throw new Error(
          `Failed to fetch rate limit status: ${error instanceof Error ? error.message : 'unknown error'
          }`
        );
      }
    },

    /**
     * List available rate limit tiers
     */
    rateLimitTiers: async () => {
      return Object.entries(RATE_LIMIT_TIERS).map(([name, tier]) => ({
        name: name.charAt(0).toUpperCase() + name.slice(1),
        description: tier.name,
        requestsPerHour: tier.requestsPerHour,
        requestsPerDay: tier.requestsPerDay,
        burstSize: tier.burstSize,
        monthlyCost: this.getTierCost(name),
        features: this.getTierFeatures(name),
      }));
    },

    /**
     * Get tier info by name
     */
    rateLimitTier: async (
      _: unknown,
      { name }: { name: string }
    ) => {
      const tierKey = name.toLowerCase();
      const tier = RATE_LIMIT_TIERS[tierKey];

      if (!tier) {
        throw new Error(`Unknown tier: ${name}`);
      }

      return {
        name: name.charAt(0).toUpperCase() + name.slice(1),
        description: tier.name,
        requestsPerHour: tier.requestsPerHour,
        requestsPerDay: tier.requestsPerDay,
        burstSize: tier.burstSize,
        monthlyCost: this.getTierCost(tierKey),
        features: this.getTierFeatures(tierKey),
      };
    },

    /**
     * Get rate limiting metrics
     */
    rateLimitMetrics: async () => {
      try {
        const metrics = await this.rateLimitService.getMetrics();
        return {
          totalActiveUsers: (metrics as unknown).totalUsers || 0,
          totalRequests: (metrics as unknown).totalRequests || 0,
          limitedUsers: (metrics as unknown).limitedUsers || 0,
          limitPercentage: parseFloat((metrics as unknown).limitPercentage || '0'),
          averageRequestsPerUser: 0,
          peakRequestsPerSecond: 0,
        };
      } catch (error) {
        throw new Error(
          `Failed to fetch metrics: ${error instanceof Error ? error.message : 'unknown error'
          }`
        );
      }
    },

    /**
     * Get all upgrade requests
     */
    upgradeRequests: async (
      _: unknown,
      { status, limit = 10 }: { status?: string; limit?: number }
    ) => {
      try {
        const where: Record<string, unknown> = {};
        if (status) where.status = status;

        const requests = await this.prisma.upgradeRequest.findMany({
          where,
          orderBy: { createdAt: 'desc' },
          take: limit,
        });

        return requests.map((r) => ({
          id: r.id,
          userId: r.userId,
          requestedTier: r.requestedTier,
          currentTier: r.currentTier,
          status: r.status,
          createdAt: r.createdAt,
          processedAt: r.processedAt,
        }));
      } catch (error) {
        throw new Error(
          `Failed to fetch upgrade requests: ${error instanceof Error ? error.message : 'unknown error'
          }`
        );
      }
    },
  };

  /**
   * Mutation resolvers object
   */
  Mutation = {
    /**
     * Request a tier upgrade
     */
    requestUpgrade: async (
      _: unknown,
      { requestedTier }: { requestedTier: string },
      context: unknown
    ) => {
      try {
        const userId = context.userId || context.user?.id;
        if (!userId) {
          throw new Error('User not authenticated');
        }

        const currentConfig = await this.prisma.rateLimitConfig.findUnique({
          where: { userId },
        });

        const request = await this.prisma.upgradeRequest.create({
          data: {
            id: `upgrade-${Date.now()}`,
            userId,
            requestedTier,
            tier: requestedTier,
            currentTier: currentConfig?.tier || 'free',
            status: 'pending',
            createdAt: new Date(),
          },
        });

        return request;
      } catch (error) {
        throw new Error(
          `Failed to request upgrade: ${error instanceof Error ? error.message : 'unknown error'
          }`
        );
      }
    },

    /**
     * Approve tier upgrade
     */
    approveUpgrade: async (
      _: unknown,
      { requestId }: { requestId: string }
    ) => {
      try {
        const request = await this.prisma.upgradeRequest.findUnique({
          where: { id: requestId },
        });

        if (!request) {
          throw new Error('Upgrade request not found');
        }

        // Upgrade user
        await this.rateLimitService.upgradeTier(request.userId, request.requestedTier);

        // Mark request as approved
        await this.prisma.upgradeRequest.update({
          where: { id: requestId },
          data: {
            status: 'approved',
            processedAt: new Date(),
          },
        });

        const config = await this.prisma.rateLimitConfig.findUnique({
          where: { userId: request.userId },
        });

        const tierConfig = RATE_LIMIT_TIERS[config?.tier || 'free'] || RATE_LIMIT_TIERS['free'];

        return {
          userId: config?.userId,
          tier: config?.tier,
          requestsPerHour: tierConfig.requestsPerHour,
          requestsPerDay: tierConfig.requestsPerDay,
          remainingHourly: tierConfig.requestsPerHour,
          remainingDaily: tierConfig.requestsPerDay,
          resetTime: new Date(),
          upgradedAt: config?.upgradedAt,
        };
      } catch (error) {
        throw new Error(
          `Failed to approve upgrade: ${error instanceof Error ? error.message : 'unknown error'
          }`
        );
      }
    },

    /**
     * Reject tier upgrade
     */
    rejectUpgrade: async (
      _: unknown,
      { requestId, reason }: { requestId: string; reason?: string }
    ) => {
      try {
        const request = await this.prisma.upgradeRequest.update({
          where: { id: requestId },
          data: {
            status: 'rejected',
            processedAt: new Date(),
          },
        });

        return request;
      } catch (error) {
        throw new Error(
          `Failed to reject upgrade: ${error instanceof Error ? error.message : 'unknown error'
          }`
        );
      }
    },

    /**
     * Manually upgrade user tier
     */
    upgradeUserTier: async (
      _: unknown,
      { userId, tier }: { userId: string; tier: string }
    ) => {
      try {
        await this.rateLimitService.upgradeTier(userId, tier);

        const config = await this.prisma.rateLimitConfig.findUnique({
          where: { userId },
        });

        const tierConfig = RATE_LIMIT_TIERS[config?.tier || tier] || RATE_LIMIT_TIERS['free'];

        return {
          userId: config?.userId,
          tier: config?.tier,
          requestsPerHour: tierConfig.requestsPerHour,
          requestsPerDay: tierConfig.requestsPerDay,
          remainingHourly: tierConfig.requestsPerHour,
          remainingDaily: tierConfig.requestsPerDay,
          resetTime: new Date(),
          upgradedAt: config?.upgradedAt,
        };
      } catch (error) {
        throw new Error(
          `Failed to upgrade user tier: ${error instanceof Error ? error.message : 'unknown error'
          }`
        );
      }
    },

    /**
     * Reset user rate limit
     */
    resetRateLimit: async (
      _: unknown,
      { userId }: { userId: string }
    ) => {
      try {
        await this.rateLimitService.resetLimit(`user:${userId}`);

        const status = await this.rateLimitService.getRateLimitStatus(`user:${userId}`);
        return {
          identifier: status.identifier,
          tier: status.tier,
          used: 0,
          limit: status.totalRequests,
          remaining: status.totalRequests,
          percentage: 0,
          resetTime: status.resetTime,
          isLimited: false,
          lastRequestAt: null,
          statusColor: 'green',
          statusLabel: 'Low',
        };
      } catch (error) {
        throw new Error(
          `Failed to reset rate limit: ${error instanceof Error ? error.message : 'unknown error'
          }`
        );
      }
    },

    /**
     * Downgrade to free tier
     */
    downgradeToFree: async (
      _: unknown,
      __: unknown,
      context: unknown
    ) => {
      try {
        const userId = context.userId || context.user?.id;
        if (!userId) {
          throw new Error('User not authenticated');
        }

        await this.rateLimitService.upgradeTier(userId, 'free');

        const config = await this.prisma.rateLimitConfig.findUnique({
          where: { userId },
        });

        const tierConfig = RATE_LIMIT_TIERS['free'];

        return {
          userId: config?.userId,
          tier: 'free',
          requestsPerHour: tierConfig.requestsPerHour,
          requestsPerDay: tierConfig.requestsPerDay,
          remainingHourly: tierConfig.requestsPerHour,
          remainingDaily: tierConfig.requestsPerDay,
          resetTime: new Date(),
          upgradedAt: config?.upgradedAt,
        };
      } catch (error) {
        throw new Error(
          `Failed to downgrade tier: ${error instanceof Error ? error.message : 'unknown error'
          }`
        );
      }
    },
  };

  /**
   * Gets tier cost
   *
   * @private
   */
  private getTierCost(tier: string): number {
    const costs: { [key: string]: number } = {
      free: 0,
      pro: 29,
      enterprise: 999,
    };
    return costs[tier] || 0;
  }

  /**
   * Gets tier features
   *
   * @private
   */
  private getTierFeatures(tier: string): string[] {
    const features: { [key: string]: string[] } = {
      free: [
        '100 requests/hour',
        '500 requests/day',
        'Basic support',
      ],
      pro: [
        '10,000 requests/hour',
        '100,000 requests/day',
        'Email support',
        'Rate limit monitoring',
      ],
      enterprise: [
        'Unlimited requests',
        'Priority support',
        'SLA guarantee',
        'Custom integrations',
      ],
    };
    return features[tier] || [];
  }

  /**
   * Gets status color based on percentage
   *
   * @private
   */
  private getStatusColor(percentage: number): string {
    if (percentage >= 0.9) return 'red';
    if (percentage >= 0.7) return 'orange';
    if (percentage >= 0.5) return 'yellow';
    return 'green';
  }

  /**
   * Gets status label based on percentage
   *
   * @private
   */
  private getStatusLabel(percentage: number): string {
    if (percentage >= 1.0) return 'Limited';
    if (percentage >= 0.9) return 'Critical';
    if (percentage >= 0.7) return 'High';
    if (percentage >= 0.5) return 'Medium';
    return 'Low';
  }
}

