/**
 * Integration tests for CircuitBreaker
 * 
 * Tests the complete circuit breaker lifecycle:
 * - CLOSED -> OPEN transition on failures
 * - OPEN -> HALF_OPEN transition after timeout
 * - HALF_OPEN -> CLOSED recovery on success
 * - Fallback execution
 * - Statistics tracking
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { CircuitBreaker } from '../../lib/circuit-breaker';

describe('CircuitBreaker', () => {
  describe('Happy Path - Successful Executions', () => {
    it('should execute function successfully in CLOSED state', async () => {
      const breaker = new CircuitBreaker('test-service', {
        failureThreshold: 3,
        successThreshold: 2,
        timeout: 5000,
        resetTimeout: 1000,
      });

      const result = await breaker.execute(async () => 'success');
      
      expect(result).toBe('success');
      expect(breaker.getState()).toBe('CLOSED');
      
      const stats = breaker.getStatistics();
      expect(stats.successCount).toBe(1);
      expect(stats.failureCount).toBe(0);
    });

    it('should track multiple successful executions', async () => {
      const breaker = new CircuitBreaker('test-service');
      
      for (let i = 0; i < 5; i++) {
        await breaker.execute(async () => `result-${i}`);
      }

      const stats = breaker.getStatistics();
      expect(stats.successCount).toBe(5);
      expect(stats.totalRequests).toBe(5);
      expect(breaker.getState()).toBe('CLOSED');
    });
  });

  describe('Failure Handling - CLOSED to OPEN Transition', () => {
    it('should open circuit after reaching failure threshold', async () => {
      const breaker = new CircuitBreaker('test-service', {
        failureThreshold: 3,
        resetTimeout: 60000, // Long timeout to keep it OPEN during test
      });

      // Execute 3 failing requests
      for (let i = 0; i < 3; i++) {
        try {
          await breaker.execute(async () => {
            throw new Error('Service unavailable');
          });
        } catch (error) {
          // Expected failures
        }
      }

      expect(breaker.getState()).toBe('OPEN');
      
      const stats = breaker.getStatistics();
      expect(stats.failureCount).toBe(3);
      expect(stats.successCount).toBe(0);
    });

    it('should immediately reject requests in OPEN state', async () => {
      const breaker = new CircuitBreaker('test-service', {
        failureThreshold: 2,
        resetTimeout: 60000,
      });

      // Trip the circuit
      for (let i = 0; i < 2; i++) {
        try {
          await breaker.execute(async () => {
            throw new Error('Fail');
          });
        } catch {}
      }

      // Now circuit is OPEN - should reject immediately
      const startTime = Date.now();
      try {
        await breaker.execute(async () => 'should not execute');
        throw new Error('Should have thrown CircuitOpenError');
      } catch (error: any) {
        const duration = Date.now() - startTime;
        expect(error.message).toContain('Circuit breaker is OPEN');
        expect(duration).toBeLessThan(10); // Should fail fast (< 10ms)
      }

      const stats = breaker.getStatistics();
      expect(stats.rejectedRequests).toBe(1);
    });
  });

  describe('Recovery - OPEN to HALF_OPEN to CLOSED', () => {
    it('should transition to HALF_OPEN after reset timeout', async () => {
      const breaker = new CircuitBreaker('test-service', {
        failureThreshold: 2,
        resetTimeout: 100, // Short timeout for testing
      });

      // Trip the circuit
      for (let i = 0; i < 2; i++) {
        try {
          await breaker.execute(async () => { throw new Error('Fail'); });
        } catch {}
      }

      expect(breaker.getState()).toBe('OPEN');

      // Wait for reset timeout
      await new Promise(resolve => setTimeout(resolve, 150));

      // Next request should transition to HALF_OPEN and execute
      const result = await breaker.execute(async () => 'recovery-attempt');
      
      expect(result).toBe('recovery-attempt');
      // After one success, still in HALF_OPEN (need successThreshold successes)
      expect(breaker.getState()).toBe('HALF_OPEN');
    });

    it('should close circuit after success threshold in HALF_OPEN', async () => {
      const breaker = new CircuitBreaker('test-service', {
        failureThreshold: 2,
        successThreshold: 2,
        resetTimeout: 100,
      });

      // Trip the circuit
      for (let i = 0; i < 2; i++) {
        try {
          await breaker.execute(async () => { throw new Error('Fail'); });
        } catch {}
      }

      expect(breaker.getState()).toBe('OPEN');

      // Wait for reset timeout
      await new Promise(resolve => setTimeout(resolve, 150));

      // Execute successful requests to close circuit
      await breaker.execute(async () => 'success-1');
      expect(breaker.getState()).toBe('HALF_OPEN');

      await breaker.execute(async () => 'success-2');
      expect(breaker.getState()).toBe('CLOSED'); // Circuit closed!

      const stats = breaker.getStatistics();
      expect(stats.successCount).toBe(2);
    });

    it('should reopen circuit if failure occurs in HALF_OPEN', async () => {
      const breaker = new CircuitBreaker('test-service', {
        failureThreshold: 2,
        resetTimeout: 100,
      });

      // Trip the circuit
      for (let i = 0; i < 2; i++) {
        try {
          await breaker.execute(async () => { throw new Error('Fail'); });
        } catch {}
      }

      // Wait for reset timeout
      await new Promise(resolve => setTimeout(resolve, 150));

      // Fail during recovery - should reopen
      try {
        await breaker.execute(async () => { throw new Error('Recovery failed'); });
      } catch {}

      expect(breaker.getState()).toBe('OPEN');
    });
  });

  describe('Timeout Handling', () => {
    it('should timeout slow operations', async () => {
      const breaker = new CircuitBreaker('test-service', {
        failureThreshold: 2,
        timeout: 100, // 100ms timeout
      });

      try {
        await breaker.execute(async () => {
          // Simulate slow operation (200ms)
          await new Promise(resolve => setTimeout(resolve, 200));
          return 'should-timeout';
        });
        throw new Error('Should have timed out');
      } catch (error: any) {
        expect(error.message).toContain('timed out');
      }

      const stats = breaker.getStatistics();
      expect(stats.failureCount).toBe(1);
    });

    it('should not timeout fast operations', async () => {
      const breaker = new CircuitBreaker('test-service', {
        timeout: 1000, // 1 second timeout
      });

      const result = await breaker.execute(async () => {
        // Fast operation (10ms)
        await new Promise(resolve => setTimeout(resolve, 10));
        return 'fast-operation';
      });

      expect(result).toBe('fast-operation');
    });
  });

  describe('Fallback Behavior', () => {
    it('should execute fallback when circuit is OPEN', async () => {
      const fallbackFn = vi.fn().mockResolvedValue('fallback-result');
      
      const breaker = new CircuitBreaker('test-service', {
        failureThreshold: 2,
        resetTimeout: 60000,
        fallbackFn,
      });

      // Trip the circuit
      for (let i = 0; i < 2; i++) {
        try {
          await breaker.execute(async () => { throw new Error('Fail'); });
        } catch {}
      }

      // Circuit is OPEN - should use fallback
      const result = await breaker.execute(
        async () => 'primary-should-not-run',
      );

      expect(result).toBe('fallback-result');
      expect(fallbackFn).toHaveBeenCalledTimes(1);
      
      const stats = breaker.getStatistics();
      expect(stats.fallbackUsed).toBe(1);
    });

    it('should not execute fallback when circuit is CLOSED', async () => {
      const fallbackFn = vi.fn().mockResolvedValue('fallback');
      
      const breaker = new CircuitBreaker('test-service', {
        fallbackFn,
      });

      const result = await breaker.execute(async () => 'primary-result');

      expect(result).toBe('primary-result');
      expect(fallbackFn).not.toHaveBeenCalled();
    });
  });

  describe('Statistics and Observability', () => {
    it('should track comprehensive statistics', async () => {
      const breaker = new CircuitBreaker('test-service', {
        failureThreshold: 3,
      });

      // Execute mixed workload
      await breaker.execute(async () => 'success-1');
      await breaker.execute(async () => 'success-2');
      
      try {
        await breaker.execute(async () => { throw new Error('fail-1'); });
      } catch {}

      const stats = breaker.getStatistics();
      
      expect(stats.name).toBe('test-service');
      expect(stats.state).toBe('CLOSED');
      expect(stats.successCount).toBe(2);
      expect(stats.failureCount).toBe(1);
      expect(stats.totalRequests).toBe(3);
      expect(stats.successRate).toBeCloseTo(0.667, 2);
      expect(stats.lastSuccessTime).toBeDefined();
      expect(stats.lastFailureTime).toBeDefined();
    });

    it('should calculate success rate correctly', async () => {
      const breaker = new CircuitBreaker('test-service', {
        failureThreshold: 10,
      });

      // 7 successes, 3 failures = 70% success rate
      for (let i = 0; i < 7; i++) {
        await breaker.execute(async () => 'success');
      }
      for (let i = 0; i < 3; i++) {
        try {
          await breaker.execute(async () => { throw new Error('fail'); });
        } catch {}
      }

      const stats = breaker.getStatistics();
      expect(stats.successRate).toBeCloseTo(0.70, 2);
      expect(stats.totalRequests).toBe(10);
    });

    it('should track last execution times', async () => {
      const breaker = new CircuitBreaker('test-service');

      const beforeSuccess = Date.now();
      await breaker.execute(async () => 'success');
      const afterSuccess = Date.now();

      await new Promise(resolve => setTimeout(resolve, 10));

      const beforeFailure = Date.now();
      try {
        await breaker.execute(async () => { throw new Error('fail'); });
      } catch {}
      const afterFailure = Date.now();

      const stats = breaker.getStatistics();
      
      expect(stats.lastSuccessTime).toBeGreaterThanOrEqual(beforeSuccess);
      expect(stats.lastSuccessTime).toBeLessThanOrEqual(afterSuccess);
      
      expect(stats.lastFailureTime).toBeGreaterThanOrEqual(beforeFailure);
      expect(stats.lastFailureTime).toBeLessThanOrEqual(afterFailure);
    });
  });

  describe('Edge Cases', () => {
    it('should handle concurrent requests correctly', async () => {
      const breaker = new CircuitBreaker('test-service', {
        failureThreshold: 5,
      });

      // Execute 10 concurrent requests
      const promises = Array.from({ length: 10 }, (_, i) =>
        breaker.execute(async () => `result-${i}`)
      );

      const results = await Promise.all(promises);
      
      expect(results).toHaveLength(10);
      expect(breaker.getStatistics().successCount).toBe(10);
    });

    it('should reset statistics when circuit recovers', async () => {
      const breaker = new CircuitBreaker('test-service', {
        failureThreshold: 2,
        successThreshold: 1,
        resetTimeout: 100,
      });

      // Trip circuit
      for (let i = 0; i < 2; i++) {
        try {
          await breaker.execute(async () => { throw new Error('fail'); });
        } catch {}
      }

      expect(breaker.getState()).toBe('OPEN');
      const openStats = breaker.getStatistics();
      expect(openStats.failureCount).toBe(2);

      // Wait and recover
      await new Promise(resolve => setTimeout(resolve, 150));
      await breaker.execute(async () => 'recovery');

      expect(breaker.getState()).toBe('CLOSED');
      const closedStats = breaker.getStatistics();
      expect(closedStats.consecutiveFailures).toBe(0);
    });

    it('should handle undefined fallback gracefully', async () => {
      const breaker = new CircuitBreaker('test-service', {
        failureThreshold: 1,
        resetTimeout: 60000,
        // No fallback provided
      });

      // Trip circuit
      try {
        await breaker.execute(async () => { throw new Error('fail'); });
      } catch {}

      // Should throw CircuitOpenError without fallback
      try {
        await breaker.execute(async () => 'should-not-execute');
        throw new Error('Should have thrown CircuitOpenError');
      } catch (error: any) {
        expect(error.message).toContain('Circuit breaker is OPEN');
      }
    });
  });

  describe('Real-World Scenarios', () => {
    it('should protect against service degradation', async () => {
      let callCount = 0;
      const flakyService = async () => {
        callCount++;
        if (callCount <= 3) {
          throw new Error('Service degraded');
        }
        return 'recovered';
      };

      const breaker = new CircuitBreaker('flaky-service', {
        failureThreshold: 3,
        resetTimeout: 100,
      });

      // Service fails 3 times - circuit opens
      for (let i = 0; i < 3; i++) {
        try {
          await breaker.execute(flakyService);
        } catch {}
      }

      expect(breaker.getState()).toBe('OPEN');

      // Circuit blocks subsequent calls (protects service)
      try {
        await breaker.execute(flakyService);
      } catch {}

      expect(callCount).toBe(3); // Service not called again

      // Wait for reset, then recover
      await new Promise(resolve => setTimeout(resolve, 150));
      const result = await breaker.execute(flakyService);
      
      expect(result).toBe('recovered');
      expect(breaker.getState()).toBe('CLOSED');
    });

    it('should provide observability for SRE monitoring', () => {
      const breaker = new CircuitBreaker('monitored-service', {
        failureThreshold: 5,
        timeout: 3000,
      });

      const stats = breaker.getStatistics();
      
      // Stats should be suitable for Prometheus metrics
      expect(stats).toHaveProperty('name');
      expect(stats).toHaveProperty('state');
      expect(stats).toHaveProperty('successCount');
      expect(stats).toHaveProperty('failureCount');
      expect(stats).toHaveProperty('successRate');
      expect(stats).toHaveProperty('rejectedRequests');
      expect(stats).toHaveProperty('lastSuccessTime');
      expect(stats).toHaveProperty('lastFailureTime');
      
      // All values should be numbers or strings (serializable)
      expect(typeof stats.successRate).toBe('number');
      expect(typeof stats.totalRequests).toBe('number');
    });
  });
});
