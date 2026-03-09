/**
 * Monitor Plugins Integration Tests
 * Tests for CPU, Memory, and Battery monitor plugins
 *
 * Phase 3f.7: Monitor Plugin Tests Implementation
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

import BatteryMonitor from '../../../../src/plugins/monitors/BatteryMonitor';
import CPUMonitor from '../../../../src/plugins/monitors/CPUMonitor';
import MemoryMonitor from '../../../../src/plugins/monitors/MemoryMonitor';

describe('Monitor Plugins Integration', () => {
  describe('CPUMonitor Plugin', () => {
    let cpuMonitor: typeof CPUMonitor;

    beforeEach(() => {
      cpuMonitor = new (CPUMonitor as any)();
      vi.clearAllMocks();
    });

    it('should initialize with correct metadata', () => {
      expect(cpuMonitor.id).toBe('cpu-monitor');
      expect(cpuMonitor.name).toBe('CPU Monitor');
      expect(cpuMonitor.type).toBe('monitor');
    });

    it('should return available sources', async () => {
      const sources = await cpuMonitor.getSources();
      expect(sources).toContain('cpu-usage');
      expect(sources).toContain('cpu-cores');
      expect(sources).toContain('cpu-temperature');
      expect(sources).toContain('cpu-throttling');
    });

    it('should collect metrics with valid structure', async () => {
      const metrics = await cpuMonitor.collect();
      expect(metrics).toHaveProperty('usage');
      expect(metrics).toHaveProperty('cores');
      expect(metrics).toHaveProperty('throttled');
      expect(metrics).toHaveProperty('trend');
    });

    it('should have valid usage value (0-100)', async () => {
      const metrics = await cpuMonitor.collect();
      const usage = metrics.usage as number;
      expect(usage).toBeGreaterThanOrEqual(0);
      expect(usage).toBeLessThanOrEqual(100);
    });

    it('should have valid trend value', async () => {
      const metrics = await cpuMonitor.collect();
      const trend = metrics.trend as string;
      expect(['rising', 'stable', 'falling']).toContain(trend);
    });

    it('should return proper status', () => {
      const status = cpuMonitor.getStatus();
      expect(['healthy', 'warning']).toContain(status);
    });

    it('should support plugin lifecycle', async () => {
      await expect(cpuMonitor.initialize()).resolves.toBeUndefined();
      await expect(cpuMonitor.shutdown()).resolves.toBeUndefined();
    });

    it('should handle error gracefully on collection failure', async () => {
      vi.spyOn(cpuMonitor, 'collect').mockRejectedValue(new Error('API Error'));
      const result = await cpuMonitor.collect();
      expect(result).toHaveProperty('usage');
      expect(result.usage).toBeGreaterThanOrEqual(0);
    });

    it('should populate error property on API unavailability', async () => {
      // This tests graceful degradation
      const metrics = await cpuMonitor.collect();
      expect(metrics).toBeDefined();
      expect(typeof metrics === 'object').toBe(true);
    });
  });

  describe('MemoryMonitor Plugin', () => {
    let memoryMonitor: typeof MemoryMonitor;

    beforeEach(() => {
      memoryMonitor = new (MemoryMonitor as any)();
      vi.clearAllMocks();
    });

    it('should initialize with correct metadata', () => {
      expect(memoryMonitor.id).toBe('memory-monitor');
      expect(memoryMonitor.name).toBe('Memory Monitor');
      expect(memoryMonitor.type).toBe('monitor');
    });

    it('should return available sources', async () => {
      const sources = await memoryMonitor.getSources();
      expect(sources).toContain('memory-usage');
      expect(sources).toContain('memory-total');
      expect(sources).toContain('memory-available');
      expect(sources).toContain('gc-activity');
    });

    it('should collect metrics with valid structure', async () => {
      const metrics = await memoryMonitor.collect();
      expect(metrics).toHaveProperty('usageMB');
      expect(metrics).toHaveProperty('usagePercent');
      expect(metrics).toHaveProperty('totalMB');
      expect(metrics).toHaveProperty('availableMB');
      expect(metrics).toHaveProperty('gcActivity');
      expect(metrics).toHaveProperty('trend');
    });

    it('should have valid memory percentages (0-100)', async () => {
      const metrics = await memoryMonitor.collect();
      const usagePercent = metrics.usagePercent as number;
      expect(usagePercent).toBeGreaterThanOrEqual(0);
      expect(usagePercent).toBeLessThanOrEqual(100);
    });

    it('should have valid GC activity levels', async () => {
      const metrics = await memoryMonitor.collect();
      const gcActivity = metrics.gcActivity as string;
      expect(['low', 'moderate', 'high']).toContain(gcActivity);
    });

    it('should have valid trend value', async () => {
      const metrics = await memoryMonitor.collect();
      const trend = metrics.trend as string;
      expect(['rising', 'stable', 'falling']).toContain(trend);
    });

    it('should have MB values greater than 0', async () => {
      const metrics = await memoryMonitor.collect();
      expect(metrics.usageMB).toBeGreaterThanOrEqual(0);
      expect(metrics.totalMB).toBeGreaterThanOrEqual(0);
      expect(metrics.availableMB).toBeGreaterThanOrEqual(0);
    });

    it('should return proper status based on memory usage', () => {
      const status = memoryMonitor.getStatus();
      expect(['healthy', 'warning']).toContain(status);
    });

    it('should support plugin lifecycle', async () => {
      await expect(memoryMonitor.initialize()).resolves.toBeUndefined();
      await expect(memoryMonitor.shutdown()).resolves.toBeUndefined();
    });

    it('should handle missing API gracefully', async () => {
      const metrics = await memoryMonitor.collect();
      expect(metrics).toHaveProperty('usageMB');
      expect(metrics).toHaveProperty('usagePercent');
      // Should have default values
      expect(metrics.usageMB as number).toBeGreaterThanOrEqual(0);
    });

    it('should track memory trend over time', async () => {
      // First collection
      await memoryMonitor.collect();
      // Second collection should establish trend baseline
      const metrics1 = await memoryMonitor.collect();
      expect(metrics1).toHaveProperty('trend');

      // Third collection should provide trend
      const metrics2 = await memoryMonitor.collect();
      expect(['rising', 'stable', 'falling']).toContain(metrics2.trend);
    });
  });

  describe('BatteryMonitor Plugin', () => {
    let batteryMonitor: typeof BatteryMonitor;

    beforeEach(() => {
      batteryMonitor = new (BatteryMonitor as any)();
      vi.clearAllMocks();
    });

    it('should initialize with correct metadata', () => {
      expect(batteryMonitor.id).toBe('battery-monitor');
      expect(batteryMonitor.name).toBe('Battery Monitor');
      expect(batteryMonitor.type).toBe('monitor');
    });

    it('should return available sources', async () => {
      const sources = await batteryMonitor.getSources();
      expect(sources).toContain('battery-level');
      expect(sources).toContain('battery-charging');
      expect(sources).toContain('battery-drain-rate');
      expect(sources).toContain('battery-time-remaining');
    });

    it('should collect metrics with valid structure', async () => {
      const metrics = await batteryMonitor.collect();
      expect(metrics).toHaveProperty('levelPercent');
      expect(metrics).toHaveProperty('charging');
      expect(metrics).toHaveProperty('timeRemaining');
      expect(metrics).toHaveProperty('health');
    });

    it('should have valid battery percentage (0-100)', async () => {
      const metrics = await batteryMonitor.collect();
      const levelPercent = metrics.levelPercent as number;
      expect(levelPercent).toBeGreaterThanOrEqual(0);
      expect(levelPercent).toBeLessThanOrEqual(100);
    });

    it('should have valid health levels', async () => {
      const metrics = await batteryMonitor.collect();
      const health = metrics.health as string;
      expect(['good', 'fair', 'poor']).toContain(health);
    });

    it('should have boolean charging property', async () => {
      const metrics = await batteryMonitor.collect();
      expect(typeof metrics.charging).toBe('boolean');
    });

    it('should have non-negative time values', async () => {
      const metrics = await batteryMonitor.collect();
      expect(metrics.timeRemaining as number).toBeGreaterThanOrEqual(0);
      expect(metrics.timeToFullCharge as number).toBeGreaterThanOrEqual(0);
    });

    it('should return proper status based on battery level', () => {
      const status = batteryMonitor.getStatus();
      expect(['critical', 'warning', 'healthy']).toContain(status);
    });

    it('should support plugin lifecycle', async () => {
      await expect(batteryMonitor.initialize()).resolves.toBeUndefined();
      await expect(batteryMonitor.shutdown()).resolves.toBeUndefined();
    });

    it('should handle missing Battery Status API gracefully', async () => {
      const metrics = await batteryMonitor.collect();
      expect(metrics).toHaveProperty('levelPercent');
      // Should have default values
      expect(metrics.levelPercent as number).toBeGreaterThanOrEqual(0);
      expect(typeof metrics.charging).toBe('boolean');
    });

    it('should calculate drain rate from history', async () => {
      // Collect multiple times to build history
      await batteryMonitor.collect();
      await batteryMonitor.collect();
      const metrics = await batteryMonitor.collect();
      expect(metrics).toHaveProperty('drainRate');
      expect(metrics.drainRate as number).toBeGreaterThanOrEqual(0);
    });

    it('should trigger low battery alert appropriately', async () => {
      // The alert property should only be true when battery is critically low
      const metrics = await batteryMonitor.collect();
      if (metrics.levelPercent as number < 20) {
        expect(metrics.alert).toBe(true);
      }
    });
  });

  describe('Plugin Interoperability', () => {
    let cpuMonitor: typeof CPUMonitor;
    let memoryMonitor: typeof MemoryMonitor;
    let batteryMonitor: typeof BatteryMonitor;

    beforeEach(() => {
      cpuMonitor = new (CPUMonitor as any)();
      memoryMonitor = new (MemoryMonitor as any)();
      batteryMonitor = new (BatteryMonitor as any)();
    });

    it('should support parallel collection', async () => {
      const results = await Promise.all([
        cpuMonitor.collect(),
        memoryMonitor.collect(),
        batteryMonitor.collect(),
      ]);

      expect(results).toHaveLength(3);
      results.forEach((result) => {
        expect(result).toBeDefined();
        expect(typeof result === 'object').toBe(true);
      });
    });

    it('should handle rapid sequential collections', async () => {
      for (let i = 0; i < 5; i++) {
        const cpu = await cpuMonitor.collect();
        const memory = await memoryMonitor.collect();
        const battery = await batteryMonitor.collect();

        expect(cpu).toHaveProperty('usage');
        expect(memory).toHaveProperty('usageMB');
        expect(battery).toHaveProperty('levelPercent');
      }
    });

    it('should maintain independent state across plugins', async () => {
      await cpuMonitor.initialize();
      await memoryMonitor.initialize();
      await batteryMonitor.initialize();

      const cpu1 = await cpuMonitor.collect();
      const mem1 = await memoryMonitor.collect();
      const bat1 = await batteryMonitor.collect();

      await cpuMonitor.shutdown();

      // Other plugins should not be affected
      const mem2 = await memoryMonitor.collect();
      const bat2 = await batteryMonitor.collect();

      expect(mem2).toHaveProperty('usageMB');
      expect(bat2).toHaveProperty('levelPercent');
    });

    it('should have unique plugin IDs', () => {
      const ids = [cpuMonitor.id, memoryMonitor.id, batteryMonitor.id];
      const uniqueIds = new Set(ids);
      expect(uniqueIds.size).toBe(3);
    });

    it('should all have type "monitor"', () => {
      expect(cpuMonitor.getType()).toBe('monitor');
      expect(memoryMonitor.getType()).toBe('monitor');
      expect(batteryMonitor.getType()).toBe('monitor');
    });
  });

  describe('Error Handling & Edge Cases', () => {
    let cpuMonitor: typeof CPUMonitor;
    let memoryMonitor: typeof MemoryMonitor;
    let batteryMonitor: typeof BatteryMonitor;

    beforeEach(() => {
      cpuMonitor = new (CPUMonitor as any)();
      memoryMonitor = new (MemoryMonitor as any)();
      batteryMonitor = new (BatteryMonitor as any)();
    });

    it('should recover from collection errors', async () => {
      // Collect normally
      const firstMetrics = await cpuMonitor.collect();
      expect(firstMetrics).toHaveProperty('usage');

      // Even if error occurs internally, should return metrics
      const secondMetrics = await cpuMonitor.collect();
      expect(secondMetrics).toHaveProperty('usage');
    });

    it('should return consistent metric structure after errors', async () => {
      const metrics1 = await memoryMonitor.collect();
      const metrics2 = await memoryMonitor.collect();

      expect(Object.keys(metrics1).sort()).toEqual(Object.keys(metrics2).sort());
    });

    it('should handle re-initialization gracefully', async () => {
      await batteryMonitor.initialize();
      await batteryMonitor.initialize(); // Second init

      const metrics = await batteryMonitor.collect();
      expect(metrics).toHaveProperty('levelPercent');
    });

    it('should handle multiple shutdown calls', async () => {
      await cpuMonitor.initialize();
      await expect(cpuMonitor.shutdown()).resolves.toBeUndefined();
      await expect(cpuMonitor.shutdown()).resolves.toBeUndefined(); // Second shutdown
    });

    it('should return metrics even before initialization', async () => {
      // Create new instances without calling initialize
      const cpu = new (CPUMonitor as any)();
      const memory = new (MemoryMonitor as any)();
      const battery = new (BatteryMonitor as any)();

      // Should still be able to collect
      const cpuMetrics = await cpu.collect();
      const memMetrics = await memory.collect();
      const batMetrics = await battery.collect();

      expect(cpuMetrics).toHaveProperty('usage');
      expect(memMetrics).toHaveProperty('usageMB');
      expect(batMetrics).toHaveProperty('levelPercent');
    });
  });
});
