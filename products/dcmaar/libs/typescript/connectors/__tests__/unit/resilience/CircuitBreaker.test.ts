import { CircuitBreaker, CircuitState } from '../../../src/resilience/CircuitBreaker';

describe('CircuitBreaker', () => {
  let breaker: CircuitBreaker;

  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.restoreAllMocks();
    jest.useRealTimers();
  });

  describe('Constructor & Initialization', () => {
    it('should initialize with default config', () => {
      breaker = new CircuitBreaker();
      expect(breaker.getState()).toBe('closed');

      const stats = breaker.getStats();
      expect(stats.failureCount).toBe(0);
      expect(stats.successCount).toBe(0);
      expect(stats.recentRequests).toBe(0);
      expect(stats.failureRate).toBe(0);
    });

    it('should initialize with custom config', () => {
      breaker = new CircuitBreaker({
        failureThreshold: 3,
        successThreshold: 1,
        timeout: 30000,
        rollingWindow: 5000,
        volumeThreshold: 5,
      });

      expect(breaker.getState()).toBe('closed');
    });

    it('should handle partial config with defaults', () => {
      breaker = new CircuitBreaker({
        failureThreshold: 10,
      });

      expect(breaker.getState()).toBe('closed');
    });
  });

  describe('CLOSED State', () => {
    beforeEach(() => {
      breaker = new CircuitBreaker({
        failureThreshold: 3,
        successThreshold: 2,
        timeout: 60000,
        rollingWindow: 10000,
        volumeThreshold: 5,
      });
    });

    it('should execute function successfully', async () => {
      const mockFn = jest.fn().mockResolvedValue('success');
      const result = await breaker.execute(mockFn);

      expect(result).toBe('success');
      expect(mockFn).toHaveBeenCalledTimes(1);
      expect(breaker.getState()).toBe('closed');
    });

    it('should track successful requests', async () => {
      const mockFn = jest.fn().mockResolvedValue('success');

      await breaker.execute(mockFn);
      await breaker.execute(mockFn);

      const stats = breaker.getStats();
      expect(stats.recentRequests).toBe(2);
      expect(stats.recentFailures).toBe(0);
      expect(stats.failureRate).toBe(0);
    });

    it('should reset failure count on success', async () => {
      const mockFn = jest.fn()
        .mockRejectedValueOnce(new Error('fail'))
        .mockResolvedValue('success');

      await expect(breaker.execute(mockFn)).rejects.toThrow('fail');
      expect(breaker.getStats().failureCount).toBe(1);

      await breaker.execute(mockFn);
      expect(breaker.getStats().failureCount).toBe(0);
    });

    it('should track failures', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      await expect(breaker.execute(mockFn)).rejects.toThrow('fail');

      const stats = breaker.getStats();
      expect(stats.failureCount).toBe(1);
      expect(stats.recentFailures).toBe(1);
    });

    it('should emit success event on successful execution', async () => {
      const successHandler = jest.fn();
      breaker.on('success', successHandler);

      const mockFn = jest.fn().mockResolvedValue('result');
      await breaker.execute(mockFn);

      expect(successHandler).toHaveBeenCalledWith({ state: 'closed' });
    });

    it('should emit failure event on failed execution', async () => {
      const failureHandler = jest.fn();
      breaker.on('failure', failureHandler);

      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));
      await expect(breaker.execute(mockFn)).rejects.toThrow('fail');

      expect(failureHandler).toHaveBeenCalledWith({
        state: 'closed',
        failureCount: 1,
      });
    });

    it('should not open circuit if volume threshold not met', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      // Only 3 failures, below volumeThreshold of 5
      for (let i = 0; i < 3; i++) {
        await expect(breaker.execute(mockFn)).rejects.toThrow('fail');
      }

      expect(breaker.getState()).toBe('closed');
    });

    it('should open circuit when failure threshold exceeded', async () => {
      const stateChangeHandler = jest.fn();
      breaker.on('stateChange', stateChangeHandler);

      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      // 5 failures to meet volumeThreshold, 3 to exceed failureThreshold
      for (let i = 0; i < 5; i++) {
        await expect(breaker.execute(mockFn)).rejects.toThrow('fail');
      }

      expect(breaker.getState()).toBe('open');
      expect(stateChangeHandler).toHaveBeenCalledWith({
        from: 'closed',
        to: 'open',
        timestamp: expect.any(Number),
      });
    });

    it('should handle mixed success/failure patterns', async () => {
      const mockFn = jest.fn()
        .mockResolvedValueOnce('ok')
        .mockRejectedValueOnce(new Error('fail'))
        .mockResolvedValueOnce('ok')
        .mockRejectedValueOnce(new Error('fail'))
        .mockResolvedValueOnce('ok');

      await breaker.execute(mockFn);
      await expect(breaker.execute(mockFn)).rejects.toThrow();
      await breaker.execute(mockFn);
      await expect(breaker.execute(mockFn)).rejects.toThrow();
      await breaker.execute(mockFn);

      const stats = breaker.getStats();
      expect(stats.recentRequests).toBe(5);
      expect(stats.recentFailures).toBe(2);
      expect(stats.failureRate).toBe(0.4);
      expect(breaker.getState()).toBe('closed');
    });

    it('should clean old request history', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      // Create some failures
      for (let i = 0; i < 3; i++) {
        await expect(breaker.execute(mockFn)).rejects.toThrow();
      }

      expect(breaker.getStats().recentRequests).toBe(3);

      // Advance time beyond rolling window
      jest.advanceTimersByTime(11000);

      const stats = breaker.getStats();
      expect(stats.recentRequests).toBe(0);
      expect(stats.recentFailures).toBe(0);
    });
  });

  describe('OPEN State', () => {
    beforeEach(() => {
      breaker = new CircuitBreaker({
        failureThreshold: 3,
        successThreshold: 2,
        timeout: 60000,
        rollingWindow: 10000,
        volumeThreshold: 3,
      });
    });

    it('should reject requests when circuit is open', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      // Open the circuit
      for (let i = 0; i < 3; i++) {
        await expect(breaker.execute(mockFn)).rejects.toThrow('fail');
      }

      expect(breaker.getState()).toBe('open');

      // Reset mock to ensure it's not called
      mockFn.mockClear();

      await expect(breaker.execute(mockFn)).rejects.toThrow('Circuit breaker is open');
      expect(mockFn).not.toHaveBeenCalled();
    });

    it('should emit rejected event when blocking requests', async () => {
      const rejectedHandler = jest.fn();
      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      // Open the circuit
      for (let i = 0; i < 3; i++) {
        await expect(breaker.execute(mockFn)).rejects.toThrow('fail');
      }

      breaker.on('rejected', rejectedHandler);

      await expect(breaker.execute(mockFn)).rejects.toThrow('Circuit breaker is open');

      expect(rejectedHandler).toHaveBeenCalledWith({
        state: 'open',
        error: expect.any(Error),
      });
    });

    it('should transition to half-open after timeout', async () => {
      const stateChangeHandler = jest.fn();
      breaker.on('stateChange', stateChangeHandler);

      const mockFn = jest.fn()
        .mockRejectedValue(new Error('fail'))
        .mockResolvedValue('success');

      // Open the circuit
      for (let i = 0; i < 3; i++) {
        await expect(breaker.execute(mockFn)).rejects.toThrow('fail');
      }

      expect(breaker.getState()).toBe('open');
      stateChangeHandler.mockClear();

      // Advance time past timeout
      jest.advanceTimersByTime(61000);

      // This should transition to half-open and execute
      mockFn.mockResolvedValue('success');
      const result = await breaker.execute(mockFn);

      expect(result).toBe('success');
      expect(stateChangeHandler).toHaveBeenCalledWith({
        from: 'open',
        to: 'half-open',
        timestamp: expect.any(Number),
      });
    });

    it('should maintain nextAttempt timestamp', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      const beforeTime = Date.now();

      // Open the circuit
      for (let i = 0; i < 3; i++) {
        await expect(breaker.execute(mockFn)).rejects.toThrow('fail');
      }

      const stats = breaker.getStats();
      expect(stats.nextAttempt).toBeGreaterThan(beforeTime);
      expect(stats.nextAttempt).toBeLessThanOrEqual(beforeTime + 60000);
    });

    it('should clear nextAttempt when null after timeout expires', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      // Open the circuit
      for (let i = 0; i < 3; i++) {
        await expect(breaker.execute(mockFn)).rejects.toThrow('fail');
      }

      expect(breaker.getStats().nextAttempt).not.toBeNull();

      // Advance time way past timeout
      jest.advanceTimersByTime(120000);

      const stats = breaker.getStats();
      expect(stats.nextAttempt).toBeNull();
    });
  });

  describe('HALF-OPEN State', () => {
    beforeEach(() => {
      breaker = new CircuitBreaker({
        failureThreshold: 3,
        successThreshold: 2,
        timeout: 60000,
        rollingWindow: 10000,
        volumeThreshold: 3,
      });
    });

    async function openCircuit() {
      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));
      for (let i = 0; i < 3; i++) {
        await expect(breaker.execute(mockFn)).rejects.toThrow('fail');
      }
      expect(breaker.getState()).toBe('open');
    }

    it('should allow requests in half-open state', async () => {
      await openCircuit();

      jest.advanceTimersByTime(61000);

      const mockFn = jest.fn().mockResolvedValue('success');
      const result = await breaker.execute(mockFn);

      expect(result).toBe('success');
      expect(breaker.getState()).toBe('half-open');
    });

    it('should close circuit after success threshold met', async () => {
      const stateChangeHandler = jest.fn();

      await openCircuit();
      jest.advanceTimersByTime(61000);

      breaker.on('stateChange', stateChangeHandler);

      const mockFn = jest.fn().mockResolvedValue('success');

      // First success - stays half-open
      await breaker.execute(mockFn);
      expect(breaker.getState()).toBe('half-open');

      // Second success - should close
      await breaker.execute(mockFn);
      expect(breaker.getState()).toBe('closed');

      expect(stateChangeHandler).toHaveBeenCalledWith({
        from: 'half-open',
        to: 'closed',
        timestamp: expect.any(Number),
      });
    });

    it('should reopen circuit on any failure in half-open', async () => {
      const stateChangeHandler = jest.fn();

      await openCircuit();
      jest.advanceTimersByTime(61000);

      breaker.on('stateChange', stateChangeHandler);

      const mockFn = jest.fn()
        .mockResolvedValueOnce('success')
        .mockRejectedValueOnce(new Error('fail'));

      // First success
      await breaker.execute(mockFn);
      expect(breaker.getState()).toBe('half-open');

      // Failure should reopen
      await expect(breaker.execute(mockFn)).rejects.toThrow('fail');
      expect(breaker.getState()).toBe('open');

      expect(stateChangeHandler).toHaveBeenCalledWith({
        from: 'half-open',
        to: 'open',
        timestamp: expect.any(Number),
      });
    });

    it('should reset success count when transitioning to closed', async () => {
      await openCircuit();
      jest.advanceTimersByTime(61000);

      const mockFn = jest.fn().mockResolvedValue('success');

      // Meet success threshold
      await breaker.execute(mockFn);
      await breaker.execute(mockFn);

      expect(breaker.getState()).toBe('closed');

      const stats = breaker.getStats();
      expect(stats.successCount).toBe(0);
    });

    it('should reset success count when failing in half-open', async () => {
      await openCircuit();
      jest.advanceTimersByTime(61000);

      const mockFn = jest.fn()
        .mockResolvedValueOnce('success')
        .mockRejectedValueOnce(new Error('fail'));

      await breaker.execute(mockFn);
      expect(breaker.getStats().successCount).toBe(1);

      await expect(breaker.execute(mockFn)).rejects.toThrow('fail');
      expect(breaker.getStats().successCount).toBe(0);
    });

    it('should track consecutive successes', async () => {
      await openCircuit();
      jest.advanceTimersByTime(61000);

      const mockFn = jest.fn().mockResolvedValue('success');

      await breaker.execute(mockFn);
      expect(breaker.getStats().successCount).toBe(1);

      await breaker.execute(mockFn);
      // Should be closed now, so success count reset
      expect(breaker.getStats().successCount).toBe(0);
    });
  });

  describe('State Transitions', () => {
    beforeEach(() => {
      breaker = new CircuitBreaker({
        failureThreshold: 2,
        successThreshold: 2,
        timeout: 30000,
        volumeThreshold: 2,
      });
    });

    it('should emit stateChange event on all transitions', async () => {
      const stateChangeHandler = jest.fn();
      breaker.on('stateChange', stateChangeHandler);

      const mockFn = jest.fn()
        .mockRejectedValue(new Error('fail'))
        .mockResolvedValue('success');

      // Closed -> Open
      await expect(breaker.execute(mockFn)).rejects.toThrow();
      await expect(breaker.execute(mockFn)).rejects.toThrow();

      expect(stateChangeHandler).toHaveBeenCalledWith({
        from: 'closed',
        to: 'open',
        timestamp: expect.any(Number),
      });

      stateChangeHandler.mockClear();
      jest.advanceTimersByTime(31000);

      // Open -> Half-Open
      mockFn.mockResolvedValue('success');
      await breaker.execute(mockFn);

      expect(stateChangeHandler).toHaveBeenCalledWith({
        from: 'open',
        to: 'half-open',
        timestamp: expect.any(Number),
      });

      stateChangeHandler.mockClear();

      // Half-Open -> Closed
      await breaker.execute(mockFn);

      expect(stateChangeHandler).toHaveBeenCalledWith({
        from: 'half-open',
        to: 'closed',
        timestamp: expect.any(Number),
      });
    });

    it('should not emit stateChange when already in target state', () => {
      const stateChangeHandler = jest.fn();
      breaker.on('stateChange', stateChangeHandler);

      breaker.forceClose();
      breaker.forceClose();

      expect(stateChangeHandler).not.toHaveBeenCalled();
    });
  });

  describe('Manual Control', () => {
    beforeEach(() => {
      breaker = new CircuitBreaker({
        failureThreshold: 3,
        volumeThreshold: 3,
      });
    });

    it('should reset circuit breaker', async () => {
      const resetHandler = jest.fn();
      breaker.on('reset', resetHandler);

      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      // Create some failures
      for (let i = 0; i < 3; i++) {
        await expect(breaker.execute(mockFn)).rejects.toThrow();
      }

      expect(breaker.getState()).toBe('open');
      expect(breaker.getStats().failureCount).toBeGreaterThan(0);

      breaker.reset();

      expect(breaker.getState()).toBe('closed');
      const stats = breaker.getStats();
      expect(stats.failureCount).toBe(0);
      expect(stats.successCount).toBe(0);
      expect(stats.recentRequests).toBe(0);
      expect(resetHandler).toHaveBeenCalled();
    });

    it('should force open circuit', () => {
      expect(breaker.getState()).toBe('closed');

      breaker.forceOpen();

      expect(breaker.getState()).toBe('open');
    });

    it('should force close circuit', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      // Open the circuit
      for (let i = 0; i < 3; i++) {
        await expect(breaker.execute(mockFn)).rejects.toThrow();
      }

      expect(breaker.getState()).toBe('open');

      breaker.forceClose();

      expect(breaker.getState()).toBe('closed');
      const stats = breaker.getStats();
      expect(stats.failureCount).toBe(0);
      expect(stats.successCount).toBe(0);
    });

    it('should emit stateChange when forcing states', () => {
      const stateChangeHandler = jest.fn();
      breaker.on('stateChange', stateChangeHandler);

      breaker.forceOpen();
      expect(stateChangeHandler).toHaveBeenCalledWith({
        from: 'closed',
        to: 'open',
        timestamp: expect.any(Number),
      });

      stateChangeHandler.mockClear();

      breaker.forceClose();
      expect(stateChangeHandler).toHaveBeenCalledWith({
        from: 'open',
        to: 'closed',
        timestamp: expect.any(Number),
      });
    });
  });

  describe('Statistics & Monitoring', () => {
    beforeEach(() => {
      breaker = new CircuitBreaker({
        failureThreshold: 5,
        rollingWindow: 10000,
        volumeThreshold: 10,
      });
    });

    it('should return accurate statistics', async () => {
      const mockFn = jest.fn()
        .mockResolvedValueOnce('ok')
        .mockResolvedValueOnce('ok')
        .mockRejectedValueOnce(new Error('fail'))
        .mockResolvedValueOnce('ok');

      await breaker.execute(mockFn);
      await breaker.execute(mockFn);
      await expect(breaker.execute(mockFn)).rejects.toThrow();
      await breaker.execute(mockFn);

      const stats = breaker.getStats();
      expect(stats.state).toBe('closed');
      expect(stats.recentRequests).toBe(4);
      expect(stats.recentFailures).toBe(1);
      expect(stats.failureRate).toBe(0.25);
      expect(stats.failureCount).toBe(0); // Reset on last success
    });

    it('should calculate failure rate correctly', async () => {
      const mockFn = jest.fn()
        .mockRejectedValueOnce(new Error('fail'))
        .mockRejectedValueOnce(new Error('fail'))
        .mockResolvedValueOnce('ok')
        .mockResolvedValueOnce('ok');

      await expect(breaker.execute(mockFn)).rejects.toThrow();
      await expect(breaker.execute(mockFn)).rejects.toThrow();
      await breaker.execute(mockFn);
      await breaker.execute(mockFn);

      const stats = breaker.getStats();
      expect(stats.failureRate).toBe(0.5);
    });

    it('should return 0 failure rate with no requests', () => {
      const stats = breaker.getStats();
      expect(stats.failureRate).toBe(0);
    });

    it('should track only requests within rolling window', async () => {
      const mockFn = jest.fn().mockResolvedValue('ok');

      // Make some requests
      await breaker.execute(mockFn);
      await breaker.execute(mockFn);

      expect(breaker.getStats().recentRequests).toBe(2);

      // Advance time beyond rolling window
      jest.advanceTimersByTime(11000);

      // Make new request
      await breaker.execute(mockFn);

      const stats = breaker.getStats();
      expect(stats.recentRequests).toBe(1);
    });
  });

  describe('Concurrent Request Handling', () => {
    beforeEach(() => {
      breaker = new CircuitBreaker({
        failureThreshold: 3,
        volumeThreshold: 3,
      });
    });

    it('should handle concurrent successful requests', async () => {
      const mockFn = jest.fn().mockResolvedValue('success');

      const promises = Array(10).fill(null).map(() => breaker.execute(mockFn));
      const results = await Promise.all(promises);

      expect(results).toHaveLength(10);
      expect(results.every(r => r === 'success')).toBe(true);
      expect(mockFn).toHaveBeenCalledTimes(10);
    });

    it('should handle concurrent failures correctly', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      const promises = Array(5).fill(null).map(() =>
        breaker.execute(mockFn).catch(e => e)
      );

      await Promise.all(promises);

      const stats = breaker.getStats();
      expect(stats.recentFailures).toBe(5);
    });

    it('should block all concurrent requests when open', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      // Open the circuit
      for (let i = 0; i < 3; i++) {
        await expect(breaker.execute(mockFn)).rejects.toThrow();
      }

      mockFn.mockClear();

      const promises = Array(5).fill(null).map(() =>
        breaker.execute(mockFn).catch(e => e.message)
      );

      const results = await Promise.all(promises);

      expect(results.every(r => r === 'Circuit breaker is open')).toBe(true);
      expect(mockFn).not.toHaveBeenCalled();
    });
  });

  describe('Edge Cases & Error Scenarios', () => {
    beforeEach(() => {
      breaker = new CircuitBreaker({
        failureThreshold: 3,
        volumeThreshold: 3,
      });
    });

    it('should handle function that throws non-Error', async () => {
      const mockFn = jest.fn().mockRejectedValue('string error');

      await expect(breaker.execute(mockFn)).rejects.toBe('string error');

      expect(breaker.getStats().failureCount).toBe(1);
    });

    it('should handle function returning undefined', async () => {
      const mockFn = jest.fn().mockResolvedValue(undefined);

      const result = await breaker.execute(mockFn);

      expect(result).toBeUndefined();
      expect(breaker.getState()).toBe('closed');
    });

    it('should handle function returning null', async () => {
      const mockFn = jest.fn().mockResolvedValue(null);

      const result = await breaker.execute(mockFn);

      expect(result).toBeNull();
      expect(breaker.getState()).toBe('closed');
    });

    it('should handle immediate successive state transitions', async () => {
      const stateChangeHandler = jest.fn();
      breaker.on('stateChange', stateChangeHandler);

      breaker.forceOpen();
      breaker.forceClose();
      breaker.forceOpen();

      expect(stateChangeHandler).toHaveBeenCalledTimes(3);
    });

    it('should handle very high failure threshold', async () => {
      breaker = new CircuitBreaker({
        failureThreshold: 1000,
        volumeThreshold: 1000,
      });

      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      // Should not open even with many failures
      for (let i = 0; i < 100; i++) {
        await expect(breaker.execute(mockFn)).rejects.toThrow();
      }

      expect(breaker.getState()).toBe('closed');
    });

    it('should handle zero timeout correctly', async () => {
      breaker = new CircuitBreaker({
        failureThreshold: 2,
        volumeThreshold: 2,
        timeout: 0,
      });

      const mockFn = jest.fn()
        .mockRejectedValue(new Error('fail'))
        .mockResolvedValue('success');

      await expect(breaker.execute(mockFn)).rejects.toThrow();
      await expect(breaker.execute(mockFn)).rejects.toThrow();

      expect(breaker.getState()).toBe('open');

      // Should immediately allow retry
      mockFn.mockResolvedValue('success');
      const result = await breaker.execute(mockFn);

      expect(result).toBe('success');
    });

    it('should handle very small rolling window', async () => {
      breaker = new CircuitBreaker({
        rollingWindow: 100,
      });

      const mockFn = jest.fn().mockResolvedValue('ok');

      await breaker.execute(mockFn);

      jest.advanceTimersByTime(150);

      const stats = breaker.getStats();
      expect(stats.recentRequests).toBe(0);
    });

    it('should maintain state consistency during rapid operations', async () => {
      const operations = [];

      for (let i = 0; i < 50; i++) {
        const mockFn = jest.fn().mockResolvedValue(`result-${i}`);
        operations.push(breaker.execute(mockFn));
      }

      await Promise.all(operations);

      expect(breaker.getState()).toBe('closed');
      expect(breaker.getStats().recentRequests).toBe(50);
    });
  });

  describe('Event Emission', () => {
    beforeEach(() => {
      breaker = new CircuitBreaker({
        failureThreshold: 2,
        volumeThreshold: 2,
      });
    });

    it('should emit all lifecycle events', async () => {
      const events = {
        success: jest.fn(),
        failure: jest.fn(),
        stateChange: jest.fn(),
        rejected: jest.fn(),
        reset: jest.fn(),
      };

      Object.entries(events).forEach(([event, handler]) => {
        breaker.on(event, handler);
      });

      // Success
      const successFn = jest.fn().mockResolvedValue('ok');
      await breaker.execute(successFn);
      expect(events.success).toHaveBeenCalled();

      // Failure
      const failFn = jest.fn().mockRejectedValue(new Error('fail'));
      await expect(breaker.execute(failFn)).rejects.toThrow();
      expect(events.failure).toHaveBeenCalled();

      // State change (to open)
      await expect(breaker.execute(failFn)).rejects.toThrow();
      expect(events.stateChange).toHaveBeenCalled();

      // Rejected
      await expect(breaker.execute(failFn)).rejects.toThrow('Circuit breaker is open');
      expect(events.rejected).toHaveBeenCalled();

      // Reset
      breaker.reset();
      expect(events.reset).toHaveBeenCalled();
    });

    it('should include proper event data', async () => {
      const successHandler = jest.fn();
      const failureHandler = jest.fn();
      const stateChangeHandler = jest.fn();

      breaker.on('success', successHandler);
      breaker.on('failure', failureHandler);
      breaker.on('stateChange', stateChangeHandler);

      const successFn = jest.fn().mockResolvedValue('ok');
      await breaker.execute(successFn);

      expect(successHandler).toHaveBeenCalledWith({
        state: expect.any(String),
      });

      const failFn = jest.fn().mockRejectedValue(new Error('fail'));
      await expect(breaker.execute(failFn)).rejects.toThrow();
      await expect(breaker.execute(failFn)).rejects.toThrow();

      expect(failureHandler).toHaveBeenCalledWith({
        state: expect.any(String),
        failureCount: expect.any(Number),
      });

      expect(stateChangeHandler).toHaveBeenCalledWith({
        from: 'closed',
        to: 'open',
        timestamp: expect.any(Number),
      });
    });
  });

  describe('Configuration Edge Cases', () => {
    it('should handle all config parameters as zero', () => {
      breaker = new CircuitBreaker({
        failureThreshold: 0,
        successThreshold: 0,
        timeout: 0,
        rollingWindow: 0,
        volumeThreshold: 0,
      });

      expect(breaker.getState()).toBe('closed');
    });

    it('should handle very large config values', () => {
      breaker = new CircuitBreaker({
        failureThreshold: Number.MAX_SAFE_INTEGER,
        successThreshold: Number.MAX_SAFE_INTEGER,
        timeout: Number.MAX_SAFE_INTEGER,
        rollingWindow: Number.MAX_SAFE_INTEGER,
        volumeThreshold: Number.MAX_SAFE_INTEGER,
      });

      expect(breaker.getState()).toBe('closed');
    });

    it('should handle undefined config gracefully', () => {
      breaker = new CircuitBreaker(undefined);
      expect(breaker.getState()).toBe('closed');
    });
  });

  describe('Memory & Performance', () => {
    it('should not accumulate request history indefinitely', async () => {
      breaker = new CircuitBreaker({
        rollingWindow: 1000,
      });

      const mockFn = jest.fn().mockResolvedValue('ok');

      // Create many requests
      for (let i = 0; i < 1000; i++) {
        await breaker.execute(mockFn);
      }

      // Advance time to clean history
      jest.advanceTimersByTime(2000);

      const stats = breaker.getStats();
      expect(stats.recentRequests).toBe(0);
    });

    it('should handle rapid state changes efficiently', () => {
      const stateChangeHandler = jest.fn();
      breaker.on('stateChange', stateChangeHandler);

      for (let i = 0; i < 100; i++) {
        breaker.forceOpen();
        breaker.forceClose();
      }

      expect(stateChangeHandler).toHaveBeenCalledTimes(200);
    });
  });
});
