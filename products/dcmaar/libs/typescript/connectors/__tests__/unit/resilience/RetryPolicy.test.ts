import { RetryPolicy, withRetry, RetryPresets } from '../../../src/resilience/RetryPolicy';

describe('RetryPolicy', () => {
  let retryPolicy: RetryPolicy;

  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.restoreAllMocks();
    jest.useRealTimers();
  });

  describe('Constructor & Initialization', () => {
    it('should initialize with default config', () => {
      retryPolicy = new RetryPolicy();
      const config = retryPolicy.getConfig();

      expect(config.maxAttempts).toBe(3);
      expect(config.initialDelay).toBe(1000);
      expect(config.maxDelay).toBe(30000);
      expect(config.backoffMultiplier).toBe(2);
      expect(config.jitter).toBe(true);
    });

    it('should initialize with custom config', () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 5,
        initialDelay: 500,
        maxDelay: 10000,
        backoffMultiplier: 1.5,
        jitter: false,
      });

      const config = retryPolicy.getConfig();
      expect(config.maxAttempts).toBe(5);
      expect(config.initialDelay).toBe(500);
      expect(config.maxDelay).toBe(10000);
      expect(config.backoffMultiplier).toBe(1.5);
      expect(config.jitter).toBe(false);
    });

    it('should handle partial config with defaults', () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 10,
      });

      const config = retryPolicy.getConfig();
      expect(config.maxAttempts).toBe(10);
      expect(config.initialDelay).toBe(1000);
    });

    it('should accept custom isRetryable predicate', () => {
      const customPredicate = (error: Error) => error.message.includes('retry');
      retryPolicy = new RetryPolicy({
        isRetryable: customPredicate,
      });

      const config = retryPolicy.getConfig();
      expect(config.isRetryable).toBe(customPredicate);
    });

    it('should accept timeout configuration', () => {
      retryPolicy = new RetryPolicy({
        timeout: 5000,
      });

      const config = retryPolicy.getConfig();
      expect(config.timeout).toBe(5000);
    });
  });

  describe('Successful Execution', () => {
    beforeEach(() => {
      retryPolicy = new RetryPolicy();
    });

    it('should execute function successfully on first attempt', async () => {
      const mockFn = jest.fn().mockResolvedValue('success');
      const result = await retryPolicy.execute(mockFn);

      expect(result).toBe('success');
      expect(mockFn).toHaveBeenCalledTimes(1);
    });

    it('should emit attempt and success events', async () => {
      const attemptHandler = jest.fn();
      const successHandler = jest.fn();

      retryPolicy.on('attempt', attemptHandler);
      retryPolicy.on('success', successHandler);

      const mockFn = jest.fn().mockResolvedValue('result');
      await retryPolicy.execute(mockFn);

      expect(attemptHandler).toHaveBeenCalledWith({
        attempt: 1,
        maxAttempts: 3,
      });

      expect(successHandler).toHaveBeenCalledWith({
        attempt: 1,
      });
    });

    it('should return correct result type', async () => {
      retryPolicy = new RetryPolicy();

      const mockFn = jest.fn().mockResolvedValue({ data: 'test', count: 42 });
      const result = await retryPolicy.execute(mockFn);

      expect(result).toEqual({ data: 'test', count: 42 });
    });

    it('should handle function returning undefined', async () => {
      const mockFn = jest.fn().mockResolvedValue(undefined);
      const result = await retryPolicy.execute(mockFn);

      expect(result).toBeUndefined();
    });

    it('should handle function returning null', async () => {
      const mockFn = jest.fn().mockResolvedValue(null);
      const result = await retryPolicy.execute(mockFn);

      expect(result).toBeNull();
    });
  });

  describe('Retry Attempts', () => {
    beforeEach(() => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 3,
        initialDelay: 1000,
        backoffMultiplier: 2,
        jitter: false,
      });
    });

    it('should retry on retryable errors', async () => {
      const mockFn = jest.fn()
        .mockRejectedValueOnce(new Error('ECONNRESET'))
        .mockRejectedValueOnce(new Error('ECONNRESET'))
        .mockResolvedValue('success');

      const promise = retryPolicy.execute(mockFn);

      // Advance through delays
      await jest.advanceTimersByTimeAsync(1000); // First retry
      await jest.advanceTimersByTimeAsync(2000); // Second retry

      const result = await promise;

      expect(result).toBe('success');
      expect(mockFn).toHaveBeenCalledTimes(3);
    });

    it('should emit retry events with correct data', async () => {
      const retryHandler = jest.fn();
      retryPolicy.on('retry', retryHandler);

      const mockFn = jest.fn()
        .mockRejectedValueOnce(new Error('ECONNRESET'))
        .mockResolvedValue('success');

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(1000);

      expect(retryHandler).toHaveBeenCalledWith({
        attempt: 1,
        delay: 1000,
        error: expect.any(Error),
      });
    });

    it('should respect maxAttempts limit', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('ECONNRESET'));

      const promise = retryPolicy.execute(mockFn);

      // Advance through all retry delays
      await jest.advanceTimersByTimeAsync(1000); // First retry
      await jest.advanceTimersByTimeAsync(2000); // Second retry

      await expect(promise).rejects.toThrow('ECONNRESET');
      expect(mockFn).toHaveBeenCalledTimes(3);
    });

    it('should emit error events for each failure', async () => {
      const errorHandler = jest.fn();
      retryPolicy.on('error', errorHandler);

      const mockFn = jest.fn().mockRejectedValue(new Error('ECONNRESET'));

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(1000);
      await jest.advanceTimersByTimeAsync(2000);

      await expect(promise).rejects.toThrow();

      expect(errorHandler).toHaveBeenCalledTimes(3);
      expect(errorHandler).toHaveBeenNthCalledWith(1, {
        attempt: 1,
        error: expect.any(Error),
        willRetry: true,
      });
      expect(errorHandler).toHaveBeenNthCalledWith(3, {
        attempt: 3,
        error: expect.any(Error),
        willRetry: false,
      });
    });

    it('should emit exhausted event after max attempts', async () => {
      const exhaustedHandler = jest.fn();
      retryPolicy.on('exhausted', exhaustedHandler);

      const mockFn = jest.fn().mockRejectedValue(new Error('ETIMEDOUT'));

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(1000);
      await jest.advanceTimersByTimeAsync(2000);

      await expect(promise).rejects.toThrow();

      expect(exhaustedHandler).toHaveBeenCalledWith({
        attempts: 3,
        error: expect.any(Error),
      });
    });

    it('should succeed after retries', async () => {
      const successHandler = jest.fn();
      retryPolicy.on('success', successHandler);

      const mockFn = jest.fn()
        .mockRejectedValueOnce(new Error('ECONNRESET'))
        .mockRejectedValueOnce(new Error('ECONNRESET'))
        .mockResolvedValue('final success');

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(1000);
      await jest.advanceTimersByTimeAsync(2000);

      const result = await promise;

      expect(result).toBe('final success');
      expect(successHandler).toHaveBeenCalledWith({
        attempt: 3,
      });
    });
  });

  describe('Exponential Backoff', () => {
    it('should apply exponential backoff without jitter', async () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 4,
        initialDelay: 100,
        backoffMultiplier: 2,
        jitter: false,
      });

      const retryHandler = jest.fn();
      retryPolicy.on('retry', retryHandler);

      const mockFn = jest.fn().mockRejectedValue(new Error('ECONNRESET'));

      const promise = retryPolicy.execute(mockFn);

      // First retry: 100ms
      await jest.advanceTimersByTimeAsync(100);
      expect(retryHandler).toHaveBeenNthCalledWith(1, {
        attempt: 1,
        delay: 100,
        error: expect.any(Error),
      });

      // Second retry: 200ms (100 * 2)
      await jest.advanceTimersByTimeAsync(200);
      expect(retryHandler).toHaveBeenNthCalledWith(2, {
        attempt: 2,
        delay: 200,
        error: expect.any(Error),
      });

      // Third retry: 400ms (100 * 2^2)
      await jest.advanceTimersByTimeAsync(400);
      expect(retryHandler).toHaveBeenNthCalledWith(3, {
        attempt: 3,
        delay: 400,
        error: expect.any(Error),
      });

      await expect(promise).rejects.toThrow();
    });

    it('should cap delay at maxDelay', async () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 5,
        initialDelay: 1000,
        maxDelay: 2000,
        backoffMultiplier: 2,
        jitter: false,
      });

      const retryHandler = jest.fn();
      retryPolicy.on('retry', retryHandler);

      const mockFn = jest.fn().mockRejectedValue(new Error('ECONNRESET'));

      const promise = retryPolicy.execute(mockFn);

      await jest.advanceTimersByTimeAsync(1000); // 1000ms
      await jest.advanceTimersByTimeAsync(2000); // Capped at 2000ms
      await jest.advanceTimersByTimeAsync(2000); // Capped at 2000ms
      await jest.advanceTimersByTimeAsync(2000); // Capped at 2000ms

      const delays = retryHandler.mock.calls.map(call => call[0].delay);
      expect(delays[0]).toBe(1000);
      expect(delays[1]).toBe(2000); // Would be 2000, capped
      expect(delays[2]).toBe(2000); // Would be 4000, capped
      expect(delays[3]).toBe(2000); // Would be 8000, capped

      await expect(promise).rejects.toThrow();
    });

    it('should apply different backoff multipliers', async () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 3,
        initialDelay: 100,
        backoffMultiplier: 3,
        jitter: false,
      });

      const retryHandler = jest.fn();
      retryPolicy.on('retry', retryHandler);

      const mockFn = jest.fn().mockRejectedValue(new Error('ECONNRESET'));

      const promise = retryPolicy.execute(mockFn);

      await jest.advanceTimersByTimeAsync(100);  // 100ms
      await jest.advanceTimersByTimeAsync(300);  // 300ms (100 * 3)

      const delays = retryHandler.mock.calls.map(call => call[0].delay);
      expect(delays[0]).toBe(100);
      expect(delays[1]).toBe(300);

      await expect(promise).rejects.toThrow();
    });
  });

  describe('Jitter', () => {
    it('should apply jitter when enabled', async () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 3,
        initialDelay: 1000,
        backoffMultiplier: 2,
        jitter: true,
      });

      const retryHandler = jest.fn();
      retryPolicy.on('retry', retryHandler);

      const mockFn = jest.fn().mockRejectedValue(new Error('ECONNRESET'));

      const promise = retryPolicy.execute(mockFn);

      // Let timers advance
      await jest.advanceTimersByTimeAsync(5000);

      const delays = retryHandler.mock.calls.map(call => call[0].delay);

      // With jitter, delays should be within ±10% of base delay
      // First delay: 1000 ± 100
      expect(delays[0]).toBeGreaterThanOrEqual(900);
      expect(delays[0]).toBeLessThanOrEqual(1100);

      // Second delay: 2000 ± 200
      expect(delays[1]).toBeGreaterThanOrEqual(1800);
      expect(delays[1]).toBeLessThanOrEqual(2200);

      await expect(promise).rejects.toThrow();
    });

    it('should have consistent delays without jitter', async () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 3,
        initialDelay: 1000,
        backoffMultiplier: 2,
        jitter: false,
      });

      const retryHandler = jest.fn();
      retryPolicy.on('retry', retryHandler);

      const mockFn = jest.fn().mockRejectedValue(new Error('ECONNRESET'));

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(5000);

      const delays = retryHandler.mock.calls.map(call => call[0].delay);

      expect(delays[0]).toBe(1000);
      expect(delays[1]).toBe(2000);

      await expect(promise).rejects.toThrow();
    });
  });

  describe('Timeout Handling', () => {
    it('should timeout operation when timeout is set', async () => {
      retryPolicy = new RetryPolicy({
        timeout: 100,
        maxAttempts: 3,
      });

      const slowFn = jest.fn().mockImplementation(
        () => new Promise(resolve => setTimeout(resolve, 200))
      );

      const promise = retryPolicy.execute(slowFn);

      // Advance past timeout
      await jest.advanceTimersByTimeAsync(150);

      await expect(promise).rejects.toThrow('Operation timed out after 100ms');
    });

    it('should retry after timeout', async () => {
      retryPolicy = new RetryPolicy({
        timeout: 100,
        maxAttempts: 3,
        initialDelay: 50,
        jitter: false,
      });

      let callCount = 0;
      const mockFn = jest.fn().mockImplementation(() => {
        callCount++;
        if (callCount < 3) {
          return new Promise(resolve => setTimeout(resolve, 200));
        }
        return Promise.resolve('success');
      });

      const promise = retryPolicy.execute(mockFn);

      // First attempt timeout
      await jest.advanceTimersByTimeAsync(150);
      // First retry delay
      await jest.advanceTimersByTimeAsync(50);
      // Second attempt timeout
      await jest.advanceTimersByTimeAsync(150);
      // Second retry delay
      await jest.advanceTimersByTimeAsync(50);
      // Third attempt succeeds
      await jest.advanceTimersByTimeAsync(10);

      const result = await promise;
      expect(result).toBe('success');
      expect(mockFn).toHaveBeenCalledTimes(3);
    });

    it('should not apply timeout when not configured', async () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 1,
      });

      const slowFn = jest.fn().mockImplementation(
        () => new Promise(resolve => setTimeout(() => resolve('done'), 5000))
      );

      const promise = retryPolicy.execute(slowFn);

      await jest.advanceTimersByTimeAsync(6000);

      const result = await promise;
      expect(result).toBe('done');
    });
  });

  describe('Custom Retry Predicate', () => {
    it('should use custom isRetryable predicate', async () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 3,
        isRetryable: (error) => error.message.includes('retry-me'),
      });

      const mockFn = jest.fn()
        .mockRejectedValueOnce(new Error('retry-me'))
        .mockResolvedValue('success');

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(2000);

      const result = await promise;
      expect(result).toBe('success');
      expect(mockFn).toHaveBeenCalledTimes(2);
    });

    it('should not retry on non-retryable errors', async () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 3,
        isRetryable: (error) => error.message.includes('retry-me'),
      });

      const mockFn = jest.fn().mockRejectedValue(new Error('do-not-retry'));

      await expect(retryPolicy.execute(mockFn)).rejects.toThrow('do-not-retry');

      expect(mockFn).toHaveBeenCalledTimes(1);
    });

    it('should handle complex retry logic', async () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 5,
        isRetryable: (error: any) => {
          return error.code === 'TRANSIENT' || error.message.includes('temporary');
        },
      });

      const mockFn = jest.fn()
        .mockRejectedValueOnce(Object.assign(new Error('error'), { code: 'TRANSIENT' }))
        .mockRejectedValueOnce(new Error('temporary failure'))
        .mockResolvedValue('success');

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(5000);

      const result = await promise;
      expect(result).toBe('success');
      expect(mockFn).toHaveBeenCalledTimes(3);
    });
  });

  describe('Default Retryable Errors', () => {
    beforeEach(() => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 3,
        initialDelay: 100,
      });
    });

    const retryableErrorCodes = [
      'ECONNRESET',
      'ECONNREFUSED',
      'ETIMEDOUT',
      'ENOTFOUND',
      'ENETUNREACH',
      'EAI_AGAIN',
    ];

    retryableErrorCodes.forEach(code => {
      it(`should retry on ${code} error`, async () => {
        const error = new Error(`Network error: ${code}`);
        (error as any).code = code;

        const mockFn = jest.fn()
          .mockRejectedValueOnce(error)
          .mockResolvedValue('success');

        const promise = retryPolicy.execute(mockFn);
        await jest.advanceTimersByTimeAsync(200);

        const result = await promise;
        expect(result).toBe('success');
        expect(mockFn).toHaveBeenCalledTimes(2);
      });
    });

    it('should not retry on non-network errors', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('Validation error'));

      await expect(retryPolicy.execute(mockFn)).rejects.toThrow('Validation error');
      expect(mockFn).toHaveBeenCalledTimes(1);
    });

    it('should retry on error message containing retryable code', async () => {
      const mockFn = jest.fn()
        .mockRejectedValueOnce(new Error('Connection failed: ECONNRESET'))
        .mockResolvedValue('success');

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(200);

      const result = await promise;
      expect(result).toBe('success');
    });
  });

  describe('Configuration Updates', () => {
    beforeEach(() => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 3,
        initialDelay: 1000,
      });
    });

    it('should update configuration', () => {
      retryPolicy.updateConfig({
        maxAttempts: 5,
        initialDelay: 500,
      });

      const config = retryPolicy.getConfig();
      expect(config.maxAttempts).toBe(5);
      expect(config.initialDelay).toBe(500);
    });

    it('should merge partial updates', () => {
      retryPolicy.updateConfig({
        maxAttempts: 10,
      });

      const config = retryPolicy.getConfig();
      expect(config.maxAttempts).toBe(10);
      expect(config.initialDelay).toBe(1000); // Unchanged
    });

    it('should apply updated config to next execution', async () => {
      const mockFn = jest.fn().mockRejectedValue(new Error('ECONNRESET'));

      // First execution with 3 max attempts
      const promise1 = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(10000);
      await expect(promise1).rejects.toThrow();
      expect(mockFn).toHaveBeenCalledTimes(3);

      // Update config
      mockFn.mockClear();
      retryPolicy.updateConfig({ maxAttempts: 1 });

      // Second execution with 1 max attempt
      const promise2 = retryPolicy.execute(mockFn);
      await expect(promise2).rejects.toThrow();
      expect(mockFn).toHaveBeenCalledTimes(1);
    });

    it('should return config clone to prevent external modification', () => {
      const config = retryPolicy.getConfig();
      config.maxAttempts = 999;

      const currentConfig = retryPolicy.getConfig();
      expect(currentConfig.maxAttempts).toBe(3);
    });
  });

  describe('Error Handling Edge Cases', () => {
    beforeEach(() => {
      retryPolicy = new RetryPolicy();
    });

    it('should handle non-Error rejections', async () => {
      const mockFn = jest.fn().mockRejectedValue('string error');

      await expect(retryPolicy.execute(mockFn)).rejects.toThrow('string error');
    });

    it('should handle null rejections', async () => {
      const mockFn = jest.fn().mockRejectedValue(null);

      await expect(retryPolicy.execute(mockFn)).rejects.toThrow('null');
    });

    it('should handle undefined rejections', async () => {
      const mockFn = jest.fn().mockRejectedValue(undefined);

      await expect(retryPolicy.execute(mockFn)).rejects.toThrow('undefined');
    });

    it('should handle number rejections', async () => {
      const mockFn = jest.fn().mockRejectedValue(42);

      await expect(retryPolicy.execute(mockFn)).rejects.toThrow('42');
    });

    it('should handle object rejections', async () => {
      const mockFn = jest.fn().mockRejectedValue({ code: 'ERROR', msg: 'failed' });

      await expect(retryPolicy.execute(mockFn)).rejects.toThrow('[object Object]');
    });

    it('should throw error when no error occurs and max attempts reached', async () => {
      // This shouldn't happen in practice but tests edge case
      retryPolicy = new RetryPolicy({ maxAttempts: 0 });

      const mockFn = jest.fn().mockResolvedValue('success');

      await expect(retryPolicy.execute(mockFn)).rejects.toThrow('Max retry attempts reached');
    });
  });

  describe('withRetry Decorator', () => {
    it('should wrap async function with retry logic', async () => {
      const originalFn = jest.fn()
        .mockRejectedValueOnce(new Error('ECONNRESET'))
        .mockResolvedValue('success');

      const wrappedFn = withRetry(originalFn, {
        maxAttempts: 3,
        initialDelay: 100,
      });

      const promise = wrappedFn();
      await jest.advanceTimersByTimeAsync(200);

      const result = await promise;
      expect(result).toBe('success');
      expect(originalFn).toHaveBeenCalledTimes(2);
    });

    it('should preserve function arguments', async () => {
      const originalFn = jest.fn().mockResolvedValue('result');

      const wrappedFn = withRetry(originalFn);

      await wrappedFn('arg1', 'arg2', 'arg3');

      expect(originalFn).toHaveBeenCalledWith('arg1', 'arg2', 'arg3');
    });

    it('should handle multiple invocations', async () => {
      const originalFn = jest.fn().mockResolvedValue('result');

      const wrappedFn = withRetry(originalFn);

      await wrappedFn();
      await wrappedFn();

      expect(originalFn).toHaveBeenCalledTimes(2);
    });

    it('should use default config when none provided', async () => {
      const originalFn = jest.fn().mockResolvedValue('success');

      const wrappedFn = withRetry(originalFn);

      await wrappedFn();

      expect(originalFn).toHaveBeenCalledTimes(1);
    });
  });

  describe('RetryPresets', () => {
    it('should provide quick preset', () => {
      expect(RetryPresets.quick).toEqual({
        maxAttempts: 3,
        initialDelay: 100,
        maxDelay: 1000,
        backoffMultiplier: 2,
      });
    });

    it('should provide standard preset', () => {
      expect(RetryPresets.standard).toEqual({
        maxAttempts: 3,
        initialDelay: 1000,
        maxDelay: 10000,
        backoffMultiplier: 2,
      });
    });

    it('should provide aggressive preset', () => {
      expect(RetryPresets.aggressive).toEqual({
        maxAttempts: 5,
        initialDelay: 500,
        maxDelay: 30000,
        backoffMultiplier: 2,
      });
    });

    it('should provide patient preset', () => {
      expect(RetryPresets.patient).toEqual({
        maxAttempts: 10,
        initialDelay: 2000,
        maxDelay: 60000,
        backoffMultiplier: 1.5,
      });
    });

    it('should work with RetryPolicy constructor', async () => {
      retryPolicy = new RetryPolicy(RetryPresets.quick);

      const mockFn = jest.fn()
        .mockRejectedValueOnce(new Error('ECONNRESET'))
        .mockResolvedValue('success');

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(500);

      const result = await promise;
      expect(result).toBe('success');
    });
  });

  describe('Concurrent Executions', () => {
    beforeEach(() => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 2,
        initialDelay: 100,
      });
    });

    it('should handle concurrent executions independently', async () => {
      const mockFn1 = jest.fn().mockResolvedValue('result1');
      const mockFn2 = jest.fn().mockResolvedValue('result2');

      const [result1, result2] = await Promise.all([
        retryPolicy.execute(mockFn1),
        retryPolicy.execute(mockFn2),
      ]);

      expect(result1).toBe('result1');
      expect(result2).toBe('result2');
    });

    it('should handle concurrent failures and retries', async () => {
      const mockFn1 = jest.fn()
        .mockRejectedValueOnce(new Error('ECONNRESET'))
        .mockResolvedValue('result1');

      const mockFn2 = jest.fn()
        .mockRejectedValueOnce(new Error('ECONNRESET'))
        .mockResolvedValue('result2');

      const promise1 = retryPolicy.execute(mockFn1);
      const promise2 = retryPolicy.execute(mockFn2);

      await jest.advanceTimersByTimeAsync(200);

      const [result1, result2] = await Promise.all([promise1, promise2]);

      expect(result1).toBe('result1');
      expect(result2).toBe('result2');
    });
  });

  describe('Performance & Edge Cases', () => {
    it('should handle maxAttempts of 1 (no retries)', async () => {
      retryPolicy = new RetryPolicy({ maxAttempts: 1 });

      const mockFn = jest.fn().mockRejectedValue(new Error('fail'));

      await expect(retryPolicy.execute(mockFn)).rejects.toThrow('fail');
      expect(mockFn).toHaveBeenCalledTimes(1);
    });

    it('should handle very high maxAttempts', async () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 100,
        initialDelay: 1,
        jitter: false,
      });

      let callCount = 0;
      const mockFn = jest.fn().mockImplementation(() => {
        callCount++;
        if (callCount < 50) {
          return Promise.reject(new Error('ECONNRESET'));
        }
        return Promise.resolve('finally');
      });

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(10000);

      const result = await promise;
      expect(result).toBe('finally');
      expect(callCount).toBe(50);
    });

    it('should handle zero initial delay', async () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 3,
        initialDelay: 0,
        jitter: false,
      });

      const mockFn = jest.fn()
        .mockRejectedValueOnce(new Error('ECONNRESET'))
        .mockResolvedValue('success');

      const result = await retryPolicy.execute(mockFn);
      expect(result).toBe('success');
    });

    it('should handle very large delays', async () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 2,
        initialDelay: 1000000,
        jitter: false,
      });

      const mockFn = jest.fn()
        .mockRejectedValueOnce(new Error('ECONNRESET'))
        .mockResolvedValue('success');

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(1000001);

      const result = await promise;
      expect(result).toBe('success');
    });

    it('should handle backoff multiplier of 1 (constant delay)', async () => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 3,
        initialDelay: 100,
        backoffMultiplier: 1,
        jitter: false,
      });

      const retryHandler = jest.fn();
      retryPolicy.on('retry', retryHandler);

      const mockFn = jest.fn().mockRejectedValue(new Error('ECONNRESET'));

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(500);

      const delays = retryHandler.mock.calls.map(call => call[0].delay);
      expect(delays[0]).toBe(100);
      expect(delays[1]).toBe(100);

      await expect(promise).rejects.toThrow();
    });
  });

  describe('Event Emission Completeness', () => {
    beforeEach(() => {
      retryPolicy = new RetryPolicy({
        maxAttempts: 3,
        initialDelay: 100,
      });
    });

    it('should emit all events in correct order for successful retry', async () => {
      const events: string[] = [];

      retryPolicy.on('attempt', () => events.push('attempt'));
      retryPolicy.on('error', () => events.push('error'));
      retryPolicy.on('retry', () => events.push('retry'));
      retryPolicy.on('success', () => events.push('success'));

      const mockFn = jest.fn()
        .mockRejectedValueOnce(new Error('ECONNRESET'))
        .mockResolvedValue('success');

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(200);

      await promise;

      expect(events).toEqual([
        'attempt',  // First attempt
        'error',    // First error
        'retry',    // Schedule retry
        'attempt',  // Second attempt
        'success',  // Success
      ]);
    });

    it('should emit all events in correct order for exhausted retries', async () => {
      const events: string[] = [];

      retryPolicy.on('attempt', () => events.push('attempt'));
      retryPolicy.on('error', () => events.push('error'));
      retryPolicy.on('retry', () => events.push('retry'));
      retryPolicy.on('exhausted', () => events.push('exhausted'));

      const mockFn = jest.fn().mockRejectedValue(new Error('ECONNRESET'));

      const promise = retryPolicy.execute(mockFn);
      await jest.advanceTimersByTimeAsync(1000);

      await expect(promise).rejects.toThrow();

      expect(events).toEqual([
        'attempt',
        'error',
        'retry',
        'attempt',
        'error',
        'retry',
        'attempt',
        'error',
        'exhausted',
      ]);
    });
  });
});
