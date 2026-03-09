import { RateLimiter, createRateLimiter } from '../../../src/security/RateLimiter';
import { RateLimitError } from '../../../src/errors/ConnectorErrors';

describe('RateLimiter', () => {
  let limiter: RateLimiter;

  afterEach(() => {
    if (limiter) {
      limiter.destroy();
    }
  });

  describe('Constructor & Configuration', () => {
    it('should initialize with required config', () => {
      limiter = new RateLimiter({
        maxRequests: 10,
        windowMs: 1000,
      });
      expect(limiter).toBeDefined();
    });

    it('should apply default strategy (sliding-window)', async () => {
      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
      });

      // Should use sliding window by default
      for (let i = 0; i < 5; i++) {
        expect(await limiter.check()).toBe(true);
      }
      expect(await limiter.check()).toBe(false);
    });

    it('should accept custom strategy', () => {
      limiter = new RateLimiter({
        maxRequests: 10,
        windowMs: 1000,
        strategy: 'token-bucket',
      });
      expect(limiter).toBeDefined();
    });

    it('should accept custom keyGenerator', async () => {
      const keyGen = jest.fn((ctx) => ctx?.userId || 'default');
      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
        keyGenerator: keyGen,
      });

      await limiter.check({ userId: 'user1' });
      expect(keyGen).toHaveBeenCalledWith({ userId: 'user1' });
    });

    it('should accept custom skip function', async () => {
      limiter = new RateLimiter({
        maxRequests: 1,
        windowMs: 1000,
        skip: (ctx) => ctx?.bypass === true,
      });

      // Should skip rate limiting
      expect(await limiter.check({ bypass: true })).toBe(true);
      expect(await limiter.check({ bypass: true })).toBe(true);

      // Should not skip
      expect(await limiter.check({ bypass: false })).toBe(true);
      expect(await limiter.check({ bypass: false })).toBe(false);
    });

    it('should accept custom message', async () => {
      const customMessage = 'Custom rate limit message';
      limiter = new RateLimiter({
        maxRequests: 1,
        windowMs: 1000,
        message: customMessage,
      });

      await limiter.consume();

      try {
        await limiter.consume();
        fail('Should have thrown RateLimitError');
      } catch (error) {
        expect(error).toBeInstanceOf(RateLimitError);
        expect((error as RateLimitError).message).toBe(customMessage);
      }
    });

    it('should use default keyGenerator when not provided', async () => {
      limiter = new RateLimiter({
        maxRequests: 2,
        windowMs: 1000,
      });

      expect(await limiter.check()).toBe(true);
      expect(await limiter.check()).toBe(true);
      expect(await limiter.check()).toBe(false);
    });
  });

  describe('Fixed Window Strategy', () => {
    beforeEach(() => {
      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
        strategy: 'fixed-window',
      });
    });

    it('should allow requests up to limit', async () => {
      for (let i = 0; i < 5; i++) {
        expect(await limiter.check()).toBe(true);
      }
    });

    it('should block requests over limit', async () => {
      for (let i = 0; i < 5; i++) {
        await limiter.check();
      }
      expect(await limiter.check()).toBe(false);
    });

    it('should reset at window boundary', async () => {
      jest.useFakeTimers();

      // Use up limit
      for (let i = 0; i < 5; i++) {
        await limiter.check();
      }
      expect(await limiter.check()).toBe(false);

      // Advance to next window
      jest.advanceTimersByTime(1001);

      // Should allow again
      expect(await limiter.check()).toBe(true);

      jest.useRealTimers();
    });

    it('should track multiple keys separately', async () => {
      limiter = new RateLimiter({
        maxRequests: 2,
        windowMs: 1000,
        strategy: 'fixed-window',
        keyGenerator: (ctx) => ctx?.key,
      });

      expect(await limiter.check({ key: 'user1' })).toBe(true);
      expect(await limiter.check({ key: 'user1' })).toBe(true);
      expect(await limiter.check({ key: 'user1' })).toBe(false);

      // Different key should have separate limit
      expect(await limiter.check({ key: 'user2' })).toBe(true);
      expect(await limiter.check({ key: 'user2' })).toBe(true);
    });
  });

  describe('Sliding Window Strategy', () => {
    beforeEach(() => {
      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
        strategy: 'sliding-window',
      });
    });

    it('should allow requests up to limit', async () => {
      for (let i = 0; i < 5; i++) {
        expect(await limiter.check()).toBe(true);
      }
    });

    it('should block requests over limit', async () => {
      for (let i = 0; i < 5; i++) {
        await limiter.check();
      }
      expect(await limiter.check()).toBe(false);
    });

    it('should allow requests after window passes', async () => {
      jest.useFakeTimers();

      await limiter.check();

      // Advance time beyond window
      jest.advanceTimersByTime(1001);

      // Should have 5 available again
      for (let i = 0; i < 5; i++) {
        expect(await limiter.check()).toBe(true);
      }

      jest.useRealTimers();
    });

    it('should implement rolling window correctly', async () => {
      jest.useFakeTimers();
      const startTime = Date.now();
      jest.setSystemTime(startTime);

      // Use 3 requests at start
      await limiter.check();
      await limiter.check();
      await limiter.check();

      // Advance 500ms (still in window)
      jest.advanceTimersByTime(500);

      // Use 2 more (now at limit)
      await limiter.check();
      await limiter.check();
      expect(await limiter.check()).toBe(false);

      // Advance another 501ms (1001ms total - first 3 should expire)
      jest.advanceTimersByTime(501);

      // Should have 3 available again
      expect(await limiter.check()).toBe(true);
      expect(await limiter.check()).toBe(true);
      expect(await limiter.check()).toBe(true);
      expect(await limiter.check()).toBe(false);

      jest.useRealTimers();
    });

    it('should track multiple keys separately', async () => {
      limiter = new RateLimiter({
        maxRequests: 3,
        windowMs: 1000,
        strategy: 'sliding-window',
        keyGenerator: (ctx) => ctx?.userId,
      });

      // User 1 uses up limit
      for (let i = 0; i < 3; i++) {
        await limiter.check({ userId: 'user1' });
      }
      expect(await limiter.check({ userId: 'user1' })).toBe(false);

      // User 2 should have separate limit
      expect(await limiter.check({ userId: 'user2' })).toBe(true);
    });
  });

  describe('Token Bucket Strategy', () => {
    beforeEach(() => {
      jest.useFakeTimers();
      limiter = new RateLimiter({
        maxRequests: 10,
        windowMs: 1000,
        strategy: 'token-bucket',
      });
    });

    afterEach(() => {
      jest.useRealTimers();
    });

    it('should start with full bucket', async () => {
      for (let i = 0; i < 10; i++) {
        expect(await limiter.check()).toBe(true);
      }
      expect(await limiter.check()).toBe(false);
    });

    it('should refill tokens over time', async () => {
      // Use all tokens
      for (let i = 0; i < 10; i++) {
        await limiter.check();
      }
      expect(await limiter.check()).toBe(false);

      // Advance time to refill 5 tokens (500ms at 10 tokens/1000ms)
      jest.advanceTimersByTime(500);

      // Should have ~5 tokens available
      expect(await limiter.check()).toBe(true);
      expect(await limiter.check()).toBe(true);
      expect(await limiter.check()).toBe(true);
      expect(await limiter.check()).toBe(true);
      expect(await limiter.check()).toBe(true);
      expect(await limiter.check()).toBe(false);
    });

    it('should cap tokens at maximum', async () => {
      // Wait long time
      jest.advanceTimersByTime(10000);

      // Should only have 10 tokens, not more
      for (let i = 0; i < 10; i++) {
        expect(await limiter.check()).toBe(true);
      }
      expect(await limiter.check()).toBe(false);
    });

    it('should handle concurrent requests', async () => {
      const results = await Promise.all([
        limiter.check(),
        limiter.check(),
        limiter.check(),
      ]);

      expect(results.every(r => r === true)).toBe(true);
    });
  });

  describe('consume() method', () => {
    beforeEach(() => {
      limiter = new RateLimiter({
        maxRequests: 2,
        windowMs: 1000,
      });
    });

    it('should allow requests within limit', async () => {
      await expect(limiter.consume()).resolves.toBeUndefined();
      await expect(limiter.consume()).resolves.toBeUndefined();
    });

    it('should throw RateLimitError when limit exceeded', async () => {
      await limiter.consume();
      await limiter.consume();

      await expect(limiter.consume()).rejects.toThrow(RateLimitError);
    });

    it('should include retry information in error', async () => {
      await limiter.consume();
      await limiter.consume();

      try {
        await limiter.consume();
        fail('Should have thrown');
      } catch (error) {
        expect(error).toBeInstanceOf(RateLimitError);
        const rateLimitError = error as RateLimitError;
        expect(rateLimitError.details.resetTime).toBeDefined();
        expect(rateLimitError.details.retryAfter).toBeGreaterThan(0);
      }
    });

    it('should emit requestAllowed event on success', async () => {
      const handler = jest.fn();
      limiter.on('requestAllowed', handler);

      await limiter.consume();

      expect(handler).toHaveBeenCalledWith({
        key: expect.any(String),
        timestamp: expect.any(Number),
      });
    });

    it('should emit rateLimitExceeded event on failure', async () => {
      const handler = jest.fn();
      limiter.on('rateLimitExceeded', handler);

      await limiter.consume();
      await limiter.consume();

      try {
        await limiter.consume();
      } catch (error) {
        // Expected
      }

      expect(handler).toHaveBeenCalledWith({
        key: expect.any(String),
        resetTime: expect.any(Number),
        timestamp: expect.any(Number),
      });
    });
  });

  describe('getRemaining() method', () => {
    it('should return correct remaining count', async () => {
      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
      });

      expect(limiter.getRemaining()).toBe(5);

      await limiter.check();
      expect(limiter.getRemaining()).toBe(4);

      await limiter.check();
      expect(limiter.getRemaining()).toBe(3);
    });

    it('should not go below zero', async () => {
      limiter = new RateLimiter({
        maxRequests: 2,
        windowMs: 1000,
      });

      await limiter.check();
      await limiter.check();
      await limiter.check(); // Over limit

      expect(limiter.getRemaining()).toBe(0);
    });

    it('should work with token bucket strategy', async () => {
      jest.useFakeTimers();

      limiter = new RateLimiter({
        maxRequests: 10,
        windowMs: 1000,
        strategy: 'token-bucket',
      });

      expect(limiter.getRemaining()).toBe(10);

      await limiter.check();
      expect(limiter.getRemaining()).toBe(9);

      jest.advanceTimersByTime(100); // Should refill 1 token
      expect(limiter.getRemaining()).toBe(10); // Capped at max

      jest.useRealTimers();
    });

    it('should track separate counts for different keys', async () => {
      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
        keyGenerator: (ctx) => ctx?.key,
      });

      await limiter.check({ key: 'user1' });
      await limiter.check({ key: 'user1' });

      expect(limiter.getRemaining({ key: 'user1' })).toBe(3);
      expect(limiter.getRemaining({ key: 'user2' })).toBe(5);
    });
  });

  describe('getResetTime() method', () => {
    it('should return future timestamp', () => {
      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
      });

      const resetTime = limiter.getResetTime('test-key');
      expect(resetTime).toBeGreaterThan(Date.now());
    });

    it('should work with fixed-window strategy', () => {
      jest.useFakeTimers();
      const now = Date.now();
      jest.setSystemTime(now);

      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
        strategy: 'fixed-window',
      });

      const resetTime = limiter.getResetTime('test-key');
      expect(resetTime).toBeGreaterThan(now);
      expect(resetTime - now).toBeLessThanOrEqual(1000);

      jest.useRealTimers();
    });

    it('should work with sliding-window strategy', () => {
      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
        strategy: 'sliding-window',
      });

      const now = Date.now();
      const resetTime = limiter.getResetTime('test-key');
      expect(resetTime).toBeGreaterThanOrEqual(now + 1000);
    });

    it('should work with token-bucket strategy', () => {
      jest.useFakeTimers();

      limiter = new RateLimiter({
        maxRequests: 10,
        windowMs: 1000,
        strategy: 'token-bucket',
      });

      // With tokens available, should reset immediately
      const resetTime1 = limiter.getResetTime('test-key');
      expect(resetTime1).toBe(Date.now());

      jest.useRealTimers();
    });
  });

  describe('reset() method', () => {
    beforeEach(() => {
      limiter = new RateLimiter({
        maxRequests: 2,
        windowMs: 1000,
        keyGenerator: (ctx) => ctx?.key || 'default',
      });
    });

    it('should reset limits for specific key', async () => {
      await limiter.check({ key: 'user1' });
      await limiter.check({ key: 'user1' });
      expect(await limiter.check({ key: 'user1' })).toBe(false);

      limiter.reset({ key: 'user1' });

      expect(await limiter.check({ key: 'user1' })).toBe(true);
    });

    it('should not affect other keys', async () => {
      await limiter.check({ key: 'user1' });
      await limiter.check({ key: 'user1' });

      await limiter.check({ key: 'user2' });

      limiter.reset({ key: 'user1' });

      expect(limiter.getRemaining({ key: 'user1' })).toBe(2);
      expect(limiter.getRemaining({ key: 'user2' })).toBe(1);
    });

    it('should emit reset event', () => {
      const handler = jest.fn();
      limiter.on('reset', handler);

      limiter.reset({ key: 'test' });

      expect(handler).toHaveBeenCalledWith({
        key: 'test',
        timestamp: expect.any(Number),
      });
    });
  });

  describe('resetAll() method', () => {
    beforeEach(() => {
      limiter = new RateLimiter({
        maxRequests: 2,
        windowMs: 1000,
        keyGenerator: (ctx) => ctx?.key || 'default',
      });
    });

    it('should reset all keys', async () => {
      await limiter.check({ key: 'user1' });
      await limiter.check({ key: 'user2' });

      limiter.resetAll();

      expect(limiter.getRemaining({ key: 'user1' })).toBe(2);
      expect(limiter.getRemaining({ key: 'user2' })).toBe(2);
    });

    it('should emit resetAll event', () => {
      const handler = jest.fn();
      limiter.on('resetAll', handler);

      limiter.resetAll();

      expect(handler).toHaveBeenCalledWith({
        timestamp: expect.any(Number),
      });
    });
  });

  describe('destroy() method', () => {
    it('should clean up resources', () => {
      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
      });

      limiter.destroy();

      // Should not throw
      expect(() => limiter.getRemaining()).not.toThrow();
    });

    it('should stop cleanup interval', () => {
      jest.useFakeTimers();

      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
      });

      limiter.destroy();

      // Advance time and ensure no cleanup happens
      jest.advanceTimersByTime(10000);

      jest.useRealTimers();
    });

    it('should remove all listeners', () => {
      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
      });

      limiter.on('requestAllowed', jest.fn());
      limiter.on('rateLimitExceeded', jest.fn());

      limiter.destroy();

      expect(limiter.listenerCount('requestAllowed')).toBe(0);
      expect(limiter.listenerCount('rateLimitExceeded')).toBe(0);
    });
  });

  describe('Cleanup mechanism', () => {
    it('should automatically cleanup old records', async () => {
      jest.useFakeTimers();

      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
      });

      await limiter.check();

      // Advance past cleanup interval
      jest.advanceTimersByTime(1001);

      // Old records should be cleaned
      expect(limiter.getRemaining()).toBe(5);

      jest.useRealTimers();
    });

    it('should cleanup inactive token bucket data', async () => {
      jest.useFakeTimers();

      limiter = new RateLimiter({
        maxRequests: 10,
        windowMs: 1000,
        strategy: 'token-bucket',
        keyGenerator: (ctx) => ctx?.key,
      });

      await limiter.check({ key: 'user1' });

      // Advance past 2x window for cleanup
      jest.advanceTimersByTime(2001);

      // Should still work
      expect(await limiter.check({ key: 'user1' })).toBe(true);

      jest.useRealTimers();
    });
  });

  describe('Edge Cases', () => {
    it('should handle zero maxRequests', async () => {
      limiter = new RateLimiter({
        maxRequests: 0,
        windowMs: 1000,
      });

      expect(await limiter.check()).toBe(false);
    });

    it('should handle very short window', async () => {
      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1,
      });

      expect(await limiter.check()).toBe(true);
    });

    it('should handle very long window', async () => {
      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 86400000, // 1 day
      });

      for (let i = 0; i < 5; i++) {
        expect(await limiter.check()).toBe(true);
      }
      expect(await limiter.check()).toBe(false);
    });

    it('should handle rapid sequential requests', async () => {
      limiter = new RateLimiter({
        maxRequests: 100,
        windowMs: 1000,
      });

      const promises = [];
      for (let i = 0; i < 100; i++) {
        promises.push(limiter.check());
      }

      const results = await Promise.all(promises);
      expect(results.filter(r => r === true).length).toBe(100);
    });

    it('should handle concurrent requests from different keys', async () => {
      limiter = new RateLimiter({
        maxRequests: 5,
        windowMs: 1000,
        keyGenerator: (ctx) => ctx?.key,
      });

      const promises = [];
      for (let i = 0; i < 10; i++) {
        promises.push(limiter.check({ key: `user${i % 2}` }));
      }

      const results = await Promise.all(promises);
      const allowed = results.filter(r => r === true).length;
      expect(allowed).toBe(10); // 5 for each of 2 users
    });
  });

  describe('createRateLimiter helper', () => {
    it('should create limiter with basic config', () => {
      limiter = createRateLimiter(10, 1000);
      expect(limiter).toBeInstanceOf(RateLimiter);
    });

    it('should accept additional options', () => {
      limiter = createRateLimiter(10, 1000, {
        strategy: 'token-bucket',
        keyGenerator: (ctx) => ctx?.key,
      });
      expect(limiter).toBeInstanceOf(RateLimiter);
    });

    it('should work correctly', async () => {
      limiter = createRateLimiter(3, 1000);

      expect(await limiter.check()).toBe(true);
      expect(await limiter.check()).toBe(true);
      expect(await limiter.check()).toBe(true);
      expect(await limiter.check()).toBe(false);
    });
  });
});
