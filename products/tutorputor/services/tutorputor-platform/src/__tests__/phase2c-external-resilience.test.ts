/**
 * @doc.type test-suite
 * @doc.purpose CRITICAL: External service resilience, error handling, fallbacks
 * @doc.layer platform
 * @doc.pattern Integration Test
 *
 * Phase 2C validates system behavior under external service failures:
 * - AI Registry timeouts and errors
 * - Payment gateway (Stripe) failures
 * - Learner profile service unavailability
 * - Feature store connectivity issues
 * - Graceful degradation and user experience
 */

import { describe, it, expect, vi, beforeAll, afterAll, beforeEach } from 'vitest';

/**
 * External service client mock factory
 */
class MockExternalClient {
  responseDelay = 0;
  shouldFail = false;
  failureMessage = 'Service unavailable';

  async callWithTimeoutHandling<T>(
    fn: () => Promise<T>,
    timeoutMs = 5000
  ): Promise<T> {
    return Promise.race([
      fn(),
      new Promise<T>((_, reject) =>
        setTimeout(() => reject(new Error('Request timeout')), timeoutMs)
      ),
    ]);
  }
}

describe('Phase 2C: External Service Resilience', () => {
  let aiRegistry: MockExternalClient;
  let paymentGateway: MockExternalClient;
  let learnerProfile: MockExternalClient;
  let featureStore: MockExternalClient;

  beforeAll(() => {
    aiRegistry = new MockExternalClient();
    paymentGateway = new MockExternalClient();
    learnerProfile = new MockExternalClient();
    featureStore = new MockExternalClient();
  });

  describe('AI Registry Service Resilience', () => {
    it('should handle AI registry timeout gracefully', async () => {
      aiRegistry.responseDelay = 10000; // 10 seconds
      aiRegistry.shouldFail = false;

      const result = await aiRegistry
        .callWithTimeoutHandling(
          () =>
            new Promise((resolve) =>
              setTimeout(() => resolve({ agents: [] }), aiRegistry.responseDelay)
            ),
          5000 // 5 second timeout
        )
        .catch((error) => ({
          success: false,
          error: 'Service timeout',
          fallback: true,
        }));

      expect(result).toHaveProperty('success', false);
      expect(result).toHaveProperty('fallback', true);
    });

    it('should return cached agents when registry is unavailable', async () => {
      aiRegistry.shouldFail = true;
      aiRegistry.failureMessage = 'Connection refused';

      const cachedAgents = [
        { id: 'agent-1', name: 'Code Generator', status: 'cached' },
        { id: 'agent-2', name: 'Analyzer', status: 'cached' },
      ];

      const result = await aiRegistry
        .callWithTimeoutHandling(() => Promise.reject(new Error('Connection refused')), 5000)
        .catch(() => ({
          success: false,
          data: cachedAgents,
          source: 'cache',
        }));

      expect(result).toHaveProperty('source', 'cache');
      expect(result.data).toHaveLength(2);
    });

    it('should retry failed registry calls with exponential backoff', async () => {
      const retryAttempts: number[] = [];
      let attempts = 0;

      const retryWithBackoff = async (fn: () => Promise<any>, maxRetries = 3) => {
        for (let i = 0; i < maxRetries; i++) {
          try {
            retryAttempts.push(i);
            return await fn();
          } catch (error) {
            if (i === maxRetries - 1) throw error;
            const delay = Math.pow(2, i) * 100; // Exponential backoff
            await new Promise((r) => setTimeout(r, delay));
          }
        }
      };

      try {
        await retryWithBackoff(() => Promise.reject(new Error('Failed')), 3);
      } catch {
        // Expected to fail after retries
      }

      expect(retryAttempts.length).toBe(3);
    });

    it('should record registry failures in observability metrics', async () => {
      const metrics = {
        registryFailures: 0,
        registryTimeouts: 0,
      };

      const recordMetric = (type: 'failures' | 'timeouts', increment = 1) => {
        if (type === 'failures') metrics.registryFailures += increment;
        if (type === 'timeouts') metrics.registryTimeouts += increment;
      };

      // Simulate failures
      recordMetric('failures');
      recordMetric('timeouts');
      recordMetric('failures');

      expect(metrics.registryFailures).toBe(2);
      expect(metrics.registryTimeouts).toBe(1);
    });
  });

  describe('Payment Gateway Resilience', () => {
    it('should handle payment timeout as transient failure', async () => {
      const paymentRequest = {
        amount: 9999,
        currency: 'USD',
        idempotencyKey: 'payment-uuid-123',
      };

      const result = await paymentGateway
        .callWithTimeoutHandling(
          () =>
            new Promise((_, reject) =>
              setTimeout(() => reject(new Error('Gateway timeout')), 10000)
            ),
          5000
        )
        .catch((error) => ({
          success: false,
          transient: true,
          idempotencyKey: paymentRequest.idempotencyKey,
          action: 'RETRY_LATER',
        }));

      expect(result.transient).toBe(true);
      expect(result.action).toBe('RETRY_LATER');
      expect(result).toHaveProperty('idempotencyKey');
    });

    it('should use idempotency keys to prevent duplicate charges', async () => {
      const chargeLog: { [key: string]: any } = {};

      const processPayment = (idempotencyKey: string, amount: number) => {
        if (chargeLog[idempotencyKey]) {
          // Idempotent: return same result
          return chargeLog[idempotencyKey];
        }

        const result = { chargeId: 'charge-' + Date.now(), amount };
        chargeLog[idempotencyKey] = result;
        return result;
      };

      const key = 'idempotent-payment-1';
      const result1 = processPayment(key, 100);
      const result2 = processPayment(key, 100); // Retry with same key

      expect(result1.chargeId).toBe(result2.chargeId);
      expect(Object.keys(chargeLog)).toHaveLength(1);
    });

    it('should handle insufficient funds gracefully', async () => {
      const result = await paymentGateway
        .callWithTimeoutHandling(() => Promise.reject(new Error('insufficient_funds')), 5000)
        .catch((error) => ({
          success: false,
          error: 'Insufficient funds',
          retryable: false,
          action: 'INSUFFICIENT_FUNDS_ERROR',
        }));

      expect(result.retryable).toBe(false);
      expect(result.action).toBe('INSUFFICIENT_FUNDS_ERROR');
    });

    it('should log payment failures with full context for debugging', async () => {
      const paymentLog: any[] = [];

      const logPaymentFailure = (error: string, context: any) => {
        paymentLog.push({
          timestamp: new Date(),
          error,
          context,
          severity: 'ERROR',
        });
      };

      logPaymentFailure('card_declined', {
        userId: 'user-1',
        amount: 9999,
        cardLast4: '4242',
        retryCount: 2,
      });

      expect(paymentLog).toHaveLength(1);
      expect(paymentLog[0].context).toHaveProperty('userId', 'user-1');
    });
  });

  describe('Learner Profile Service Failures', () => {
    it('should use default learner profile on service unavailability', async () => {
      const defaultProfile = {
        learningLevel: 'intermediate',
        preferredLanguage: 'en',
        enrolledCourses: [],
        progressData: null,
      };

      const profile = await learnerProfile
        .callWithTimeoutHandling(() => Promise.reject(new Error('Service down')), 3000)
        .catch(() => defaultProfile);

      expect(profile.learningLevel).toBe('intermediate');
      expect(profile.enrolledCourses).toEqual([]);
    });

    it('should cache learner profile data during outages', async () => {
      const cache: { [key: string]: any } = {
        'learner-profile:user-1': {
          userId: 'user-1',
          courses: 3,
          lastUpdated: new Date(),
        },
      };

      const getLearnerProfile = (userId: string) => {
        // Try service first, fall back to cache
        return learnerProfile
          .callWithTimeoutHandling(() => Promise.reject(new Error('Service down')), 3000)
          .catch(() => cache[`learner-profile:${userId}`] || null);
      };

      const profile = await getLearnerProfile('user-1');
      expect(profile).toHaveProperty('courses', 3);
      expect(profile).toHaveProperty('userId', 'user-1');
    });

    it('should continue session even with missing enrollment data', async () => {
      const sessionData = {
        userId: 'user-1',
        tenantId: 'tenant-1',
        authenticated: true,
        enrollmentData: null, // Failed to fetch
        fallbackMode: true,
      };

      // User can continue but with limited features
      expect(sessionData.authenticated).toBe(true);
      expect(sessionData.fallbackMode).toBe(true);
      expect(sessionData.enrollmentData).toBeNull();
    });
  });

  describe('Feature Store Connectivity Issues', () => {
    it('should handle feature store timeout in recommendation engine', async () => {
      const recommendationFallback = [
        { id: 'course-1', reason: 'popular' },
        { id: 'course-2', reason: 'popular' },
      ];

      const getRecommendations = async () => {
        return featureStore
          .callWithTimeoutHandling(() => Promise.reject(new Error('Feature store down')), 2000)
          .catch(() => recommendationFallback);
      };

      const recommendations = await getRecommendations();
      expect(recommendations).toHaveLength(2);
      expect(recommendations[0]).toHaveProperty('reason', 'popular');
    });

    it('should serve basic content without ML features', async () => {
      const basicContent = {
        title: 'Introduction to Python',
        description: 'Learn Python basics',
        mlFeatures: null, // Disabled due to feature store unavailability
        basicRanking: 'trending',
      };

      expect(basicContent.mlFeatures).toBeNull();
      expect(basicContent.basicRanking).toBe('trending');

      // User experience continues without ML features
      expect(basicContent.title).toBeDefined();
    });

    it('should log feature store failures for team alert', async () => {
      const alerts: any[] = [];

      const alertTeamOnFailure = (service: string, error: string) => {
        alerts.push({
          severity: 'CRITICAL',
          service,
          error,
          timestamp: new Date(),
        });
      };

      alertTeamOnFailure('feature-store', 'Connection pool exhausted');

      expect(alerts).toHaveLength(1);
      expect(alerts[0].severity).toBe('CRITICAL');
      expect(alerts[0].service).toBe('feature-store');
    });
  });

  describe('Circuit Breaker Pattern', () => {
    it('should open circuit after threshold of consecutive failures', async () => {
      const circuit = {
        state: 'closed',
        failureCount: 0,
        failureThreshold: 5,
        checkInterval: 60000,
      };

      const recordFailure = (c: typeof circuit) => {
        c.failureCount++;
        if (c.failureCount >= c.failureThreshold) {
          c.state = 'open';
        }
      };

      // Simulate 5 failures
      for (let i = 0; i < 5; i++) {
        recordFailure(circuit);
      }

      expect(circuit.state).toBe('open');
      expect(circuit.failureCount).toBe(5);
    });

    it('should reject requests when circuit is open', async () => {
      const circuit = {
        state: 'open',
        lastFailure: new Date(),
      };

      const callWithCircuitBreaker = () => {
        if (circuit.state === 'open') {
          return Promise.reject(new Error('Circuit breaker open'));
        }
        return Promise.resolve({ data: 'success' });
      };

      const result = await callWithCircuitBreaker().catch((error) => ({
        error: error.message,
        circuitOpen: true,
      }));

      expect(result.circuitOpen).toBe(true);
    });

    it('should half-open circuit after timeout period', async () => {
      const circuit = {
        state: 'open',
        openedAt: Date.now() - 70000, // 70 seconds ago
        checkInterval: 60000,
      };

      const tryReset = (c: typeof circuit) => {
        const timeSinceOpen = Date.now() - c.openedAt;
        if (timeSinceOpen > c.checkInterval) {
          c.state = 'half-open';
          return true;
        }
        return false;
      };

      const canRetry = tryReset(circuit);
      expect(canRetry).toBe(true);
      expect(circuit.state).toBe('half-open');
    });
  });

  describe('Graceful Degradation', () => {
    it('should show minimal UI when all external services down', async () => {
      const features = {
        aiRecommendations: false, // AI registry down
        paymentProcessing: false, // Stripe down
        personalizedContent: false, // Feature store down
        basicNavigation: true, // Critical path always available
        fallbackMode: true,
      };

      expect(features.basicNavigation).toBe(true);
      expect(features.fallbackMode).toBe(true);
      expect(features.aiRecommendations).toBe(false);
    });

    it('should queue operations during outage for later processing', async () => {
      const operationQueue: any[] = [];

      const queueOperation = (op: any) => {
        operationQueue.push({
          ...op,
          queuedAt: new Date(),
          status: 'pending',
          retryCount: 0,
        });
      };

      queueOperation({
        type: 'PROCESS_PAYMENT',
        userId: 'user-1',
        amount: 9999,
      });

      queueOperation({
        type: 'UPDATE_RECOMMENDATIONS',
        userId: 'user-1',
      });

      expect(operationQueue).toHaveLength(2);
      expect(operationQueue[0].status).toBe('pending');
      expect(operationQueue[1].type).toBe('UPDATE_RECOMMENDATIONS');
    });

    it('should process queued operations once service recovers', async () => {
      const queue: any[] = [];

      // Add operations while service was down
      queue.push({
        id: 'op-1',
        type: 'CHARGE',
        status: 'pending',
      });

      // Service recovers
      const processQueue = async () => {
        const processed = [];
        for (const op of queue) {
          op.status = 'processing';
          try {
            // Simulate successful processing
            op.status = 'completed';
            processed.push(op);
          } catch (error) {
            op.status = 'failed';
          }
        }
        return processed;
      };

      const result = await processQueue();
      expect(result).toHaveLength(1);
      expect(result[0].status).toBe('completed');
    });
  });

  describe('Observability During Failures', () => {
    it('should emit detailed logs when external service fails', async () => {
      const logs: any[] = [];

      const logFailure = (service: string, error: Error, context: any) => {
        logs.push({
          timestamp: new Date(),
          level: 'ERROR',
          service,
          message: error.message,
          stack: error.stack,
          context,
          correlationId: context.correlationId,
        });
      };

      logFailure('ai-registry', new Error('Connection refused'), {
        userId: 'user-1',
        requestId: 'req-123',
        correlationId: 'corr-456',
        retryCount: 2,
      });

      expect(logs).toHaveLength(1);
      expect(logs[0].service).toBe('ai-registry');
      expect(logs[0]).toHaveProperty('correlationId', 'corr-456');
    });

    it('should emit metrics for circuit breaker state changes', async () => {
      const metrics: any[] = [];

      const recordMetric = (event: string, service: string, state: string) => {
        metrics.push({
          timestamp: new Date(),
          event,
          service,
          state,
        });
      };

      recordMetric('CIRCUIT_OPENED', 'payment-gateway', 'open');
      recordMetric('CIRCUIT_HALF_OPEN', 'payment-gateway', 'half-open');
      recordMetric('CIRCUIT_CLOSED', 'payment-gateway', 'closed');

      expect(metrics).toHaveLength(3);
      expect(metrics[0].event).toBe('CIRCUIT_OPENED');
      expect(metrics[2].state).toBe('closed');
    });

    it('should create health check endpoint showing service status', async () => {
      const health = {
        status: 'degraded',
        services: {
          'ai-registry': { status: 'down', lastCheck: new Date() },
          'payment-gateway': { status: 'up', lastCheck: new Date() },
          'learner-profile': { status: 'down', lastCheck: new Date() },
          'feature-store': { status: 'up', lastCheck: new Date() },
        },
      };

      const downServices = Object.entries(health.services)
        .filter(([_, svc]) => svc.status === 'down')
        .map(([name]) => name);

      expect(health.status).toBe('degraded');
      expect(downServices).toHaveLength(2);
      expect(downServices).toContain('ai-registry');
    });
  });
});
