/**
 * Unit Tests for RateLimitResolver
 *
 * <p><b>Purpose</b><br>
 * Tests the GraphQL resolvers for rate limiting including queries,
 * mutations, error handling, and authorization.
 *
 * <p><b>Test Coverage</b><br>
 * - Query resolvers (status, config, tiers, metrics)
 * - Mutation resolvers (upgrade, reset, downgrade)
 * - Authorization checks
 * - Error handling
 * - Input validation
 *
 * @doc.type test
 * @doc.purpose Unit tests for rate limit GraphQL resolvers
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { RateLimitResolver } from '../RateLimitResolver';
import { PrismaClient } from '@prisma/client';
import { RateLimitingService } from '../../services/ratelimit/RateLimitingService';

// Mock dependencies
jest.mock('@prisma/client');
jest.mock('../../services/ratelimit/RateLimitingService');

describe('RateLimitResolver', () => {
  let resolver: RateLimitResolver;
  let mockPrisma: jest.Mocked<PrismaClient>;
  let mockRateLimitService: jest.Mocked<RateLimitingService>;
  let mockContext: unknown;

  beforeEach(() => {
    // GIVEN: Fresh mocks and resolver instance
    mockPrisma = new PrismaClient() as jest.Mocked<PrismaClient>;
    mockRateLimitService = new RateLimitingService() as jest.Mocked<RateLimitingService>;

    resolver = new RateLimitResolver(mockRateLimitService, mockPrisma);

    mockContext = {
      userId: 'user-123',
      user: { id: 'user-123', email: 'user@example.com' },
    };
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('Query Resolvers', () => {
    describe('myRateLimitStatus', () => {
      it('should return current user rate limit status', async () => {
        // GIVEN: Authenticated user
        const status = {
          identifier: 'user:user-123',
          tier: 'free',
          used: 50,
          limit: 100,
          remaining: 50,
          percentage: 50,
          resetTime: new Date(),
          isLimited: false,
          lastRequestAt: new Date(),
        };

        mockRateLimitService.getRateLimitStatus = jest
          .fn()
          .mockResolvedValue(status);

        // WHEN: Query myRateLimitStatus
        const result = await resolver.Query.myRateLimitStatus(
          {},
          {},
          mockContext
        );

        // THEN: Should return user's status
        expect(result).toBeDefined();
        expect(result.tier).toBe('free');
        expect(result.percentage).toBe(50);
      });

      it('should throw error when user not authenticated', async () => {
        // GIVEN: No user context
        const unauthenticatedContext = {};

        // WHEN: Query myRateLimitStatus
        // THEN: Should throw authentication error
        await expect(
          resolver.Query.myRateLimitStatus({}, {}, unauthenticatedContext)
        ).rejects.toThrow('User not authenticated');
      });
    });

    describe('rateLimitStatus', () => {
      it('should return status for given identifier', async () => {
        // GIVEN: Identifier
        const identifier = 'user:user-456';
        const status = {
          identifier,
          tier: 'pro',
          used: 5000,
          limit: 10000,
          remaining: 5000,
          percentage: 50,
          resetTime: new Date(),
          isLimited: false,
        };

        mockRateLimitService.getRateLimitStatus = jest
          .fn()
          .mockResolvedValue(status);

        // WHEN: Query rateLimitStatus
        const result = await resolver.Query.rateLimitStatus({}, { identifier });

        // THEN: Should return status
        expect(result.identifier).toBe(identifier);
        expect(result.tier).toBe('pro');
      });
    });

    describe('rateLimitTiers', () => {
      it('should return all available tiers', async () => {
        // WHEN: Query rateLimitTiers
        const result = await resolver.Query.rateLimitTiers();

        // THEN: Should return all tiers
        expect(result).toBeInstanceOf(Array);
        expect(result.length).toBeGreaterThan(0);
        expect(result[0]).toHaveProperty('name');
        expect(result[0]).toHaveProperty('requestsPerHour');
      });

      it('should include tier features', async () => {
        // WHEN: Query rateLimitTiers
        const result = await resolver.Query.rateLimitTiers();

        // THEN: Should include features
        result.forEach((tier) => {
          expect(tier.features).toBeInstanceOf(Array);
          expect(tier.features.length).toBeGreaterThan(0);
        });
      });
    });

    describe('rateLimitTier', () => {
      it('should return info for specific tier', async () => {
        // WHEN: Query rateLimitTier for 'PRO'
        const result = await resolver.Query.rateLimitTier({}, { name: 'PRO' });

        // THEN: Should return tier info
        expect(result.name).toMatch(/pro/i);
        expect(result.requestsPerHour).toBe(10000);
        expect(result.monthlyCost).toBe(29);
      });

      it('should throw error for invalid tier', async () => {
        // WHEN: Query rateLimitTier for invalid tier
        // THEN: Should throw error
        await expect(
          resolver.Query.rateLimitTier({}, { name: 'INVALID' })
        ).rejects.toThrow('Unknown tier');
      });
    });

    describe('rateLimitMetrics', () => {
      it('should return system metrics', async () => {
        // GIVEN: Metrics available
        mockRateLimitService.getMetrics = jest.fn().mockResolvedValue({
          totalUsers: 100,
          totalRequests: 50000,
          limitedUsers: 5,
          limitPercentage: '5.0',
        });

        // WHEN: Query rateLimitMetrics
        const result = await resolver.Query.rateLimitMetrics();

        // THEN: Should return metrics
        expect(result.totalActiveUsers).toBe(100);
        expect(result.limitedUsers).toBe(5);
        expect(result.limitPercentage).toBe(5);
      });
    });
  });

  describe('Mutation Resolvers', () => {
    describe('requestUpgrade', () => {
      it('should create upgrade request', async () => {
        // GIVEN: Authenticated user requesting upgrade
        (mockPrisma.rateLimitConfig as unknown) = {
          findUnique: jest
            .fn()
            .mockResolvedValue({ userId: 'user-123', tier: 'free' }),
        };

        (mockPrisma.upgradeRequest as unknown) = {
          create: jest.fn().mockResolvedValue({
            id: 'upgrade-123',
            userId: 'user-123',
            requestedTier: 'pro',
            currentTier: 'free',
            status: 'pending',
            createdAt: new Date(),
          }),
        };

        // WHEN: Request upgrade
        const result = await resolver.Mutation.requestUpgrade(
          {},
          { requestedTier: 'pro' },
          mockContext
        );

        // THEN: Should create request
        expect(result.status).toBe('pending');
        expect(result.requestedTier).toBe('pro');
      });

      it('should throw error when not authenticated', async () => {
        // GIVEN: No authentication
        // WHEN: Request upgrade
        // THEN: Should throw error
        await expect(
          resolver.Mutation.requestUpgrade(
            {},
            { requestedTier: 'pro' },
            {}
          )
        ).rejects.toThrow('User not authenticated');
      });
    });

    describe('approveUpgrade', () => {
      it('should approve upgrade request', async () => {
        // GIVEN: Pending upgrade request
        (mockPrisma.upgradeRequest as unknown) = {
          findUnique: jest.fn().mockResolvedValue({
            id: 'upgrade-123',
            userId: 'user-123',
            requestedTier: 'pro',
            status: 'pending',
          }),
          update: jest.fn().mockResolvedValue({
            id: 'upgrade-123',
            status: 'approved',
          }),
        };

        mockRateLimitService.upgradeTier = jest.fn();

        (mockPrisma.rateLimitConfig as unknown) = {
          findUnique: jest.fn().mockResolvedValue({
            userId: 'user-123',
            tier: 'pro',
            upgradedAt: new Date(),
          }),
        };

        // WHEN: Approve upgrade
        const result = await resolver.Mutation.approveUpgrade(
          {},
          { requestId: 'upgrade-123' }
        );

        // THEN: Should upgrade tier
        expect(mockRateLimitService.upgradeTier).toHaveBeenCalledWith(
          'user-123',
          'pro'
        );
      });
    });

    describe('resetRateLimit', () => {
      it('should reset user rate limit', async () => {
        // GIVEN: User with usage
        mockRateLimitService.resetLimit = jest.fn();
        mockRateLimitService.getRateLimitStatus = jest.fn().mockResolvedValue({
          identifier: 'user:user-123',
          tier: 'free',
          used: 0,
          limit: 100,
          remaining: 100,
          percentage: 0,
          resetTime: new Date(),
          isLimited: false,
        });

        // WHEN: Reset limit
        const result = await resolver.Mutation.resetRateLimit(
          {},
          { userId: 'user-123' }
        );

        // THEN: Should reset
        expect(mockRateLimitService.resetLimit).toHaveBeenCalled();
        expect(result.remaining).toBe(100);
      });
    });

    describe('downgradeToFree', () => {
      it('should downgrade user to free tier', async () => {
        // GIVEN: Pro tier user
        mockRateLimitService.upgradeTier = jest.fn();

        (mockPrisma.rateLimitConfig as unknown) = {
          findUnique: jest.fn().mockResolvedValue({
            userId: 'user-123',
            tier: 'free',
            upgradedAt: new Date(),
          }),
        };

        // WHEN: Downgrade
        const result = await resolver.Mutation.downgradeToFree(
          {},
          {},
          mockContext
        );

        // THEN: Should downgrade
        expect(mockRateLimitService.upgradeTier).toHaveBeenCalledWith(
          'user-123',
          'free'
        );
        expect(result.tier).toBe('free');
      });

      it('should throw error when not authenticated', async () => {
        // GIVEN: No authentication
        // WHEN: Downgrade
        // THEN: Should throw error
        await expect(
          resolver.Mutation.downgradeToFree({}, {}, {})
        ).rejects.toThrow('User not authenticated');
      });
    });
  });

  describe('Error Handling', () => {
    it('should handle service errors gracefully', async () => {
      // GIVEN: Service throws error
      mockRateLimitService.getRateLimitStatus = jest
        .fn()
        .mockRejectedValue(new Error('Service error'));

      // WHEN: Query status
      // THEN: Should throw with descriptive message
      await expect(
        resolver.Query.rateLimitStatus({}, { identifier: 'user:123' })
      ).rejects.toThrow();
    });

    it('should handle database errors gracefully', async () => {
      // GIVEN: Database error
      (mockPrisma.rateLimitConfig as unknown) = {
        findUnique: jest
          .fn()
          .mockRejectedValue(new Error('Database error')),
      };

      // WHEN: Query config
      // THEN: Should throw error
      await expect(
        resolver.Query.rateLimitConfig({}, { userId: 'user-123' })
      ).rejects.toThrow();
    });
  });
});

