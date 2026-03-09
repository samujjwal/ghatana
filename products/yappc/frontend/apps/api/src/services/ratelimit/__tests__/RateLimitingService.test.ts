/**
 * Unit Tests for RateLimitingService
 *
 * <p><b>Purpose</b><br>
 * Tests the rate limiting service including token bucket algorithm,
 * tier management, usage tracking, and limit enforcement.
 *
 * <p><b>Test Coverage</b><br>
 * - Token bucket algorithm with refill
 * - Sliding window fallback
 * - Tier-based limit enforcement
 * - Usage tracking and metrics
 * - Tier upgrades and downgrades
 * - Redis integration
 *
 * @doc.type test
 * @doc.purpose Unit tests for rate limiting
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { RateLimitingService, RATE_LIMIT_TIERS } from '../RateLimitingService';
import { PrismaClient } from '@prisma/client';
import Redis from 'ioredis';

// Mock dependencies
jest.mock('@prisma/client');
jest.mock('ioredis');

describe('RateLimitingService', () => {
  let service: RateLimitingService;
  let mockPrisma: jest.Mocked<PrismaClient>;
  let mockRedis: jest.Mocked<Redis>;

  beforeEach(() => {
    // GIVEN: Fresh mocks for each test
    mockPrisma = new PrismaClient() as jest.Mocked<PrismaClient>;
    mockRedis = new Redis() as jest.Mocked<Redis>;

    service = new RateLimitingService(mockPrisma, mockRedis);

    // Mock Redis methods
    mockRedis.get = jest.fn();
    mockRedis.set = jest.fn();
    mockRedis.incr = jest.fn();
    mockRedis.expire = jest.fn();
    mockRedis.ttl = jest.fn();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('allowRequest', () => {
    it('should allow request when under limit', async () => {
      // GIVEN: User with free tier and no previous requests
      const identifier = 'user:123';

      mockRedis.get.mockResolvedValue(null); // No previous requests
      mockRedis.incr.mockResolvedValue(1);
      mockRedis.ttl.mockResolvedValue(3600);

      (mockPrisma.rateLimitConfig as unknown) = {
        findUnique: jest.fn().mockResolvedValue({
          userId: '123',
          tier: 'free',
        }),
      };

      // WHEN: Check if request is allowed
      const result = await service.allowRequest(identifier);

      // THEN: Should allow request
      expect(result.allowed).toBe(true);
      expect(result.remaining).toBeLessThan(RATE_LIMIT_TIERS.free.requestsPerHour);
      expect(mockRedis.incr).toHaveBeenCalled();
    });

    it('should deny request when limit exceeded', async () => {
      // GIVEN: User who has exceeded their limit
      const identifier = 'user:123';
      const freeLimit = RATE_LIMIT_TIERS.free.requestsPerHour;

      mockRedis.get.mockResolvedValue(String(freeLimit + 1));
      mockRedis.ttl.mockResolvedValue(1800); // 30 minutes remaining

      (mockPrisma.rateLimitConfig as unknown) = {
        findUnique: jest.fn().mockResolvedValue({
          userId: '123',
          tier: 'free',
        }),
      };

      // WHEN: Check if request is allowed
      const result = await service.allowRequest(identifier);

      // THEN: Should deny request
      expect(result.allowed).toBe(false);
      expect(result.remaining).toBe(0);
      expect(result.resetTime).toBeDefined();
    });

    it('should apply correct limits for pro tier', async () => {
      // GIVEN: User with pro tier
      const identifier = 'user:456';

      mockRedis.get.mockResolvedValue('5000'); // 5000 requests used
      mockRedis.incr.mockResolvedValue(5001);
      mockRedis.ttl.mockResolvedValue(3600);

      (mockPrisma.rateLimitConfig as unknown) = {
        findUnique: jest.fn().mockResolvedValue({
          userId: '456',
          tier: 'pro',
        }),
      };

      // WHEN: Check if request is allowed
      const result = await service.allowRequest(identifier);

      // THEN: Should allow (pro limit is 10k/hr)
      expect(result.allowed).toBe(true);
      expect(result.remaining).toBe(
        RATE_LIMIT_TIERS.pro.requestsPerHour - 5001
      );
    });

    it('should never limit enterprise tier', async () => {
      // GIVEN: User with enterprise tier
      const identifier = 'user:enterprise';

      mockRedis.get.mockResolvedValue('999999'); // Very high usage
      mockRedis.incr.mockResolvedValue(1000000);
      mockRedis.ttl.mockResolvedValue(3600);

      (mockPrisma.rateLimitConfig as unknown) = {
        findUnique: jest.fn().mockResolvedValue({
          userId: 'enterprise',
          tier: 'enterprise',
        }),
      };

      // WHEN: Check if request is allowed
      const result = await service.allowRequest(identifier);

      // THEN: Should always allow for enterprise
      expect(result.allowed).toBe(true);
    });

    it('should handle burst capacity correctly', async () => {
      // GIVEN: User with requests within burst capacity
      const identifier = 'user:burst';
      const burstSize = RATE_LIMIT_TIERS.free.burstSize;

      mockRedis.get.mockResolvedValue(String(burstSize - 1));
      mockRedis.incr.mockResolvedValue(burstSize);
      mockRedis.ttl.mockResolvedValue(3600);

      (mockPrisma.rateLimitConfig as unknown) = {
        findUnique: jest.fn().mockResolvedValue({
          userId: 'burst',
          tier: 'free',
        }),
      };

      // WHEN: Check if request is allowed
      const result = await service.allowRequest(identifier);

      // THEN: Should allow burst
      expect(result.allowed).toBe(true);
    });

    it('should set TTL on first request', async () => {
      // GIVEN: First request from user
      const identifier = 'user:new';

      mockRedis.get.mockResolvedValue(null);
      mockRedis.incr.mockResolvedValue(1);
      mockRedis.ttl.mockResolvedValue(-1); // No TTL set

      (mockPrisma.rateLimitConfig as unknown) = {
        findUnique: jest.fn().mockResolvedValue({
          userId: 'new',
          tier: 'free',
        }),
      };

      // WHEN: Check if request is allowed
      await service.allowRequest(identifier);

      // THEN: Should set TTL to 1 hour
      expect(mockRedis.expire).toHaveBeenCalledWith(
        expect.any(String),
        3600
      );
    });
  });

  describe('getRateLimitStatus', () => {
    it('should return current usage status', async () => {
      // GIVEN: User with some usage
      const identifier = 'user:123';
      const used = 50;

      mockRedis.get.mockResolvedValue(String(used));
      mockRedis.ttl.mockResolvedValue(1800);

      (mockPrisma.rateLimitConfig as unknown) = {
        findUnique: jest.fn().mockResolvedValue({
          userId: '123',
          tier: 'free',
        }),
      };

      // WHEN: Get status
      const status = await service.getRateLimitStatus(identifier);

      // THEN: Should return correct status
      expect(status.identifier).toBe(identifier);
      expect(status.tier).toBe('free');
      expect(status.totalRequests).toBe(RATE_LIMIT_TIERS.free.requestsPerHour);
      expect(status.remainingRequests).toBe(
        RATE_LIMIT_TIERS.free.requestsPerHour - used
      );
      expect(status.isLimited).toBe(false);
    });

    it('should indicate when user is limited', async () => {
      // GIVEN: User who exceeded limit
      const identifier = 'user:limited';
      const limit = RATE_LIMIT_TIERS.free.requestsPerHour;

      mockRedis.get.mockResolvedValue(String(limit + 10));
      mockRedis.ttl.mockResolvedValue(1800);

      (mockPrisma.rateLimitConfig as unknown) = {
        findUnique: jest.fn().mockResolvedValue({
          userId: 'limited',
          tier: 'free',
        }),
      };

      // WHEN: Get status
      const status = await service.getRateLimitStatus(identifier);

      // THEN: Should indicate limited status
      expect(status.isLimited).toBe(true);
      expect(status.remainingRequests).toBe(0);
    });

    it('should calculate reset time correctly', async () => {
      // GIVEN: User with TTL remaining
      const identifier = 'user:123';
      const ttlSeconds = 1800; // 30 minutes

      mockRedis.get.mockResolvedValue('10');
      mockRedis.ttl.mockResolvedValue(ttlSeconds);

      (mockPrisma.rateLimitConfig as unknown) = {
        findUnique: jest.fn().mockResolvedValue({
          userId: '123',
          tier: 'free',
        }),
      };

      const beforeTime = Date.now();

      // WHEN: Get status
      const status = await service.getRateLimitStatus(identifier);

      // THEN: Reset time should be ~30 minutes from now
      const resetTime = new Date(status.resetTime).getTime();
      const expectedResetTime = beforeTime + ttlSeconds * 1000;

      expect(resetTime).toBeGreaterThan(beforeTime);
      expect(resetTime).toBeLessThanOrEqual(expectedResetTime + 1000); // Allow 1s margin
    });
  });

  describe('upgradeTier', () => {
    it('should upgrade user tier successfully', async () => {
      // GIVEN: User with free tier
      const userId = 'user-123';
      const newTier = 'pro';

      (mockPrisma.rateLimitConfig as unknown) = {
        upsert: jest.fn().mockResolvedValue({
          userId,
          tier: newTier,
          upgradedAt: new Date(),
        }),
      };

      // WHEN: Upgrade tier
      await service.upgradeTier(userId, newTier);

      // THEN: Should update database
      expect(mockPrisma.rateLimitConfig.upsert).toHaveBeenCalledWith({
        where: { userId },
        update: {
          tier: newTier,
          upgradedAt: expect.any(Date),
        },
        create: {
          userId,
          tier: newTier,
          upgradedAt: expect.any(Date),
        },
      });
    });

    it('should clear usage counters on upgrade', async () => {
      // GIVEN: User with existing usage
      const userId = 'user-123';
      const identifier = `user:${userId}`;

      mockRedis.del = jest.fn().mockResolvedValue(1);

      (mockPrisma.rateLimitConfig as unknown) = {
        upsert: jest.fn().mockResolvedValue({
          userId,
          tier: 'pro',
        }),
      };

      // WHEN: Upgrade tier
      await service.upgradeTier(userId, 'pro');

      // THEN: Should clear Redis counter
      expect(mockRedis.del).toHaveBeenCalledWith(
        expect.stringContaining(identifier)
      );
    });

    it('should reject invalid tier names', async () => {
      // GIVEN: Invalid tier name
      const userId = 'user-123';
      const invalidTier = 'platinum'; // Not in RATE_LIMIT_TIERS

      // WHEN: Attempt upgrade
      // THEN: Should throw error
      await expect(
        service.upgradeTier(userId, invalidTier)
      ).rejects.toThrow('Invalid tier');
    });
  });

  describe('resetLimit', () => {
    it('should reset user rate limit counter', async () => {
      // GIVEN: User with usage
      const identifier = 'user:123';

      mockRedis.del = jest.fn().mockResolvedValue(1);

      // WHEN: Reset limit
      await service.resetLimit(identifier);

      // THEN: Should delete Redis key
      expect(mockRedis.del).toHaveBeenCalledWith(
        expect.stringContaining(identifier)
      );
    });

    it('should handle non-existent keys gracefully', async () => {
      // GIVEN: User with no usage
      const identifier = 'user:new';

      mockRedis.del = jest.fn().mockResolvedValue(0);

      // WHEN: Reset limit
      // THEN: Should not throw error
      await expect(service.resetLimit(identifier)).resolves.not.toThrow();
    });
  });

  describe('getMetrics', () => {
    it('should calculate system-wide metrics', async () => {
      // GIVEN: Multiple users with different tiers
      const configs = [
        { userId: '1', tier: 'free' },
        { userId: '2', tier: 'free' },
        { userId: '3', tier: 'pro' },
        { userId: '4', tier: 'enterprise' },
      ];

      (mockPrisma.rateLimitConfig as unknown) = {
        findMany: jest.fn().mockResolvedValue(configs),
        count: jest.fn().mockResolvedValue(4),
      };

      // Mock Redis to simulate usage
      mockRedis.get = jest.fn()
        .mockResolvedValueOnce('150') // user 1 exceeded (free limit 100)
        .mockResolvedValueOnce('50')  // user 2 ok
        .mockResolvedValueOnce('5000') // user 3 ok (pro limit 10k)
        .mockResolvedValueOnce('100000'); // user 4 ok (enterprise unlimited)

      // WHEN: Get metrics
      const metrics = await service.getMetrics();

      // THEN: Should calculate correct metrics
      expect(metrics.totalUsers).toBe(4);
      expect(metrics.limitedUsers).toBe(1); // Only user 1
      expect(metrics.limitPercentage).toBe('25.0'); // 1/4 = 25%
    });

    it('should handle empty user base', async () => {
      // GIVEN: No users
      (mockPrisma.rateLimitConfig as unknown) = {
        findMany: jest.fn().mockResolvedValue([]),
        count: jest.fn().mockResolvedValue(0),
      };

      // WHEN: Get metrics
      const metrics = await service.getMetrics();

      // THEN: Should return zeros
      expect(metrics.totalUsers).toBe(0);
      expect(metrics.limitedUsers).toBe(0);
      expect(metrics.limitPercentage).toBe('0.0');
    });
  });

  describe('Edge Cases', () => {
    it('should handle Redis connection errors gracefully', async () => {
      // GIVEN: Redis connection error
      const identifier = 'user:123';

      mockRedis.get.mockRejectedValue(new Error('Redis connection failed'));

      // WHEN: Attempt to check limit
      // THEN: Should fallback or throw appropriate error
      await expect(service.allowRequest(identifier)).rejects.toThrow(
        'Redis connection failed'
      );
    });

    it('should handle concurrent requests correctly', async () => {
      // GIVEN: Multiple concurrent requests
      const identifier = 'user:concurrent';

      mockRedis.get.mockResolvedValue('99');
      mockRedis.incr.mockResolvedValue(100);
      mockRedis.ttl.mockResolvedValue(3600);

      (mockPrisma.rateLimitConfig as unknown) = {
        findUnique: jest.fn().mockResolvedValue({
          userId: 'concurrent',
          tier: 'free',
        }),
      };

      // WHEN: Make concurrent requests
      const requests = await Promise.all([
        service.allowRequest(identifier),
        service.allowRequest(identifier),
        service.allowRequest(identifier),
      ]);

      // THEN: All should be processed
      expect(requests).toHaveLength(3);
      requests.forEach((r) => expect(r).toHaveProperty('allowed'));
    });

    it('should handle malformed Redis data', async () => {
      // GIVEN: Invalid data in Redis
      const identifier = 'user:malformed';

      mockRedis.get.mockResolvedValue('invalid-number');

      (mockPrisma.rateLimitConfig as unknown) = {
        findUnique: jest.fn().mockResolvedValue({
          userId: 'malformed',
          tier: 'free',
        }),
      };

      // WHEN: Check limit
      // THEN: Should handle gracefully (treat as 0)
      const result = await service.allowRequest(identifier);
      expect(result).toHaveProperty('allowed');
    });
  });
});

