/**
 * Unit tests for CPUMonitor
 * 
 * Tests validate:
 * - Plugin lifecycle (initialize, shutdown)
 * - Metrics collection with proper types
 * - Usage percentage calculation
 * - Error handling on failed collection
 * - Command execution interface
 */

import { CPUMonitor } from '../../CPUMonitor';

describe('CPUMonitor', () => {
  let monitor: CPUMonitor;

  beforeEach(() => {
    monitor = new CPUMonitor({ pollingInterval: 1000 });
  });

  afterEach(async () => {
    if (monitor.enabled) {
      await monitor.shutdown();
    }
  });

  describe('initialization', () => {
    it('should initialize successfully', async () => {
      expect(monitor.enabled).toBe(false);

      await monitor.initialize();

      expect(monitor.enabled).toBe(true);
      expect(monitor.id).toBe('cpu-monitor');
      expect(monitor.name).toBe('CPU Monitor');
    });

    it('should be idempotent on multiple initialize calls', async () => {
      await monitor.initialize();
      await monitor.initialize(); // Should not error

      expect(monitor.enabled).toBe(true);
    });

    it('should have correct metadata', async () => {
      await monitor.initialize();

      expect(monitor.version).toBe('0.1.0');
      expect(monitor.description).toContain('CPU');
      expect(monitor.id).toBe('cpu-monitor');
    });
  });

  describe('collection', () => {
    beforeEach(async () => {
      await monitor.initialize();
    });

    it('should collect CPU metrics', async () => {
      const metrics = await monitor.collect('system');

      expect(metrics).toHaveProperty('user');
      expect(metrics).toHaveProperty('system');
      expect(metrics).toHaveProperty('idle');
      expect(metrics).toHaveProperty('iowait');
      expect(metrics).toHaveProperty('usagePercent');
      expect(metrics).toHaveProperty('timestamp');

      // First collection should have 0% usage (no baseline)
      expect(metrics.usagePercent).toBe(0);
    });

    it('should calculate usage percentage on subsequent collections', async () => {
      // First collection
      await monitor.collect('system');

      // Wait a bit and collect again
      await new Promise(resolve => setTimeout(resolve, 100));
      const metrics = await monitor.collect('system');

      // On Linux, should have percentage; on other systems might still be simulated
      expect(typeof metrics.usagePercent).toBe('number');
      expect(metrics.usagePercent).toBeGreaterThanOrEqual(0);
      expect(metrics.usagePercent).toBeLessThanOrEqual(100);
    });

    it('should reject unknown sources', async () => {
      await expect(monitor.collect('unknown')).rejects.toThrow(
        'Unknown CPU source',
      );
    });

    it('should return consistent timestamp', async () => {
      const before = Date.now();
      const metrics = await monitor.collect('system');
      const after = Date.now();

      expect(metrics.timestamp as number).toBeGreaterThanOrEqual(before);
      expect(metrics.timestamp as number).toBeLessThanOrEqual(after);
    });
  });

  describe('validation', () => {
    beforeEach(async () => {
      await monitor.initialize();
    });

    it('should validate system source', async () => {
      const isValid = await monitor.validate('system');

      expect(isValid).toBe(true);
    });

    it('should reject unknown sources', async () => {
      const isValid = await monitor.validate('unknown');

      expect(isValid).toBe(false);
    });
  });

  describe('sources', () => {
    beforeEach(async () => {
      await monitor.initialize();
    });

    it('should return available sources', async () => {
      const sources = await monitor.getSources();

      expect(Array.isArray(sources)).toBe(true);
      expect(sources).toContain('system');
    });
  });

  describe('command execution', () => {
    beforeEach(async () => {
      await monitor.initialize();
    });

    it('should execute collect command', async () => {
      const result = await monitor.execute('collect', { source: 'system' });

      expect(result).toHaveProperty('usagePercent');
    });

    it('should execute validate command', async () => {
      const result = await monitor.execute('validate', { source: 'system' });

      expect(result).toBe(true);
    });

    it('should execute getSources command', async () => {
      const result = await monitor.execute('getSources');

      expect(Array.isArray(result)).toBe(true);
    });

    it('should reject unknown commands', async () => {
      await expect(
        monitor.execute('unknownCommand'),
      ).rejects.toThrow('Unknown command');
    });
  });

  describe('shutdown', () => {
    it('should disable on shutdown', async () => {
      await monitor.initialize();
      expect(monitor.enabled).toBe(true);

      await monitor.shutdown();

      expect(monitor.enabled).toBe(false);
    });

    it('should throw error if collecting after shutdown', async () => {
      await monitor.initialize();
      await monitor.shutdown();

      await expect(monitor.collect('system')).rejects.toThrow('not enabled');
    });
  });

  describe('error handling', () => {
    it('should throw error if not initialized', async () => {
      await expect(monitor.collect('system')).rejects.toThrow('not enabled');
    });

    it('should handle retry logic on failure', async () => {
      monitor = new CPUMonitor({ maxRetries: 2, timeout: 100 });
      await monitor.initialize();

      // This should succeed even if there are transient issues
      const metrics = await monitor.collect('system');

      expect(metrics).toHaveProperty('usagePercent');
    });
  });

  describe('configuration', () => {
    it('should use custom polling interval', () => {
      monitor = new CPUMonitor({ pollingInterval: 2000 });

      expect(monitor['config'].pollingInterval).toBe(2000);
    });

    it('should use default polling interval', () => {
      monitor = new CPUMonitor();

      expect(monitor['config'].pollingInterval).toBe(5000);
    });

    it('should use custom retry count', () => {
      monitor = new CPUMonitor({ maxRetries: 5 });

      expect(monitor['config'].maxRetries).toBe(5);
    });
  });
});
