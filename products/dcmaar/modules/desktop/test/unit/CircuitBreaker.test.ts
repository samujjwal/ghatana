/**
 * Circuit Breaker unit tests
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  CircuitBreaker,
  CircuitState,
  CircuitBreakerError,
  DEFAULT_CIRCUIT_CONFIG,
} from '../../src/utils/CircuitBreaker';

describe('CircuitBreaker', () => {
  let breaker: CircuitBreaker;

  beforeEach(() => {
    breaker = new CircuitBreaker({
      ...DEFAULT_CIRCUIT_CONFIG,
      resetTimeout: 100, // Short timeout for testing
    });
  });

  describe('CLOSED state', () => {
    it('should start in CLOSED state', () => {
      expect(breaker.getState()).toBe(CircuitState.CLOSED);
      expect(breaker.isClosed()).toBe(true);
      expect(breaker.isOpen()).toBe(false);
    });

    it('should execute function successfully', async () => {
      const fn = vi.fn().mockResolvedValue('success');
      const result = await breaker.execute(fn);

      expect(result).toBe('success');
      expect(fn).toHaveBeenCalledOnce();
      expect(breaker.getState()).toBe(CircuitState.CLOSED);
    });

    it('should remain closed after single failure', async () => {
      const fn = vi.fn().mockRejectedValue(new Error('failure'));

      await expect(breaker.execute(fn)).rejects.toThrow('failure');
      expect(breaker.getState()).toBe(CircuitState.CLOSED);
    });

    it('should open after threshold failures', async () => {
      const fn = vi.fn().mockRejectedValue(new Error('failure'));

      // Fail threshold times
      for (let i = 0; i < DEFAULT_CIRCUIT_CONFIG.failureThreshold; i++) {
        await expect(breaker.execute(fn)).rejects.toThrow('failure');
      }

      expect(breaker.getState()).toBe(CircuitState.OPEN);
      expect(breaker.isOpen()).toBe(true);
    });
  });

  describe('OPEN state', () => {
    beforeEach(async () => {
      // Force circuit to open
      const fn = vi.fn().mockRejectedValue(new Error('failure'));
      for (let i = 0; i < DEFAULT_CIRCUIT_CONFIG.failureThreshold; i++) {
        await expect(breaker.execute(fn)).rejects.toThrow('failure');
      }
    });

    it('should reject requests immediately', async () => {
      const fn = vi.fn().mockResolvedValue('success');

      await expect(breaker.execute(fn)).rejects.toThrow(CircuitBreakerError);
      expect(fn).not.toHaveBeenCalled();
    });

    it('should transition to HALF_OPEN after timeout', async () => {
      // Wait for reset timeout
      await new Promise((resolve) => setTimeout(resolve, 150));

      const fn = vi.fn().mockResolvedValue('success');
      await breaker.execute(fn);

      expect(breaker.getState()).toBe(CircuitState.HALF_OPEN);
    });

    it('should include state in error', async () => {
      const fn = vi.fn().mockResolvedValue('success');

      try {
        await breaker.execute(fn);
        expect.fail('Should have thrown');
      } catch (error) {
        expect(error).toBeInstanceOf(CircuitBreakerError);
        expect((error as CircuitBreakerError).state).toBe(CircuitState.OPEN);
      }
    });
  });

  describe('HALF_OPEN state', () => {
    beforeEach(async () => {
      // Force circuit to open then wait for half-open
      const fn = vi.fn().mockRejectedValue(new Error('failure'));
      for (let i = 0; i < DEFAULT_CIRCUIT_CONFIG.failureThreshold; i++) {
        await expect(breaker.execute(fn)).rejects.toThrow('failure');
      }
      await new Promise((resolve) => setTimeout(resolve, 150));
    });

    it('should close after threshold successes', async () => {
      const fn = vi.fn().mockResolvedValue('success');

      // Execute threshold times
      for (let i = 0; i < DEFAULT_CIRCUIT_CONFIG.halfOpenThreshold; i++) {
        await breaker.execute(fn);
      }

      expect(breaker.getState()).toBe(CircuitState.CLOSED);
    });

    it('should reopen immediately on failure', async () => {
      const successFn = vi.fn().mockResolvedValue('success');
      const failFn = vi.fn().mockRejectedValue(new Error('failure'));

      // One success
      await breaker.execute(successFn);
      expect(breaker.getState()).toBe(CircuitState.HALF_OPEN);

      // Then failure
      await expect(breaker.execute(failFn)).rejects.toThrow('failure');
      expect(breaker.getState()).toBe(CircuitState.OPEN);
    });
  });

  describe('reset', () => {
    it('should reset to CLOSED state', async () => {
      // Force open
      const fn = vi.fn().mockRejectedValue(new Error('failure'));
      for (let i = 0; i < DEFAULT_CIRCUIT_CONFIG.failureThreshold; i++) {
        await expect(breaker.execute(fn)).rejects.toThrow('failure');
      }

      expect(breaker.getState()).toBe(CircuitState.OPEN);

      breaker.reset();

      expect(breaker.getState()).toBe(CircuitState.CLOSED);
      expect(breaker.getStats().failures).toBe(0);
      expect(breaker.getStats().successes).toBe(0);
    });
  });

  describe('getStats', () => {
    it('should return current statistics', async () => {
      const fn = vi.fn().mockResolvedValue('success');
      await breaker.execute(fn);

      const stats = breaker.getStats();

      expect(stats.state).toBe(CircuitState.CLOSED);
      expect(stats.failures).toBe(0);
      expect(stats.lastSuccessTime).toBeInstanceOf(Date);
    });

    it('should track failure timestamps', async () => {
      const fn = vi.fn().mockRejectedValue(new Error('failure'));

      await expect(breaker.execute(fn)).rejects.toThrow('failure');

      const stats = breaker.getStats();
      expect(stats.failures).toBe(1);
      expect(stats.lastFailureTime).toBeInstanceOf(Date);
    });
  });

  describe('monitoring window', () => {
    it('should only count failures within window', async () => {
      const breaker = new CircuitBreaker({
        failureThreshold: 3,
        resetTimeout: 1000,
        halfOpenThreshold: 2,
        monitoringWindow: 100, // 100ms window
      });

      const fn = vi.fn().mockRejectedValue(new Error('failure'));

      // First failure
      await expect(breaker.execute(fn)).rejects.toThrow('failure');
      expect(breaker.getStats().failures).toBe(1);

      // Wait for window to expire
      await new Promise((resolve) => setTimeout(resolve, 150));

      // Second failure (first should be expired)
      await expect(breaker.execute(fn)).rejects.toThrow('failure');
      expect(breaker.getStats().failures).toBe(1); // Only counts recent failure
      expect(breaker.getState()).toBe(CircuitState.CLOSED); // Not enough to open
    });
  });
});
