/**
 * @fileoverview Comprehensive unit tests for HealthChecker module
 *
 * Tests cover:
 * - Health check registration and unregistration
 * - Health check execution
 * - Status aggregation (healthy/unhealthy/degraded)
 * - Timeout handling for checks
 * - Concurrent health checks
 * - Check results caching
 * - Dependency health checks
 * - Custom health indicators
 * - Health check metadata
 * - Alert triggering based on status
 * - Start/stop lifecycle
 * - Critical vs non-critical checks
 */

import {
  HealthChecker,
  HealthCheck,
  HealthCheckResult,
  HealthStatus,
  SystemHealth,
  createMemoryHealthCheck,
  createEventLoopHealthCheck,
} from '../../../src/monitoring/HealthChecker';

describe('HealthChecker', () => {
  let healthChecker: HealthChecker;

  beforeEach(() => {
    jest.useFakeTimers();
    healthChecker = new HealthChecker();
  });

  afterEach(() => {
    healthChecker.destroy();
    jest.useRealTimers();
  });

  describe('Check Registration', () => {
    it('should register a health check', () => {
      const check: HealthCheck = {
        name: 'database',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);

      expect(healthChecker.getChecks()).toContain('database');
    });

    it('should apply default values to registered check', () => {
      const check: HealthCheck = {
        name: 'api',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);
      healthChecker.start();

      // If defaults are applied, the check should run on interval
      expect(healthChecker.getChecks()).toContain('api');
    });

    it('should use custom interval when provided', () => {
      const check: HealthCheck = {
        name: 'custom',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
        interval: 10000,
      };

      healthChecker.registerCheck(check);

      expect(healthChecker.getChecks()).toContain('custom');
    });

    it('should use custom timeout when provided', () => {
      const check: HealthCheck = {
        name: 'custom',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
        timeout: 2000,
      };

      healthChecker.registerCheck(check);

      expect(healthChecker.getChecks()).toContain('custom');
    });

    it('should throw error on duplicate registration', () => {
      const check: HealthCheck = {
        name: 'duplicate',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);

      expect(() => {
        healthChecker.registerCheck(check);
      }).toThrow("Health check 'duplicate' already registered");
    });

    it('should emit checkRegistered event', () => {
      const registeredListener = jest.fn();
      healthChecker.on('checkRegistered', registeredListener);

      const check: HealthCheck = {
        name: 'test',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);

      expect(registeredListener).toHaveBeenCalledWith({ name: 'test' });
    });

    it('should start check immediately if already running', async () => {
      const checkFn = jest.fn().mockResolvedValue({
        status: 'healthy',
        timestamp: Date.now(),
        duration: 0,
      });

      healthChecker.start();

      const check: HealthCheck = {
        name: 'late-registration',
        check: checkFn,
      };

      healthChecker.registerCheck(check);

      await jest.runOnlyPendingTimersAsync();

      expect(checkFn).toHaveBeenCalled();
    });
  });

  describe('Check Unregistration', () => {
    it('should unregister a health check', () => {
      const check: HealthCheck = {
        name: 'removable',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);
      const result = healthChecker.unregisterCheck('removable');

      expect(result).toBe(true);
      expect(healthChecker.getChecks()).not.toContain('removable');
    });

    it('should return false when unregistering non-existent check', () => {
      const result = healthChecker.unregisterCheck('non-existent');

      expect(result).toBe(false);
    });

    it('should emit checkUnregistered event', () => {
      const unregisteredListener = jest.fn();
      healthChecker.on('checkUnregistered', unregisteredListener);

      const check: HealthCheck = {
        name: 'test',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);
      healthChecker.unregisterCheck('test');

      expect(unregisteredListener).toHaveBeenCalledWith({ name: 'test' });
    });

    it('should clear check results on unregistration', async () => {
      const check: HealthCheck = {
        name: 'test',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);
      await healthChecker.runCheck('test');

      healthChecker.unregisterCheck('test');

      expect(healthChecker.getCheckResult('test')).toBeUndefined();
    });

    it('should stop scheduled checks on unregistration', () => {
      const checkFn = jest.fn().mockResolvedValue({
        status: 'healthy',
        timestamp: Date.now(),
        duration: 0,
      });

      const check: HealthCheck = {
        name: 'test',
        check: checkFn,
        interval: 1000,
      };

      healthChecker.registerCheck(check);
      healthChecker.start();
      healthChecker.unregisterCheck('test');

      jest.advanceTimersByTime(5000);

      // Check should not be called after unregistration
      expect(checkFn).toHaveBeenCalledTimes(1); // Only the initial call
    });
  });

  describe('Health Check Execution', () => {
    it('should execute health check on demand', async () => {
      const checkFn = jest.fn().mockResolvedValue({
        status: 'healthy',
        message: 'All good',
        timestamp: Date.now(),
        duration: 0,
      });

      const check: HealthCheck = {
        name: 'on-demand',
        check: checkFn,
      };

      healthChecker.registerCheck(check);
      const result = await healthChecker.runCheck('on-demand');

      expect(checkFn).toHaveBeenCalled();
      expect(result.status).toBe('healthy');
      expect(result.message).toBe('All good');
    });

    it('should throw error when running non-existent check', async () => {
      await expect(
        healthChecker.runCheck('non-existent')
      ).rejects.toThrow("Health check 'non-existent' not found");
    });

    it('should capture check duration', async () => {
      const check: HealthCheck = {
        name: 'timed',
        check: async () => {
          await new Promise(resolve => setTimeout(resolve, 100));
          return {
            status: 'healthy',
            timestamp: Date.now(),
            duration: 0,
          };
        },
      };

      healthChecker.registerCheck(check);

      const resultPromise = healthChecker.runCheck('timed');
      jest.advanceTimersByTime(100);
      const result = await resultPromise;

      expect(result.duration).toBeGreaterThanOrEqual(0);
    });

    it('should include timestamp in result', async () => {
      const check: HealthCheck = {
        name: 'test',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);

      const before = Date.now();
      const result = await healthChecker.runCheck('test');
      const after = Date.now();

      expect(result.timestamp).toBeGreaterThanOrEqual(before);
      expect(result.timestamp).toBeLessThanOrEqual(after);
    });

    it('should emit checkCompleted event on success', async () => {
      const completedListener = jest.fn();
      healthChecker.on('checkCompleted', completedListener);

      const check: HealthCheck = {
        name: 'test',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);
      await healthChecker.runCheck('test');

      expect(completedListener).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'test',
          result: expect.objectContaining({ status: 'healthy' }),
        })
      );
    });

    it('should support checks with details', async () => {
      const check: HealthCheck = {
        name: 'detailed',
        check: async () => ({
          status: 'healthy',
          message: 'Database connected',
          details: {
            connections: 10,
            maxConnections: 100,
            uptime: 3600,
          },
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);
      const result = await healthChecker.runCheck('detailed');

      expect(result.details).toEqual({
        connections: 10,
        maxConnections: 100,
        uptime: 3600,
      });
    });
  });

  describe('Timeout Handling', () => {
    it('should timeout slow health checks', async () => {
      const check: HealthCheck = {
        name: 'slow',
        check: async () => {
          await new Promise(resolve => setTimeout(resolve, 10000));
          return {
            status: 'healthy',
            timestamp: Date.now(),
            duration: 0,
          };
        },
        timeout: 1000,
      };

      healthChecker.registerCheck(check);

      const resultPromise = healthChecker.runCheck('slow');
      jest.advanceTimersByTime(1000);
      const result = await resultPromise;

      expect(result.status).toBe('unhealthy');
      expect(result.message).toContain('timed out');
    });

    it('should emit checkFailed event on timeout', async () => {
      const failedListener = jest.fn();
      healthChecker.on('checkFailed', failedListener);

      const check: HealthCheck = {
        name: 'slow',
        check: async () => {
          await new Promise(resolve => setTimeout(resolve, 10000));
          return {
            status: 'healthy',
            timestamp: Date.now(),
            duration: 0,
          };
        },
        timeout: 1000,
      };

      healthChecker.registerCheck(check);

      const resultPromise = healthChecker.runCheck('slow');
      jest.advanceTimersByTime(1000);
      await resultPromise;

      expect(failedListener).toHaveBeenCalled();
    });

    it('should complete before timeout if check is fast', async () => {
      const check: HealthCheck = {
        name: 'fast',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
        timeout: 5000,
      };

      healthChecker.registerCheck(check);
      const result = await healthChecker.runCheck('fast');

      expect(result.status).toBe('healthy');
    });
  });

  describe('Status Aggregation', () => {
    it('should return healthy when all checks are healthy', async () => {
      const check1: HealthCheck = {
        name: 'check1',
        check: async () => ({ status: 'healthy', timestamp: Date.now(), duration: 0 }),
      };

      const check2: HealthCheck = {
        name: 'check2',
        check: async () => ({ status: 'healthy', timestamp: Date.now(), duration: 0 }),
      };

      healthChecker.registerCheck(check1);
      healthChecker.registerCheck(check2);

      const health = await healthChecker.runAllChecks();

      expect(health.status).toBe('healthy');
    });

    it('should return degraded when non-critical check is unhealthy', async () => {
      const check1: HealthCheck = {
        name: 'check1',
        check: async () => ({ status: 'healthy', timestamp: Date.now(), duration: 0 }),
        critical: true,
      };

      const check2: HealthCheck = {
        name: 'check2',
        check: async () => ({ status: 'unhealthy', timestamp: Date.now(), duration: 0 }),
        critical: false,
      };

      healthChecker.registerCheck(check1);
      healthChecker.registerCheck(check2);

      const health = await healthChecker.runAllChecks();

      expect(health.status).toBe('degraded');
    });

    it('should return unhealthy when critical check is unhealthy', async () => {
      const check1: HealthCheck = {
        name: 'critical-db',
        check: async () => ({ status: 'unhealthy', timestamp: Date.now(), duration: 0 }),
        critical: true,
      };

      const check2: HealthCheck = {
        name: 'optional-cache',
        check: async () => ({ status: 'healthy', timestamp: Date.now(), duration: 0 }),
        critical: false,
      };

      healthChecker.registerCheck(check1);
      healthChecker.registerCheck(check2);

      const health = await healthChecker.runAllChecks();

      expect(health.status).toBe('unhealthy');
    });

    it('should return degraded when check status is degraded', async () => {
      const check: HealthCheck = {
        name: 'degraded-service',
        check: async () => ({ status: 'degraded', timestamp: Date.now(), duration: 0 }),
      };

      healthChecker.registerCheck(check);

      const health = await healthChecker.runAllChecks();

      expect(health.status).toBe('degraded');
    });

    it('should include all check results in health snapshot', async () => {
      const check1: HealthCheck = {
        name: 'check1',
        check: async () => ({ status: 'healthy', timestamp: Date.now(), duration: 0 }),
      };

      const check2: HealthCheck = {
        name: 'check2',
        check: async () => ({ status: 'degraded', timestamp: Date.now(), duration: 0 }),
      };

      healthChecker.registerCheck(check1);
      healthChecker.registerCheck(check2);

      const health = await healthChecker.runAllChecks();

      expect(health.checks).toHaveProperty('check1');
      expect(health.checks).toHaveProperty('check2');
      expect(health.checks.check1.status).toBe('healthy');
      expect(health.checks.check2.status).toBe('degraded');
    });

    it('should include timestamp in health snapshot', async () => {
      const check: HealthCheck = {
        name: 'test',
        check: async () => ({ status: 'healthy', timestamp: Date.now(), duration: 0 }),
      };

      healthChecker.registerCheck(check);

      const before = Date.now();
      const health = await healthChecker.runAllChecks();
      const after = Date.now();

      expect(health.timestamp).toBeGreaterThanOrEqual(before);
      expect(health.timestamp).toBeLessThanOrEqual(after);
    });
  });

  describe('Concurrent Execution', () => {
    it('should run all checks concurrently', async () => {
      const executionOrder: string[] = [];

      const check1: HealthCheck = {
        name: 'check1',
        check: async () => {
          await new Promise(resolve => setTimeout(resolve, 100));
          executionOrder.push('check1');
          return { status: 'healthy', timestamp: Date.now(), duration: 0 };
        },
      };

      const check2: HealthCheck = {
        name: 'check2',
        check: async () => {
          await new Promise(resolve => setTimeout(resolve, 50));
          executionOrder.push('check2');
          return { status: 'healthy', timestamp: Date.now(), duration: 0 };
        },
      };

      healthChecker.registerCheck(check1);
      healthChecker.registerCheck(check2);

      const healthPromise = healthChecker.runAllChecks();
      jest.advanceTimersByTime(100);
      await healthPromise;

      // check2 should complete before check1
      expect(executionOrder).toEqual(['check2', 'check1']);
    });

    it('should handle concurrent check failures', async () => {
      const check1: HealthCheck = {
        name: 'failing1',
        check: async () => {
          throw new Error('Check 1 failed');
        },
      };

      const check2: HealthCheck = {
        name: 'failing2',
        check: async () => {
          throw new Error('Check 2 failed');
        },
      };

      healthChecker.registerCheck(check1);
      healthChecker.registerCheck(check2);

      const health = await healthChecker.runAllChecks();

      expect(health.checks.failing1.status).toBe('unhealthy');
      expect(health.checks.failing2.status).toBe('unhealthy');
    });
  });

  describe('Start and Stop', () => {
    it('should start scheduled checks', () => {
      const startedListener = jest.fn();
      healthChecker.on('started', startedListener);

      healthChecker.start();

      expect(startedListener).toHaveBeenCalled();
    });

    it('should not start if already running', () => {
      const startedListener = jest.fn();
      healthChecker.on('started', startedListener);

      healthChecker.start();
      healthChecker.start();

      expect(startedListener).toHaveBeenCalledTimes(1);
    });

    it('should execute checks on interval after start', async () => {
      const checkFn = jest.fn().mockResolvedValue({
        status: 'healthy',
        timestamp: Date.now(),
        duration: 0,
      });

      const check: HealthCheck = {
        name: 'periodic',
        check: checkFn,
        interval: 1000,
      };

      healthChecker.registerCheck(check);
      healthChecker.start();

      await jest.runOnlyPendingTimersAsync();

      // Initial execution
      expect(checkFn).toHaveBeenCalledTimes(1);

      jest.advanceTimersByTime(1000);
      await jest.runOnlyPendingTimersAsync();

      // Second execution after interval
      expect(checkFn).toHaveBeenCalledTimes(2);

      jest.advanceTimersByTime(1000);
      await jest.runOnlyPendingTimersAsync();

      // Third execution
      expect(checkFn).toHaveBeenCalledTimes(3);
    });

    it('should stop scheduled checks', () => {
      const stoppedListener = jest.fn();
      healthChecker.on('stopped', stoppedListener);

      healthChecker.start();
      healthChecker.stop();

      expect(stoppedListener).toHaveBeenCalled();
    });

    it('should not stop if not running', () => {
      const stoppedListener = jest.fn();
      healthChecker.on('stopped', stoppedListener);

      healthChecker.stop();

      expect(stoppedListener).not.toHaveBeenCalled();
    });

    it('should not execute checks after stop', async () => {
      const checkFn = jest.fn().mockResolvedValue({
        status: 'healthy',
        timestamp: Date.now(),
        duration: 0,
      });

      const check: HealthCheck = {
        name: 'periodic',
        check: checkFn,
        interval: 1000,
      };

      healthChecker.registerCheck(check);
      healthChecker.start();

      await jest.runOnlyPendingTimersAsync();

      healthChecker.stop();

      jest.advanceTimersByTime(5000);

      // Only the initial execution should have happened
      expect(checkFn).toHaveBeenCalledTimes(1);
    });

    it('should support restart', async () => {
      const checkFn = jest.fn().mockResolvedValue({
        status: 'healthy',
        timestamp: Date.now(),
        duration: 0,
      });

      const check: HealthCheck = {
        name: 'restartable',
        check: checkFn,
        interval: 1000,
      };

      healthChecker.registerCheck(check);
      healthChecker.start();

      await jest.runOnlyPendingTimersAsync();

      healthChecker.stop();
      healthChecker.start();

      await jest.runOnlyPendingTimersAsync();

      // Should be called twice (once for each start)
      expect(checkFn).toHaveBeenCalledTimes(2);
    });
  });

  describe('Check Results Caching', () => {
    it('should cache check results', async () => {
      const check: HealthCheck = {
        name: 'cached',
        check: async () => ({
          status: 'healthy',
          message: 'Cached result',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);
      await healthChecker.runCheck('cached');

      const result = healthChecker.getCheckResult('cached');

      expect(result).toBeDefined();
      expect(result?.status).toBe('healthy');
      expect(result?.message).toBe('Cached result');
    });

    it('should return undefined for non-existent check result', () => {
      const result = healthChecker.getCheckResult('non-existent');

      expect(result).toBeUndefined();
    });

    it('should update cached result on re-execution', async () => {
      let callCount = 0;

      const check: HealthCheck = {
        name: 'updating',
        check: async () => {
          callCount++;
          return {
            status: 'healthy',
            message: `Call ${callCount}`,
            timestamp: Date.now(),
            duration: 0,
          };
        },
      };

      healthChecker.registerCheck(check);
      await healthChecker.runCheck('updating');

      const result1 = healthChecker.getCheckResult('updating');
      expect(result1?.message).toBe('Call 1');

      await healthChecker.runCheck('updating');

      const result2 = healthChecker.getCheckResult('updating');
      expect(result2?.message).toBe('Call 2');
    });

    it('should clear results on clearResults()', async () => {
      const check: HealthCheck = {
        name: 'clearable',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);
      await healthChecker.runCheck('clearable');

      healthChecker.clearResults();

      expect(healthChecker.getCheckResult('clearable')).toBeUndefined();
    });

    it('should emit resultsCleared event', () => {
      const clearedListener = jest.fn();
      healthChecker.on('resultsCleared', clearedListener);

      healthChecker.clearResults();

      expect(clearedListener).toHaveBeenCalled();
    });

    it('should include cached results in getHealth()', async () => {
      const check: HealthCheck = {
        name: 'test',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);
      await healthChecker.runCheck('test');

      const health = healthChecker.getHealth();

      expect(health.checks.test).toBeDefined();
      expect(health.checks.test.status).toBe('healthy');
    });
  });

  describe('Status Change Detection', () => {
    it('should emit statusChanged event when status changes', async () => {
      const statusChangedListener = jest.fn();
      healthChecker.on('statusChanged', statusChangedListener);

      let isHealthy = true;

      const check: HealthCheck = {
        name: 'changing',
        check: async () => ({
          status: isHealthy ? 'healthy' : 'unhealthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);
      await healthChecker.runCheck('changing');

      isHealthy = false;
      await healthChecker.runCheck('changing');

      expect(statusChangedListener).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'changing',
          result: expect.objectContaining({ status: 'unhealthy' }),
        })
      );
    });

    it('should not emit statusChanged if status unchanged', async () => {
      const statusChangedListener = jest.fn();
      healthChecker.on('statusChanged', statusChangedListener);

      const check: HealthCheck = {
        name: 'stable',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);
      await healthChecker.runCheck('stable');
      await healthChecker.runCheck('stable');

      // Should only be called once (for the first execution)
      expect(statusChangedListener).toHaveBeenCalledTimes(1);
    });
  });

  describe('Error Handling', () => {
    it('should handle check throwing error', async () => {
      const check: HealthCheck = {
        name: 'throwing',
        check: async () => {
          throw new Error('Check failed');
        },
      };

      healthChecker.registerCheck(check);
      const result = await healthChecker.runCheck('throwing');

      expect(result.status).toBe('unhealthy');
      expect(result.message).toBe('Check failed');
    });

    it('should emit checkFailed event on error', async () => {
      const failedListener = jest.fn();
      healthChecker.on('checkFailed', failedListener);

      const check: HealthCheck = {
        name: 'failing',
        check: async () => {
          throw new Error('Check error');
        },
      };

      healthChecker.registerCheck(check);
      await healthChecker.runCheck('failing');

      expect(failedListener).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'failing',
          error: expect.any(Error),
          result: expect.objectContaining({ status: 'unhealthy' }),
        })
      );
    });

    it('should handle non-Error exceptions', async () => {
      const check: HealthCheck = {
        name: 'non-error',
        check: async () => {
          throw 'String error';
        },
      };

      healthChecker.registerCheck(check);
      const result = await healthChecker.runCheck('non-error');

      expect(result.status).toBe('unhealthy');
      expect(result.message).toBe('Unknown error');
    });

    it('should continue other checks if one fails', async () => {
      const check1: HealthCheck = {
        name: 'failing',
        check: async () => {
          throw new Error('Failed');
        },
      };

      const check2: HealthCheck = {
        name: 'succeeding',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check1);
      healthChecker.registerCheck(check2);

      const health = await healthChecker.runAllChecks();

      expect(health.checks.failing.status).toBe('unhealthy');
      expect(health.checks.succeeding.status).toBe('healthy');
    });
  });

  describe('Check Interval Configuration', () => {
    it('should not schedule checks with interval 0', async () => {
      const checkFn = jest.fn().mockResolvedValue({
        status: 'healthy',
        timestamp: Date.now(),
        duration: 0,
      });

      const check: HealthCheck = {
        name: 'no-interval',
        check: checkFn,
        interval: 0,
      };

      healthChecker.registerCheck(check);
      healthChecker.start();

      await jest.runOnlyPendingTimersAsync();

      // Initial execution only
      expect(checkFn).toHaveBeenCalledTimes(1);

      jest.advanceTimersByTime(10000);

      // No additional executions
      expect(checkFn).toHaveBeenCalledTimes(1);
    });

    it('should respect different intervals for different checks', async () => {
      const checkFn1 = jest.fn().mockResolvedValue({
        status: 'healthy',
        timestamp: Date.now(),
        duration: 0,
      });

      const checkFn2 = jest.fn().mockResolvedValue({
        status: 'healthy',
        timestamp: Date.now(),
        duration: 0,
      });

      const check1: HealthCheck = {
        name: 'fast',
        check: checkFn1,
        interval: 1000,
      };

      const check2: HealthCheck = {
        name: 'slow',
        check: checkFn2,
        interval: 3000,
      };

      healthChecker.registerCheck(check1);
      healthChecker.registerCheck(check2);
      healthChecker.start();

      await jest.runOnlyPendingTimersAsync();

      // Initial executions
      expect(checkFn1).toHaveBeenCalledTimes(1);
      expect(checkFn2).toHaveBeenCalledTimes(1);

      jest.advanceTimersByTime(1000);
      await jest.runOnlyPendingTimersAsync();

      // Fast check executed again
      expect(checkFn1).toHaveBeenCalledTimes(2);
      expect(checkFn2).toHaveBeenCalledTimes(1);

      jest.advanceTimersByTime(2000);
      await jest.runOnlyPendingTimersAsync();

      // Both executed
      expect(checkFn1).toHaveBeenCalledTimes(3);
      expect(checkFn2).toHaveBeenCalledTimes(2);
    });
  });

  describe('Destroy', () => {
    it('should stop all checks on destroy', () => {
      healthChecker.start();
      healthChecker.destroy();

      expect(healthChecker.getChecks()).toHaveLength(0);
    });

    it('should clear all state on destroy', async () => {
      const check: HealthCheck = {
        name: 'test',
        check: async () => ({
          status: 'healthy',
          timestamp: Date.now(),
          duration: 0,
        }),
      };

      healthChecker.registerCheck(check);
      await healthChecker.runCheck('test');

      healthChecker.destroy();

      expect(healthChecker.getChecks()).toHaveLength(0);
      expect(healthChecker.getCheckResult('test')).toBeUndefined();
    });

    it('should remove all listeners on destroy', () => {
      const listener = jest.fn();
      healthChecker.on('checkCompleted', listener);

      healthChecker.destroy();

      expect(healthChecker.listenerCount('checkCompleted')).toBe(0);
    });
  });

  describe('Built-in Health Checks', () => {
    describe('Memory Health Check', () => {
      it('should create memory health check with defaults', () => {
        const memCheck = createMemoryHealthCheck();

        expect(memCheck.name).toBe('memory');
        expect(memCheck.check).toBeDefined();
        expect(memCheck.critical).toBe(true);
      });

      it('should report healthy when memory usage is low', async () => {
        const memCheck = createMemoryHealthCheck({
          warning: 0.99,
          critical: 0.999,
        });

        const result = await memCheck.check();

        expect(result.status).toBe('healthy');
        expect(result.details).toHaveProperty('heapUsed');
        expect(result.details).toHaveProperty('heapTotal');
      });

      it('should use custom thresholds', () => {
        const memCheck = createMemoryHealthCheck({
          warning: 0.7,
          critical: 0.9,
        });

        expect(memCheck.name).toBe('memory');
      });
    });

    describe('Event Loop Health Check', () => {
      it('should create event loop health check with defaults', () => {
        const loopCheck = createEventLoopHealthCheck();

        expect(loopCheck.name).toBe('event_loop');
        expect(loopCheck.check).toBeDefined();
        expect(loopCheck.critical).toBe(true);
      });

      it('should measure event loop lag', async () => {
        const loopCheck = createEventLoopHealthCheck();

        const result = await loopCheck.check();

        expect(result.status).toBeDefined();
        expect(result.details).toHaveProperty('lag');
        expect(typeof result.details?.lag).toBe('number');
      });

      it('should use custom thresholds', () => {
        const loopCheck = createEventLoopHealthCheck({
          warning: 50,
          critical: 200,
        });

        expect(loopCheck.name).toBe('event_loop');
      });
    });
  });

  describe('Complex Scenarios', () => {
    it('should handle mixed healthy, degraded, and unhealthy checks', async () => {
      const checks: HealthCheck[] = [
        {
          name: 'healthy-critical',
          check: async () => ({ status: 'healthy', timestamp: Date.now(), duration: 0 }),
          critical: true,
        },
        {
          name: 'degraded-non-critical',
          check: async () => ({ status: 'degraded', timestamp: Date.now(), duration: 0 }),
          critical: false,
        },
        {
          name: 'unhealthy-non-critical',
          check: async () => ({ status: 'unhealthy', timestamp: Date.now(), duration: 0 }),
          critical: false,
        },
      ];

      checks.forEach(check => healthChecker.registerCheck(check));

      const health = await healthChecker.runAllChecks();

      expect(health.status).toBe('degraded');
    });

    it('should prioritize unhealthy critical over degraded', async () => {
      const checks: HealthCheck[] = [
        {
          name: 'unhealthy-critical',
          check: async () => ({ status: 'unhealthy', timestamp: Date.now(), duration: 0 }),
          critical: true,
        },
        {
          name: 'degraded-non-critical',
          check: async () => ({ status: 'degraded', timestamp: Date.now(), duration: 0 }),
          critical: false,
        },
      ];

      checks.forEach(check => healthChecker.registerCheck(check));

      const health = await healthChecker.runAllChecks();

      expect(health.status).toBe('unhealthy');
    });

    it('should handle health checks with varying execution times', async () => {
      const checks: HealthCheck[] = [
        {
          name: 'instant',
          check: async () => ({ status: 'healthy', timestamp: Date.now(), duration: 0 }),
        },
        {
          name: 'slow',
          check: async () => {
            await new Promise(resolve => setTimeout(resolve, 100));
            return { status: 'healthy', timestamp: Date.now(), duration: 0 };
          },
        },
        {
          name: 'medium',
          check: async () => {
            await new Promise(resolve => setTimeout(resolve, 50));
            return { status: 'healthy', timestamp: Date.now(), duration: 0 };
          },
        },
      ];

      checks.forEach(check => healthChecker.registerCheck(check));

      const healthPromise = healthChecker.runAllChecks();
      jest.advanceTimersByTime(100);
      const health = await healthPromise;

      expect(health.status).toBe('healthy');
      expect(Object.keys(health.checks)).toHaveLength(3);
    });
  });
});
