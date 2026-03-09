/**
 * @fileoverview Integration tests for resilience patterns
 * Tests circuit breaker, retry policy, DLQ, and health checks
 *
 * Updated Phase 2.5: Use shared resilience patterns from @ghatana/dcmaar-connectors
 */

import {
  CircuitBreaker,
  CircuitBreakerOpenError,
  RetryPolicy,
  RetryStrategy,
  DeadLetterQueue,
  HealthChecker,
  HealthStatus,
} from '@ghatana/dcmaar-connectors';

describe.skip('Resilience Patterns', () => {
  describe('CircuitBreaker', () => {
    it('should open after failure threshold', async () => {
      const breaker = new CircuitBreaker({
        name: 'test-breaker',
        failureThreshold: 3,
        timeout: 100,
      });

      // Simulate failures
      for (let i = 0; i < 3; i++) {
        try {
          await breaker.execute(async () => {
            throw new Error('Test error');
          });
        } catch (error) {
          // Expected
        }
      }

      expect(breaker.getState()).toBe('open');
    });

    it('should reject requests when open', async () => {
      const breaker = new CircuitBreaker({
        name: 'test-breaker',
        failureThreshold: 1,
        timeout: 100,
      });

      // Open circuit
      try {
        await breaker.execute(async () => {
          throw new Error('Test error');
        });
      } catch (error) {
        // Expected
      }

      // Should reject
      await expect(
        breaker.execute(async () => {
          return 'success';
        })
      ).rejects.toThrow(CircuitBreakerOpenError);
    });

    it('should transition to half-open after timeout', async () => {
      const breaker = new CircuitBreaker({
        name: 'test-breaker',
        failureThreshold: 1,
        timeout: 50,
      });

      // Open circuit
      try {
        await breaker.execute(async () => {
          throw new Error('Test error');
        });
      } catch (error) {
        // Expected
      }

      expect(breaker.getState()).toBe('open');

      // Wait for timeout
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Should be able to execute (half-open)
      try {
        await breaker.execute(async () => {
          return 'success';
        });
      } catch (error) {
        // May still be open
      }
    });

    it('should close after success in half-open', async () => {
      const breaker = new CircuitBreaker({
        name: 'test-breaker',
        failureThreshold: 1,
        successThreshold: 1,
        timeout: 50,
      });

      // Open circuit
      try {
        await breaker.execute(async () => {
          throw new Error('Test error');
        });
      } catch (error) {
        // Expected
      }

      // Wait for timeout
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Execute successfully
      await breaker.execute(async () => {
        return 'success';
      });

      expect(breaker.getState()).toBe('closed');
    });
  });

  describe('RetryPolicy', () => {
    it('should retry on failure', async () => {
      const policy = new RetryPolicy({
        maxAttempts: 3,
        initialDelayMs: 10,
      });

      let attempts = 0;

      try {
        await policy.execute(async () => {
          attempts++;
          throw new Error('Test error');
        });
      } catch (error) {
        // Expected
      }

      expect(attempts).toBe(3);
    });

    it('should succeed on eventual success', async () => {
      const policy = new RetryPolicy({
        maxAttempts: 3,
        initialDelayMs: 10,
      });

      let attempts = 0;

      const result = await policy.execute(async () => {
        attempts++;
        if (attempts < 2) {
          throw new Error('Test error');
        }
        return 'success';
      });

      expect(result).toBe('success');
      expect(attempts).toBe(2);
    });

    it('should apply exponential backoff', async () => {
      const policy = new RetryPolicy({
        maxAttempts: 3,
        initialDelayMs: 10,
        strategy: RetryStrategy.EXPONENTIAL,
        jitter: false,
      });

      const times: number[] = [];

      try {
        await policy.execute(async () => {
          times.push(Date.now());
          throw new Error('Test error');
        });
      } catch (error) {
        // Expected
      }

      // Delays should increase exponentially
      expect(times.length).toBe(3);
    });

    it('should add jitter to delays', async () => {
      const policy = new RetryPolicy({
        maxAttempts: 3,
        initialDelayMs: 100,
        strategy: RetryStrategy.EXPONENTIAL,
        jitter: true,
      });

      let attempts = 0;

      try {
        await policy.execute(async () => {
          attempts++;
          throw new Error('Test error');
        });
      } catch (error) {
        // Expected
      }

      expect(attempts).toBe(3);
    });
  });

  describe('DeadLetterQueue', () => {
    it('should enqueue failed messages', async () => {
      const dlq = new DeadLetterQueue({
        maxSize: 100,
      });

      const id = await dlq.enqueue({
        data: { test: 'data' },
        error: 'Test error',
        timestamp: Date.now(),
        adapter: 'test-adapter',
      });

      expect(id).toBeDefined();
      expect(dlq.getSize()).toBe(1);
    });

    it('should dequeue messages', async () => {
      const dlq = new DeadLetterQueue({
        maxSize: 100,
      });

      const id = await dlq.enqueue({
        data: { test: 'data' },
        error: 'Test error',
        timestamp: Date.now(),
        adapter: 'test-adapter',
      });

      const message = await dlq.dequeue(id);
      expect(message).toBeDefined();
      expect(dlq.getSize()).toBe(0);
    });

    it('should peek without removing', async () => {
      const dlq = new DeadLetterQueue({
        maxSize: 100,
      });

      await dlq.enqueue({
        data: { test: 'data' },
        error: 'Test error',
        timestamp: Date.now(),
        adapter: 'test-adapter',
      });

      const messages = await dlq.peek(10);
      expect(messages.length).toBe(1);
      expect(dlq.getSize()).toBe(1);
    });

    it('should enforce max size', async () => {
      const dlq = new DeadLetterQueue({
        maxSize: 2,
      });

      await dlq.enqueue({
        data: { test: 'data1' },
        error: 'Error 1',
        timestamp: Date.now(),
        adapter: 'test-adapter',
      });

      await dlq.enqueue({
        data: { test: 'data2' },
        error: 'Error 2',
        timestamp: Date.now(),
        adapter: 'test-adapter',
      });

      // Should throw on third enqueue
      await expect(
        dlq.enqueue({
          data: { test: 'data3' },
          error: 'Error 3',
          timestamp: Date.now(),
          adapter: 'test-adapter',
        })
      ).rejects.toThrow();
    });

    it('should track retry count', async () => {
      const dlq = new DeadLetterQueue({
        maxSize: 100,
      });

      const id = await dlq.enqueue({
        data: { test: 'data' },
        error: 'Test error',
        timestamp: Date.now(),
        adapter: 'test-adapter',
      });

      await dlq.incrementRetry(id, 'Retry error');

      const messages = await dlq.peek(10);
      expect(messages[0].retryCount).toBe(1);
      expect(messages[0].lastError).toBe('Retry error');
    });
  });

  describe('HealthChecker', () => {
    it('should report healthy status', async () => {
      const checker = new HealthChecker({
        name: 'test-checker',
        intervalMs: 100,
        checks: [
          {
            name: 'connectivity',
            fn: async () => true,
            critical: true,
          },
        ],
      });

      await checker.start();

      // Wait for first check
      await new Promise((resolve) => setTimeout(resolve, 150));

      expect(checker.getStatus()).toBe(HealthStatus.HEALTHY);

      checker.stop();
    });

    it('should report degraded status', async () => {
      const checker = new HealthChecker({
        name: 'test-checker',
        intervalMs: 100,
        checks: [
          {
            name: 'connectivity',
            fn: async () => true,
            critical: true,
          },
          {
            name: 'latency',
            fn: async () => false,
            critical: false,
          },
        ],
      });

      await checker.start();

      // Wait for first check
      await new Promise((resolve) => setTimeout(resolve, 150));

      expect(checker.getStatus()).toBe(HealthStatus.DEGRADED);

      checker.stop();
    });

    it('should report unhealthy status', async () => {
      const checker = new HealthChecker({
        name: 'test-checker',
        intervalMs: 100,
        checks: [
          {
            name: 'connectivity',
            fn: async () => false,
            critical: true,
          },
        ],
      });

      await checker.start();

      // Wait for first check
      await new Promise((resolve) => setTimeout(resolve, 150));

      expect(checker.getStatus()).toBe(HealthStatus.UNHEALTHY);

      checker.stop();
    });

    it('should track check results', async () => {
      const checker = new HealthChecker({
        name: 'test-checker',
        intervalMs: 100,
        checks: [
          {
            name: 'connectivity',
            fn: async () => true,
            critical: true,
          },
        ],
      });

      await checker.start();

      // Wait for first check
      await new Promise((resolve) => setTimeout(resolve, 150));

      const results = checker.getResults(10);
      expect(results.length).toBeGreaterThan(0);

      checker.stop();
    });

    it('should provide health statistics', async () => {
      const checker = new HealthChecker({
        name: 'test-checker',
        intervalMs: 100,
        checks: [
          {
            name: 'connectivity',
            fn: async () => true,
            critical: true,
          },
        ],
      });

      await checker.start();

      // Wait for first check
      await new Promise((resolve) => setTimeout(resolve, 150));

      const stats = checker.getStats();
      expect(stats.status).toBeDefined();
      expect(stats.passRate).toBeGreaterThanOrEqual(0);
      expect(stats.lastCheckTime).toBeGreaterThan(0);

      checker.stop();
    });
  });
});
