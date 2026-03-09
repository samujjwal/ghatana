/**
 * Integration layer between PluginSystemAdapter and React hooks.
 * 
 * Provides useRealSystemMetrics hook that connects UI components
 * to the real system metrics via the plugin adapter.
 */

import { useCallback, useEffect, useState } from 'react';

import { PluginSystemAdapter } from './PluginSystemAdapter';

import type { PluginConfig, PluginError, PluginMetrics } from './PluginSystemAdapter';

export interface RealMetricsState {
  metrics: PluginMetrics | null;
  loading: boolean;
  error: PluginError | null;
  errorCount: number;
}

/**
 * Hook for real system metrics from Rust plugins.
 * Automatically initializes adapter and starts polling on mount.
 */
export function useRealSystemMetrics(): RealMetricsState & {
  refresh: () => Promise<void>;
  updateConfig: (config: Partial<import('./PluginSystemAdapter').PluginConfig>) => void;
  resetErrors: () => void;
} {
  const [state, setState] = useState<RealMetricsState>({
    metrics: null,
    loading: true,
    error: null,
    errorCount: 0,
  });

  const adapter = PluginSystemAdapter.getInstance();

  // Initialize adapter on first mount
  useEffect(() => {
    let mounted = true;

    const init = async () => {
      try {
        await adapter.initialize();
        if (!mounted) return;
      } catch (error) {
        if (!mounted) return;
        const errorMsg = error instanceof Error ? error.message : String(error);
        setState((prev) => ({
          ...prev,
          error: {
            code: 'INIT_ERROR',
            message: `Adapter initialization failed: ${errorMsg}`,
            plugin: 'system_adapter',
            recoverable: true,
            timestamp: Date.now(),
          },
          loading: false,
        }));
      }
    };

    void init();

    return () => {
      mounted = false;
    };
  }, [adapter]);

  // Set up metric polling
  useEffect(() => {
    const callback = (metrics: PluginMetrics, error?: PluginError) => {
      setState((prev) => ({
        metrics,
        loading: false,
        error: error || null,
        errorCount: error ? prev.errorCount + 1 : 0,
      }));
    };

    adapter.startPolling(callback);

    return () => {
      adapter.stopPolling(callback);
    };
  }, [adapter]);

  // Refresh metrics on demand
  const refresh = useCallback(async () => {
    setState((_prev) => ({ ..._prev, loading: true }));
    try {
      const metrics = await adapter.refreshMetrics();
      setState((_prev) => ({
        metrics,
        loading: false,
        error: null,
        errorCount: 0,
      }));
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : String(error);
      setState((_prev) => ({
        ..._prev,
        loading: false,
        error: {
          code: 'REFRESH_ERROR',
          message: `Refresh failed: ${errorMsg}`,
          plugin: 'system_adapter',
          recoverable: true,
          timestamp: Date.now(),
        },
      }));
    }
  }, [adapter]);

  // Update config
  const updateConfig = useCallback(
    (config: Partial<import('./PluginSystemAdapter').PluginConfig>) => {
      adapter.updateConfig(config);
    },
    [adapter]
  );

  // Reset errors
  const resetErrors = useCallback(() => {
    adapter.resetErrorCount();
    setState((prev) => ({
      ...prev,
      error: null,
      errorCount: 0,
    }));
  }, [adapter]);

  return {
    ...state,
    refresh,
    updateConfig,
    resetErrors,
  };
}

/**
 * Hook for safely accessing real metrics with fallback.
 * Returns real metrics if available, otherwise returns mock data.
 */
export function useMaybeRealMetrics(): PluginMetrics {
  const { metrics } = useRealSystemMetrics();

  return (
    metrics || {
      cpu: { usage: 0, temperature: 0, throttled: false, cores: 0 },
      memory: { usage: 0, total: 0, available: 0, gcActivity: 0 },
      battery: { level: 100, charging: false, health: 'good', timeRemaining: 0 },
      timestamp: Date.now(),
    }
  );
}
