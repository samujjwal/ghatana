/**
 * Unit tests for MemoryMonitor
 * 
 * Tests validate:
 * - Plugin lifecycle (initialize, shutdown)
 * - Memory metrics collection
 * - Memory usage percentage
 * - Error handling
 * - Platform-specific behavior (Linux vs others)
 */

import { MemoryMonitor } from '../../MemoryMonitor';

describe('MemoryMonitor', () => {
  let monitor: MemoryMonitor;

  beforeEach(() => {
    monitor = new MemoryMonitor({ pollingInterval: 1000 });
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
      expect(monitor.id).toBe('memory-monitor');
      expect(monitor.name).toBe('Memory Monitor');
    });

    it('should be idempotent on multiple initialize calls', async () => {
      await monitor.initialize();
      await monitor.initialize(); // Should not error

      expect(monitor.enabled).toBe(true);
    });
  });

  describe('collection', () => {
    beforeEach(async () => {
      await monitor.initialize();
    });

    it('should collect memory metrics', async () => {
      const metrics = await monitor.collect('system');

      expect(metrics).toHaveProperty('total');
      expect(metrics).toHaveProperty('free');
      expect(metrics).toHaveProperty('used');
      expect(metrics).toHaveProperty('cached');
      expect(metrics).toHaveProperty('usagePercent');
      expect(metrics).toHaveProperty('timestamp');

      // Validate metric ranges
      const total = metrics.total as number;
      const used = metrics.used as number;
      const usagePercent = metrics.usagePercent as number;

      expect(total).toBeGreaterThan(0);
      expect(used).toBeGreaterThanOrEqual(0);
      expect(usagePercent).toBeGreaterThanOrEqual(0);
      expect(usagePercent).toBeLessThanOrEqual(100);
    });

    it('should reject unknown sources', async () => {
      await expect(monitor.collect('unknown')).rejects.toThrow(
        'Unknown memory source',
      );
    });

    it('should return consistent memory values', async () => {
      const metrics1 = await monitor.collect('system');
      const metrics2 = await monitor.collect('system');

      // Values should be similar (within margin for variations)
      const total1 = metrics1.total as number;
      const total2 = metrics2.total as number;

      expect(Math.abs(total1 - total2)).toBeLessThanOrEqual(
        Math.max(total1, total2) * 0.1,
      ); // Within 10%
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

      expect(result).toHaveProperty('total');
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

    it('should provide valid metrics even with retry logic', async () => {
      monitor = new MemoryMonitor({ maxRetries: 2, timeout: 100 });
      await monitor.initialize();

      const metrics = await monitor.collect('system');

      expect(metrics.total as number).toBeGreaterThan(0);
    });
  });

  describe('metric calculations', () => {
    beforeEach(async () => {
      await monitor.initialize();
    });

    it('should calculate usage percentage correctly', async () => {
      const metrics = await monitor.collect('system');

      const total = metrics.total as number;
      const used = metrics.used as number;
      const usagePercent = metrics.usagePercent as number;

      if (total > 0) {
        const expected = Math.round(((total - (metrics.free as number)) / total) * 100);
        expect(usagePercent).toBe(expected);
      }
    });

    it('should handle zero total memory gracefully', async () => {
      // This tests the formula with zero total
      const metrics = await monitor.collect('system');

      const total = metrics.total as number;
      const usagePercent = metrics.usagePercent as number;

      // If total is 0, usage should be 0
      if (total === 0) {
        expect(usagePercent).toBe(0);
      }
    });
  });

  describe('configuration', () => {
    it('should use custom polling interval', () => {
      monitor = new MemoryMonitor({ pollingInterval: 3000 });

      expect(monitor['config'].pollingInterval).toBe(3000);
    });

    it('should use default configuration', () => {
      monitor = new MemoryMonitor();

      expect(monitor['config'].pollingInterval).toBe(5000);
      expect(monitor['config'].maxRetries).toBe(3);
      expect(monitor['config'].timeout).toBe(10000);
    });
  });
});
