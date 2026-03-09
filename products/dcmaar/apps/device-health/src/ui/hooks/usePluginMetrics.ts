/**
 * @file usePluginMetrics Hook
 *
 * Real-time polling hook for fetching monitor metrics from PluginSystemAdapter.
 * Handles CPU (1s), Memory (1s), and Battery (10s) polling intervals independently.
 *
 * @module src/ui/hooks/usePluginMetrics
 * @version 1.0.0
 */

import { useCallback, useEffect, useState } from "react";
import browser from "webextension-polyfill";

/**
 * Type definitions for metrics
 */
export interface CPUMetrics {
  usage: number; // 0-100
  cores: number;
  temperature?: number; // °C
  throttled: boolean;
  trend: "rising" | "stable" | "falling";
}

export interface MemoryMetrics {
  usageMB: number;
  usagePercent: number; // 0-100
  totalMB: number;
  availableMB: number;
  gcActivity: "low" | "moderate" | "high";
  trend: "rising" | "stable" | "falling";
}

export interface BatteryMetrics {
  levelPercent: number; // 0-100
  charging: boolean;
  timeRemaining?: number; // minutes
  health: "good" | "fair" | "poor";
  drainRate: number; // % per hour
  trend: "rising" | "stable" | "falling";
}

/**
 * Return type for usePluginMetrics hook
 *
 * @interface UsePluginMetricsReturn
 * @property {Object} metrics - Current metric values
 * @property {CPUMetrics | undefined} metrics.cpu - CPU metrics
 * @property {MemoryMetrics | undefined} metrics.memory - Memory metrics
 * @property {BatteryMetrics | undefined} metrics.battery - Battery metrics
 * @property {boolean} isLoading - True while fetching initial data
 * @property {string | null} error - Error message if fetch failed
 * @property {() => Promise<void>} refetch - Manual refetch function
 */
export interface UsePluginMetricsReturn {
  metrics: {
    cpu?: CPUMetrics;
    memory?: MemoryMetrics;
    battery?: BatteryMetrics;
  };
  isLoading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
}

/**
 * usePluginMetrics Hook
 *
 * Fetches real-time metrics from the PluginSystemAdapter at configurable intervals.
 * Handles different polling rates for different metric types:
 * - CPU: 1 second (fast-changing)
 * - Memory: 1 second (fast-changing)
 * - Battery: 10 seconds (slow-changing)
 *
 * Cleans up intervals on unmount. Supports enable/disable toggle.
 *
 * @param {boolean} [enabled=true] - Enable or disable polling
 * @param {Partial<{cpuInterval, memoryInterval, batteryInterval}>} [config] - Optional polling intervals
 * @returns {UsePluginMetricsReturn} Metrics, loading state, error, and refetch function
 *
 * @example
 * ```tsx
 * function Dashboard() {
 *   const { metrics, isLoading, error, refetch } = usePluginMetrics();
 *
 *   return (
 *     <PluginMetricsPanel
 *       metrics={metrics}
 *       isLoading={isLoading}
 *       error={error}
 *       onRefresh={refetch}
 *     />
 *   );
 * }
 * ```
 */
