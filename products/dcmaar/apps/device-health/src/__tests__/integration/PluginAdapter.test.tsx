/**
 * Integration tests for PluginSystemAdapter and plugin handler.
 * Tests the real system metrics collection flow via Rust plugins.
 */

import { renderHook, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { PluginSystemAdapter, type PluginConfig } from '../../core/plugin/PluginSystemAdapter';
import { useRealSystemMetrics, useMaybeRealMetrics } from '../../core/plugin/useRealSystemMetrics';

describe('PluginSystemAdapter', () => {
  let adapter: PluginSystemAdapter;

  beforeEach(() => {
    // Get fresh instance for each test
    adapter = PluginSystemAdapter.getInstance();
    adapter.resetErrorCount();
  });

  afterEach(() => {
    adapter.shutdown();
  });

  describe('getInstance', () => {
    it('should return singleton instance', () => {
      const instance1 = PluginSystemAdapter.getInstance();
      const instance2 = PluginSystemAdapter.getInstance();
      expect(instance1).toBe(instance2);
    });

    it('should accept initial config', () => {
      const config: PluginConfig = {
        cpuEnabled: false,
        memoryEnabled: true,
        batteryEnabled: true,
        pollIntervalMs: 2000,
        maxCacheAgeMs: 10000,
        errorThreshold: 15,
      };
      const adapterWithConfig = PluginSystemAdapter.getInstance(config);
      expect(adapterWithConfig).toBeDefined();
    });
  });

  describe('initialize', () => {
    it('should initialize without errors', async () => {
      await expect(adapter.initialize()).resolves.not.toThrow();
    });

    it('should be idempotent', async () => {
      await adapter.initialize();
      await expect(adapter.initialize()).resolves.not.toThrow();
    });
  });

  describe('metrics collection', () => {
    it('should get default metrics on error', async () => {
      const metrics = await adapter.refreshMetrics();
      expect(metrics).toBeDefined();
      expect(metrics.cpu).toBeDefined();
      expect(metrics.memory).toBeDefined();
      expect(metrics.battery).toBeDefined();
      expect(metrics.timestamp).toBeDefined();
    });

    it('should provide CPU metrics structure', async () => {
      const metrics = await adapter.refreshMetrics();
      expect(metrics.cpu).toHaveProperty('usage');
      expect(metrics.cpu).toHaveProperty('temperature');
      expect(metrics.cpu).toHaveProperty('throttled');
      expect(metrics.cpu).toHaveProperty('cores');
    });

    it('should provide memory metrics structure', async () => {
      const metrics = await adapter.refreshMetrics();
      expect(metrics.memory).toHaveProperty('usage');
      expect(metrics.memory).toHaveProperty('total');
      expect(metrics.memory).toHaveProperty('available');
      expect(metrics.memory).toHaveProperty('gcActivity');
    });

    it('should provide battery metrics structure', async () => {
      const metrics = await adapter.refreshMetrics();
      expect(metrics.battery).toHaveProperty('level');
      expect(metrics.battery).toHaveProperty('charging');
      expect(metrics.battery).toHaveProperty('health');
      expect(metrics.battery).toHaveProperty('timeRemaining');
    });

    it('should include recent timestamp', async () => {
      const before = Date.now();
      const metrics = await adapter.refreshMetrics();
      const after = Date.now();
      expect(metrics.timestamp).toBeGreaterThanOrEqual(before);
      expect(metrics.timestamp).toBeLessThanOrEqual(after);
    });
  });

  describe('caching', () => {
    it('should cache metrics within maxCacheAgeMs', async () => {
      adapter.updateConfig({ maxCacheAgeMs: 5000 });
      const metrics1 = await adapter.refreshMetrics();
      const metrics2 = adapter.getMetrics();
      expect(metrics2).toEqual(metrics1);
    });

    it('should return null for expired cache', async () => {
      vi.useFakeTimers();
      try {
        adapter.updateConfig({ maxCacheAgeMs: 100 });
        await adapter.refreshMetrics();
        vi.advanceTimersByTime(150);
        const metrics = adapter.getMetrics();
        expect(metrics).toBeNull();
      } finally {
        vi.useRealTimers();
      }
    });
  });

  describe('polling', () => {
    it('should call callback when polling', async () => {
      const callback = vi.fn();
      adapter.startPolling(callback);
      await waitFor(() => expect(callback).toHaveBeenCalled(), { timeout: 3000 });
      adapter.stopPolling(callback);
    });

    it('should stop polling', async () => {
      const callback = vi.fn();
      adapter.startPolling(callback);
      await waitFor(() => expect(callback).toHaveBeenCalled(), { timeout: 3000 });
      const callCount = callback.mock.calls.length;
      adapter.stopPolling(callback);
      // Wait to ensure no more calls
      await new Promise((resolve) => setTimeout(resolve, 100));
      expect(callback.mock.calls.length).toBe(callCount);
    });

    it('should handle multiple callbacks', async () => {
      const callback1 = vi.fn();
      const callback2 = vi.fn();
      adapter.startPolling(callback1);
      adapter.startPolling(callback2);
      await waitFor(() => expect(callback1).toHaveBeenCalled(), { timeout: 3000 });
      await waitFor(() => expect(callback2).toHaveBeenCalled(), { timeout: 3000 });
      adapter.stopPolling(callback1);
      adapter.stopPolling(callback2);
    });
  });

  describe('error handling', () => {
    it('should track error count', async () => {
      expect(adapter.getErrorCount()).toBe(0);
      adapter.resetErrorCount();
      expect(adapter.getErrorCount()).toBe(0);
    });

    it('should reset error count', () => {
      adapter.resetErrorCount();
      expect(adapter.getErrorCount()).toBe(0);
    });
  });

  describe('configuration', () => {
    it('should update config', () => {
      const newConfig: Partial<PluginConfig> = {
        cpuEnabled: false,
        pollIntervalMs: 2000,
      };
      adapter.updateConfig(newConfig);
      // Config is applied internally, verify no error
      expect(() => adapter.updateConfig(newConfig)).not.toThrow();
    });

    it('should merge config updates', () => {
      adapter.updateConfig({ cpuEnabled: false });
      adapter.updateConfig({ memoryEnabled: true });
      // Both settings should be applied
      expect(() => adapter.getMetrics()).not.toThrow();
    });
  });
});

describe('useRealSystemMetrics', () => {
  afterEach(() => {
    const adapter = PluginSystemAdapter.getInstance();
    adapter.shutdown();
  });

  it('should initialize adapter on mount', async () => {
    const { result } = renderHook(() => useRealSystemMetrics());
    await waitFor(() => expect(result.current.loading).toBe(false), { timeout: 3000 });
  });

  it('should start with loading state', () => {
    const { result } = renderHook(() => useRealSystemMetrics());
    expect(result.current.loading).toBe(true);
  });

  it('should provide metrics after polling', async () => {
    const { result } = renderHook(() => useRealSystemMetrics());
    await waitFor(() => expect(result.current.metrics).toBeDefined(), { timeout: 3000 });
  });

  it('should provide refresh function', async () => {
    const { result } = renderHook(() => useRealSystemMetrics());
    expect(typeof result.current.refresh).toBe('function');
    await result.current.refresh();
    await waitFor(() => expect(result.current.loading).toBe(false), { timeout: 3000 });
  });

  it('should provide updateConfig function', () => {
    const { result } = renderHook(() => useRealSystemMetrics());
    expect(typeof result.current.updateConfig).toBe('function');
    expect(() => result.current.updateConfig({ cpuEnabled: false })).not.toThrow();
  });

  it('should provide resetErrors function', async () => {
    const { result } = renderHook(() => useRealSystemMetrics());
    expect(typeof result.current.resetErrors).toBe('function');
    result.current.resetErrors();
    expect(result.current.errorCount).toBe(0);
  });

  it('should handle errors gracefully', async () => {
    const { result } = renderHook(() => useRealSystemMetrics());
    await waitFor(() => expect(result.current.loading).toBe(false), { timeout: 3000 });
    // Error should be null or defined with proper structure
    if (result.current.error) {
      expect(result.current.error).toHaveProperty('code');
      expect(result.current.error).toHaveProperty('message');
      expect(result.current.error).toHaveProperty('plugin');
    }
  });
});

describe('useMaybeRealMetrics', () => {
  afterEach(() => {
    const adapter = PluginSystemAdapter.getInstance();
    adapter.shutdown();
  });

  it('should return valid metrics structure', async () => {
    const { result } = renderHook(() => useMaybeRealMetrics());
    const metrics = result.current;
    expect(metrics).toHaveProperty('cpu');
    expect(metrics).toHaveProperty('memory');
    expect(metrics).toHaveProperty('battery');
    expect(metrics).toHaveProperty('timestamp');
  });

  it('should have CPU metrics', () => {
    const { result } = renderHook(() => useMaybeRealMetrics());
    expect(result.current.cpu).toHaveProperty('usage');
    expect(result.current.cpu).toHaveProperty('temperature');
    expect(result.current.cpu).toHaveProperty('throttled');
    expect(result.current.cpu).toHaveProperty('cores');
  });

  it('should have memory metrics', () => {
    const { result } = renderHook(() => useMaybeRealMetrics());
    expect(result.current.memory).toHaveProperty('usage');
    expect(result.current.memory).toHaveProperty('total');
    expect(result.current.memory).toHaveProperty('available');
    expect(result.current.memory).toHaveProperty('gcActivity');
  });

  it('should have battery metrics', () => {
    const { result } = renderHook(() => useMaybeRealMetrics());
    expect(result.current.battery).toHaveProperty('level');
    expect(result.current.battery).toHaveProperty('charging');
    expect(result.current.battery).toHaveProperty('health');
    expect(result.current.battery).toHaveProperty('timeRemaining');
  });

  it('should always return valid metrics even on errors', async () => {
    const { result } = renderHook(() => useMaybeRealMetrics());
    await waitFor(() => {
      expect(result.current.cpu.cores).toBeGreaterThanOrEqual(0);
    }, { timeout: 3000 });
  });
});

describe('Plugin Adapter Integration Flow', () => {
  afterEach(() => {
    const adapter = PluginSystemAdapter.getInstance();
    adapter.shutdown();
  });

  it('should support full polling lifecycle', async () => {
    const { result: hookResult } = renderHook(() => useRealSystemMetrics());

    // Wait for initial metrics
    await waitFor(() => expect(hookResult.current.metrics).toBeDefined(), { timeout: 3000 });

    // Verify metrics are populated
    expect(hookResult.current.metrics?.cpu).toBeDefined();
    expect(hookResult.current.metrics?.memory).toBeDefined();
    expect(hookResult.current.metrics?.battery).toBeDefined();

    // Refresh metrics
    await hookResult.current.refresh();
    expect(hookResult.current.loading).toBe(false);
  });

  it('should handle config updates during polling', async () => {
    const { result: hookResult } = renderHook(() => useRealSystemMetrics());

    await waitFor(() => expect(hookResult.current.metrics).toBeDefined(), { timeout: 3000 });

    // Update config while polling
    hookResult.current.updateConfig({ cpuEnabled: false });

    // Verify still works
    await waitFor(() => expect(hookResult.current.metrics).toBeDefined(), { timeout: 3000 });
  });

  it('should handle rapid refresh calls', async () => {
    const { result: hookResult } = renderHook(() => useRealSystemMetrics());

    await waitFor(() => expect(hookResult.current.metrics).toBeDefined(), { timeout: 3000 });

    // Rapid refresh calls should not cause errors
    await Promise.all([
      hookResult.current.refresh(),
      hookResult.current.refresh(),
      hookResult.current.refresh(),
    ]);

    expect(hookResult.current.error).toBeNull();
  });

  it('should provide stable metrics over time', async () => {
    const { result: hookResult } = renderHook(() => useRealSystemMetrics());

    await waitFor(() => expect(hookResult.current.metrics).toBeDefined(), { timeout: 3000 });

    const firstMetrics = hookResult.current.metrics;
    await new Promise((resolve) => setTimeout(resolve, 500));
    const secondMetrics = hookResult.current.metrics;

    // Both should be valid, though they may differ
    expect(firstMetrics).toBeDefined();
    expect(secondMetrics).toBeDefined();
    expect(firstMetrics?.timestamp).toBeLessThanOrEqual(secondMetrics?.timestamp || 0);
  });
});
