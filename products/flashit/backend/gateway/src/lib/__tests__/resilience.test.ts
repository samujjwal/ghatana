/**
 * @fileoverview Resilience Tests
 * Tests for retry logic and circuit breaker patterns
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  withRetry,
  withFallback,
  withTimeout,
  withResilience,
  CircuitBreaker,
  CircuitState,
  sleep,
  RetryOptions,
} from '../resilience';

describe('Resilience Utilities', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('sleep', () => {
    it('should delay for specified milliseconds', async () => {
      const sleepPromise = sleep(1000);
      vi.advanceTimersByTime(1000);
      await sleepPromise;
      // Should complete without error
    });
  });

  describe('withRetry', () => {
    it('should return result on successful operation', async () => {
      const operation = vi.fn().mockResolvedValue('success');

      const result = await withRetry(operation);

      expect(result).toBe('success');
      expect(operation).toHaveBeenCalledTimes(1);
    });

    it('should retry on failure and eventually succeed', async () => {
      const operation = vi
        .fn()
        .mockRejectedValueOnce(new Error('timeout'))
        .mockRejectedValueOnce(new Error('timeout'))
        .mockResolvedValue('success');

      const retryPromise = withRetry(operation, { maxRetries: 3, initialDelay: 1000 });

      // Advance past first retry
      vi.advanceTimersByTime(1000);
      await Promise.resolve();

      // Advance past second retry
      vi.advanceTimersByTime(2000);
      await Promise.resolve();

      const result = await retryPromise;

      expect(result).toBe('success');
      expect(operation).toHaveBeenCalledTimes(3);
    });

    it('should throw after max retries exceeded', async () => {
      const operation = vi.fn().mockRejectedValue(new Error('persistent error'));

      const retryPromise = withRetry(operation, { maxRetries: 2, initialDelay: 100 });

      vi.advanceTimersByTime(100);
      await Promise.resolve();

      vi.advanceTimersByTime(200);
      await Promise.resolve();

      await expect(retryPromise).rejects.toThrow('persistent error');
      expect(operation).toHaveBeenCalledTimes(3); // initial + 2 retries
    });

    it('should not retry non-retryable errors', async () => {
      const operation = vi.fn().mockRejectedValue(new Error('auth failed'));

      await expect(
        withRetry(operation, {
          maxRetries: 3,
          shouldRetry: (error) => !error.message.includes('auth'),
        })
      ).rejects.toThrow('auth failed');

      expect(operation).toHaveBeenCalledTimes(1);
    });

    it('should use custom retry options', async () => {
      const operation = vi
        .fn()
        .mockRejectedValueOnce(new Error('timeout'))
        .mockResolvedValue('success');

      const customOptions: Partial<RetryOptions> = {
        maxRetries: 1,
        initialDelay: 500,
        backoffMultiplier: 3,
      };

      const retryPromise = withRetry(operation, customOptions);

      vi.advanceTimersByTime(500);
      await Promise.resolve();

      const result = await retryPromise;

      expect(result).toBe('success');
    });

    it('should handle ECONNREFUSED as retryable', async () => {
      const operation = vi
        .fn()
        .mockRejectedValueOnce(new Error('ECONNREFUSED'))
        .mockResolvedValue('success');

      const retryPromise = withRetry(operation, { maxRetries: 1, initialDelay: 100 });

      vi.advanceTimersByTime(100);
      await Promise.resolve();

      const result = await retryPromise;
      expect(result).toBe('success');
    });
  });

  describe('withFallback', () => {
    it('should return primary result when successful', async () => {
      const primary = vi.fn().mockResolvedValue('primary');
      const fallback = vi.fn().mockResolvedValue('fallback');

      const result = await withFallback(primary, fallback);

      expect(result).toBe('primary');
      expect(fallback).not.toHaveBeenCalled();
    });

    it('should use fallback when primary fails', async () => {
      const primary = vi.fn().mockRejectedValue(new Error('primary failed'));
      const fallback = vi.fn().mockResolvedValue('fallback');

      const result = await withFallback(primary, fallback);

      expect(result).toBe('fallback');
      expect(fallback).toHaveBeenCalled();
    });

    it('should throw when both primary and fallback fail', async () => {
      const primary = vi.fn().mockRejectedValue(new Error('primary failed'));
      const fallback = vi.fn().mockRejectedValue(new Error('fallback failed'));

      await expect(withFallback(primary, fallback)).rejects.toThrow('fallback failed');
    });
  });

  describe('withTimeout', () => {
    it('should return result within timeout', async () => {
      const operation = vi.fn().mockImplementation(async () => {
        await sleep(100);
        return 'success';
      });

      const promise = withTimeout(operation(), 1000);
      vi.advanceTimersByTime(100);

      const result = await promise;
      expect(result).toBe('success');
    });

    it('should throw timeout error when operation exceeds limit', async () => {
      const slowOperation = async () => {
        await sleep(5000);
        return 'too late';
      };

      const promise = withTimeout(slowOperation(), 100);

      vi.advanceTimersByTime(100);

      await expect(promise).rejects.toThrow('Operation timed out');
    });
  });

  describe('withResilience', () => {
    it('should combine retry and fallback', async () => {
      const operation = vi
        .fn()
        .mockRejectedValueOnce(new Error('timeout'))
        .mockResolvedValue('success');
      const fallback = vi.fn().mockResolvedValue('fallback');

      const retryPromise = withResilience(operation, fallback, {
        maxRetries: 1,
        initialDelay: 100,
      });

      vi.advanceTimersByTime(100);
      await Promise.resolve();

      const result = await retryPromise;
      expect(result).toBe('success');
    });

    it('should use fallback after retries exhausted', async () => {
      const operation = vi.fn().mockRejectedValue(new Error('persistent error'));
      const fallback = vi.fn().mockResolvedValue('fallback');

      const retryPromise = withResilience(operation, fallback, {
        maxRetries: 1,
        initialDelay: 100,
      });

      vi.advanceTimersByTime(100);
      await Promise.resolve();

      const result = await retryPromise;
      expect(result).toBe('fallback');
    });
  });

  describe('CircuitBreaker', () => {
    it('should start in CLOSED state', () => {
      const cb = new CircuitBreaker('test', { failureThreshold: 3, successThreshold: 2, timeout: 1000 });
      expect(cb.getState()).toBe(CircuitState.CLOSED);
    });

    it('should execute successfully in closed state', async () => {
      const cb = new CircuitBreaker('test', { failureThreshold: 3, successThreshold: 2, timeout: 1000 });
      const operation = vi.fn().mockResolvedValue('success');

      const result = await cb.execute(operation);

      expect(result).toBe('success');
      expect(operation).toHaveBeenCalled();
    });

    it('should track failures', async () => {
      const cb = new CircuitBreaker('test', { failureThreshold: 3, successThreshold: 2, timeout: 1000 });
      const operation = vi.fn().mockRejectedValue(new Error('failure'));

      try {
        await cb.execute(operation);
      } catch {
        // expected
      }

      expect(cb.getStats().failures).toBe(1);
    });

    it('should track successes', async () => {
      const cb = new CircuitBreaker('test', { failureThreshold: 3, successThreshold: 2, timeout: 1000 });
      const operation = vi.fn().mockResolvedValue('success');

      await cb.execute(operation);
      await cb.execute(operation);

      expect(cb.getStats().successes).toBe(2);
    });

    it('should get circuit stats', async () => {
      const cb = new CircuitBreaker('test', { failureThreshold: 3, successThreshold: 2, timeout: 1000 });

      const stats = cb.getStats();

      expect(stats).toHaveProperty('state');
      expect(stats).toHaveProperty('failures');
      expect(stats).toHaveProperty('successes');
      expect(stats).toHaveProperty('lastFailureTime');
      expect(stats).toHaveProperty('lastSuccessTime');
    });
  });
});
