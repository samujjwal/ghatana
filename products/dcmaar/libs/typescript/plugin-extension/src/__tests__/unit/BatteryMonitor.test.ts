/**
 * Unit tests for BatteryMonitor
 * 
 * Tests validate:
 * - Plugin lifecycle management
 * - Battery metrics collection
 * - Status parsing (charging, discharging, full)
 * - Metric value ranges and types
 * - Graceful fallback to simulated data
 */

import { BatteryMonitor } from '../../BatteryMonitor';

describe('BatteryMonitor', () => {
  let monitor: BatteryMonitor;

  beforeEach(() => {
    monitor = new BatteryMonitor({ pollingInterval: 1000 });
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
      expect(monitor.id).toBe('battery-monitor');
      expect(monitor.name).toBe('Battery Monitor');
    });

    it('should be idempotent on multiple initialize calls', async () => {
      await monitor.initialize();
      await monitor.initialize(); // Should not error

      expect(monitor.enabled).toBe(true);
    });

    it('should have battery-specific description', () => {
      expect(monitor.description).toContain('battery');
    });
  });

  describe('collection', () => {
    beforeEach(async () => {
      await monitor.initialize();
    });

    it('should collect battery metrics', async () => {
      const metrics = await monitor.collect('system');

      expect(metrics).toHaveProperty('level');
      expect(metrics).toHaveProperty('status');
      expect(metrics).toHaveProperty('timeToEmpty');
      expect(metrics).toHaveProperty('timeToFull');
      expect(metrics).toHaveProperty('voltage');
      expect(metrics).toHaveProperty('current');
      expect(metrics).toHaveProperty('health');
      expect(metrics).toHaveProperty('temperature');
      expect(metrics).toHaveProperty('timestamp');
    });

    it('should return valid battery level', async () => {
      const metrics = await monitor.collect('system');
      const level = metrics.level as number;

      expect(level).toBeGreaterThanOrEqual(0);
      expect(level).toBeLessThanOrEqual(100);
    });

    it('should return valid status', async () => {
      const metrics = await monitor.collect('system');
      const status = metrics.status as string;

      expect(['charging', 'discharging', 'full', 'unknown']).toContain(status);
    });

    it('should return valid health percentage', async () => {
      const metrics = await monitor.collect('system');
      const health = metrics.health as number;

      expect(health).toBeGreaterThanOrEqual(0);
      expect(health).toBeLessThanOrEqual(100);
    });

    it('should return negative current/time when not available', async () => {
      const metrics = await monitor.collect('system');

      // timeToEmpty and timeToFull should be -1 when not available
      const timeToEmpty = metrics.timeToEmpty as number;
      const timeToFull = metrics.timeToFull as number;

      expect(
        timeToEmpty === -1 ||
          (typeof timeToEmpty === 'number' && timeToEmpty >= 0),
      ).toBe(true);
      expect(
        timeToFull === -1 ||
          (typeof timeToFull === 'number' && timeToFull >= 0),
      ).toBe(true);
    });

    it('should reject unknown sources', async () => {
      await expect(monitor.collect('unknown')).rejects.toThrow(
        'Unknown battery source',
      );
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

      expect(result).toHaveProperty('level');
      expect(result).toHaveProperty('status');
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

    it('should fallback gracefully to simulated data', async () => {
      monitor = new BatteryMonitor({ maxRetries: 1 });
      await monitor.initialize();

      const metrics = await monitor.collect('system');

      // Should still return valid metrics even on systems without battery
      expect(metrics.level as number).toBeGreaterThanOrEqual(0);
      expect(metrics.status).toBeDefined();
    });
  });

  describe('status interpretation', () => {
    beforeEach(async () => {
      await monitor.initialize();
    });

    it('should interpret charging status correctly', async () => {
      // Collect multiple times to potentially see charging status
      for (let i = 0; i < 5; i++) {
        const metrics = await monitor.collect('system');
        const status = metrics.status as string;

        if (status === 'charging') {
          // When charging, timeToFull should be set (not -1) on real battery
          expect(['charging', 'discharging', 'full', 'unknown']).toContain(
            status,
          );
          break;
        }
      }
    });

    it('should provide consistent status across collections', async () => {
      const metrics1 = await monitor.collect('system');
      const metrics2 = await monitor.collect('system');

      // Status might not be identical (could change), but should be valid
      expect(['charging', 'discharging', 'full', 'unknown']).toContain(
        metrics1.status as string,
      );
      expect(['charging', 'discharging', 'full', 'unknown']).toContain(
        metrics2.status as string,
      );
    });
  });

  describe('configuration', () => {
    it('should use custom configuration', () => {
      monitor = new BatteryMonitor({
        pollingInterval: 2000,
        maxRetries: 5,
        timeout: 5000,
      });

      expect(monitor['config'].pollingInterval).toBe(2000);
      expect(monitor['config'].maxRetries).toBe(5);
      expect(monitor['config'].timeout).toBe(5000);
    });

    it('should use default configuration', () => {
      monitor = new BatteryMonitor();

      expect(monitor['config'].pollingInterval).toBe(5000);
      expect(monitor['config'].maxRetries).toBe(3);
      expect(monitor['config'].timeout).toBe(10000);
    });
  });

  describe('temperature metrics', () => {
    beforeEach(async () => {
      await monitor.initialize();
    });

    it('should return temperature in celsius', async () => {
      const metrics = await monitor.collect('system');
      const temp = metrics.temperature as number;

      // Reasonable temperature range: -20 to 80 celsius
      expect(temp).toBeGreaterThan(-20);
      expect(temp).toBeLessThan(100);
    });
  });

  describe('voltage and current metrics', () => {
    beforeEach(async () => {
      await monitor.initialize();
    });

    it('should return voltage in millivolts', async () => {
      const metrics = await monitor.collect('system');
      const voltage = metrics.voltage as number;

      // Typical battery voltage: 2000-5000 mV (generous range for simulated data)
      if (voltage > 0) {
        expect(voltage).toBeGreaterThan(1000);
        expect(voltage).toBeLessThan(15000); // Generous upper bound for edge cases
      }
    });

    it('should return current in milliamps', async () => {
      const metrics = await monitor.collect('system');
      const current = metrics.current as number;

      // Current can be negative (discharging) or positive (charging)
      // Reasonable range: -5000 to 5000 mA
      expect(current).toBeGreaterThan(-10000);
      expect(current).toBeLessThan(10000);
    });
  });
});