export function usePluginMetrics(
  enabled: boolean = true,
  config?: {
    cpuInterval?: number;
    memoryInterval?: number;
    batteryInterval?: number;
  }
): UsePluginMetricsReturn {
  const {
    cpuInterval = 1000,
    memoryInterval = 1000,
    batteryInterval = 10000,
  } = config || {};

  const [metrics, setMetrics] = useState<UsePluginMetricsReturn["metrics"]>({
    cpu: undefined,
    memory: undefined,
    battery: undefined,
  });

  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  /**
   * Fetch individual metric from PluginSystemAdapter (mocked for now)
   */
  const fetchMetric = useCallback(
    async (
      type: "cpu" | "memory" | "battery"
    ): Promise<CPUMetrics | MemoryMetrics | BatteryMetrics | undefined> => {
      try {
        const messageType =
          type === "cpu"
            ? "GET_CPU_METRICS"
            : type === "memory"
              ? "GET_MEMORY_METRICS"
              : "GET_BATTERY_METRICS";

        const response = await browser.runtime.sendMessage({ type: messageType });

        if (!response || typeof response !== "object" || !("ok" in (response as any))) {
          throw new Error(`Invalid response for ${messageType}`);
        }

        const { ok, data, error: responseError } = response as {
          ok: boolean;
          data?: unknown;
          error?: unknown;
        };

        if (!ok) {
          throw new Error(
            typeof responseError === "string"
              ? responseError
              : `Request ${messageType} failed`,
          );
        }

        if (!data || typeof data !== "object") {
          return undefined;
        }

        const pluginMetric = data as {
          pluginId: string;
          metrics?: Record<string, unknown>;
        };

        if (!pluginMetric.metrics || typeof pluginMetric.metrics !== "object") {
          return undefined;
        }

        const metricValues = Object.values(pluginMetric.metrics);
        if (metricValues.length === 0) {
          return undefined;
        }

        const raw = metricValues[0] as Record<string, unknown>;

        if (type === "cpu") {
          return raw as unknown as CPUMetrics;
        }

        if (type === "memory") {
          return raw as unknown as MemoryMetrics;
        }

        const battery = raw as Partial<BatteryMetrics>;

        return {
          levelPercent:
            typeof battery.levelPercent === "number" ? battery.levelPercent : 0,
          charging: battery.charging ?? false,
          timeRemaining:
            typeof battery.timeRemaining === "number"
              ? battery.timeRemaining
              : undefined,
          health: battery.health ?? "good",
          drainRate:
            typeof battery.drainRate === "number" ? battery.drainRate : 0,
          trend: battery.trend ?? "stable",
        } as BatteryMetrics;
      } catch (err) {
        console.error(`Failed to fetch ${type} metrics:`, err);
        return undefined;
      }
    },
    []
  );

  /**
   * Fetch all metrics
   */
  const fetchAllMetrics = useCallback(async () => {
    setError(null);

    try {
      const [cpu, memory, battery] = await Promise.all([
        fetchMetric("cpu"),
        fetchMetric("memory"),
        fetchMetric("battery"),
      ]);

      setMetrics({
        cpu: cpu as CPUMetrics | undefined,
        memory: memory as MemoryMetrics | undefined,
        battery: battery as BatteryMetrics | undefined,
      });
    } catch (err) {
      const message =
        err instanceof Error
          ? err.message
          : "Failed to fetch plugin metrics";
      setError(message);
      console.error("Error fetching metrics:", err);
    } finally {
      setIsLoading(false);
    }
  }, [fetchMetric]);

  /**
   * Public refetch function
   */
  const refetch = useCallback(async () => {
    setIsLoading(true);
    await fetchAllMetrics();
  }, [fetchAllMetrics]);

  /**
   * Set up polling intervals
   */
  useEffect(() => {
    if (!enabled) {
      return;
    }

    // Fetch immediately on mount
    fetchAllMetrics();

    // Fast polling for CPU and Memory (1s)
    const fastInterval = setInterval(() => {
      (async () => {
        const cpu = await fetchMetric("cpu");
        const memory = await fetchMetric("memory");

        setMetrics((prev) => ({
          ...prev,
          cpu: cpu as CPUMetrics | undefined,
          memory: memory as MemoryMetrics | undefined,
        }));
      })();
    }, cpuInterval);

    // Slow polling for Battery (10s)
    const slowInterval = setInterval(() => {
      (async () => {
        const battery = await fetchMetric("battery");

        setMetrics((prev) => ({
          ...prev,
          battery: battery as BatteryMetrics | undefined,
        }));
      })();
    }, batteryInterval);

    // Cleanup
    return () => {
      clearInterval(fastInterval);
      clearInterval(slowInterval);
    };
  }, [enabled, cpuInterval, memoryInterval, batteryInterval, fetchAllMetrics, fetchMetric]);

  return { metrics, isLoading, error, refetch };
}

export default usePluginMetrics;
