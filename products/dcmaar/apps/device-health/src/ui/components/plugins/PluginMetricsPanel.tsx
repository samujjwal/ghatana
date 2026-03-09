/**
 * @file PluginMetricsPanel Component
 *
 * Main dashboard card displaying real-time metrics from CPU, Memory, and Battery monitors.
 * Integrates with usePluginMetrics hook for live updates and PluginSystemAdapter for data.
 *
 * @module src/ui/components/plugins/PluginMetricsPanel
 * @version 1.0.0
 */

import clsx from "clsx";
import React from "react";

import { MetricCard } from "./MetricCard";

/**
 * Interface definitions for plugin metrics
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

export interface PluginMetricsPanelProps {
  metrics: {
    cpu?: CPUMetrics;
    memory?: MemoryMetrics;
    battery?: BatteryMetrics;
  };
  isLoading?: boolean;
  error?: string | null;
  onRefresh?: () => void | Promise<void>;
  onSettings?: (type: "cpu" | "memory" | "battery") => void;
  showAlerts?: boolean;
}

/**
 * Helper function to determine CPU status based on usage
 */
const getCPUStatus = (
  usage?: number
): "good" | "warning" | "critical" => {
  if (usage === undefined) return "warning";
  if (usage >= 85) return "critical";
  if (usage >= 65) return "warning";
  return "good";
};

/**
 * Helper function to determine Memory status based on usage
 */
const getMemoryStatus = (
  usage?: number
): "good" | "warning" | "critical" => {
  if (usage === undefined) return "warning";
  if (usage >= 90) return "critical";
  if (usage >= 75) return "warning";
  return "good";
};

/**
 * Helper function to determine Battery status based on level and health
 */
const getBatteryStatus = (
  level?: number,
  health?: string
): "good" | "warning" | "critical" => {
  if (level === undefined) return "warning";
  if (health === "poor") return "critical";
  if (level <= 20) return "critical";
  if (level <= 40 || health === "fair") return "warning";
  return "good";
};

/**
 * Helper function to format battery time remaining
 */
const formatTimeRemaining = (minutes?: number): string => {
  if (!minutes) return "—";
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return `${hours}h ${mins}m`;
};

/**
 * PluginMetricsPanel Component
 *
 * Main dashboard card showing real-time CPU, Memory, and Battery metrics.
 * Renders MetricCard components in a responsive grid layout.
 *
 * @component
 * @example
 * ```tsx
 * const { metrics, isLoading, error, refetch } = usePluginMetrics();
 * return (
 *   <PluginMetricsPanel
 *     metrics={metrics}
 *     isLoading={isLoading}
 *     error={error}
 *     onRefresh={refetch}
 *     onSettings={(type) => navigate(`/settings/${type}`)}
 *     showAlerts={true}
 *   />
 * );
 * ```
 */
export const PluginMetricsPanel: React.FC<PluginMetricsPanelProps> = ({
  metrics,
  isLoading,
  error,
  onRefresh,
  onSettings,
  showAlerts,
}) => {
  const [isRefreshing, setIsRefreshing] = React.useState(false);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    try {
      if (onRefresh) {
        const result = onRefresh();
        if (result instanceof Promise) {
          await result;
        }
      }
    } finally {
      setIsRefreshing(false);
    }
  };

  return (
    <div
      className="plugin-metrics-panel rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
      data-testid="plugin-metrics-panel"
    >
      {/* Header */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h3 className="text-lg font-semibold text-gray-900">
            Real-time Monitoring
          </h3>
          <p className="mt-1 text-sm text-gray-500">
            Live metrics from system monitors
          </p>
        </div>

        {onRefresh && (
          <button
            onClick={handleRefresh}
            disabled={isRefreshing || isLoading}
            className={clsx(
              "rounded-md bg-gray-100 px-4 py-2 text-sm font-medium transition-all",
              "hover:bg-gray-200 focus:outline-none focus:ring-2 focus:ring-offset-2",
              (isRefreshing || isLoading) && "cursor-not-allowed opacity-50"
            )}
            data-testid="refresh-button"
            aria-label="Refresh metrics"
          >
            {isRefreshing ? (
              <span className="inline-flex items-center gap-2">
                <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
                Updating...
              </span>
            ) : (
              "🔄 Refresh"
            )}
          </button>
        )}
      </div>

      {/* Error Alert */}
      {error && (
        <div
          className="mb-6 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700"
          data-testid="error-alert"
          role="alert"
        >
          {error}
        </div>
      )}

      {/* Metrics Grid */}
      <div
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
        data-testid="metrics-grid"
      >
        {/* CPU Metric */}
        <MetricCard
          label="CPU"
          value={
            metrics.cpu?.usage !== undefined
              ? `${Math.round(metrics.cpu.usage)}`
              : "—"
          }
          unit="%"
          status={getCPUStatus(metrics.cpu?.usage)}
          trend={metrics.cpu?.trend}
          details={
            metrics.cpu
              ? {
                  Cores: metrics.cpu.cores,
                  ...(metrics.cpu.temperature && {
                    Temp: `${Math.round(metrics.cpu.temperature)}°C`,
                  }),
                  Throttled: metrics.cpu.throttled ? "Yes" : "No",
                }
              : undefined
          }
          onSettings={() => onSettings?.("cpu")}
          isLoading={isLoading}
          icon={
            <span role="img" aria-label="CPU">
              ⚙️
            </span>
          }
        />

        {/* Memory Metric */}
        <MetricCard
          label="Memory"
          value={
            metrics.memory?.usageMB !== undefined
              ? `${Math.round(metrics.memory.usageMB / 1024)}`
              : "—"
          }
          unit="GB"
          status={getMemoryStatus(metrics.memory?.usagePercent)}
          trend={metrics.memory?.trend}
          details={
            metrics.memory
              ? {
                  Used: `${Math.round(
                    (metrics.memory.usagePercent * 100) / 100
                  )}%`,
                  Total: `${(metrics.memory.totalMB / 1024).toFixed(1)}GB`,
                  "GC Activity": metrics.memory.gcActivity,
                }
              : undefined
          }
          onSettings={() => onSettings?.("memory")}
          isLoading={isLoading}
          icon={
            <span role="img" aria-label="Memory">
              💾
            </span>
          }
        />

        {/* Battery Metric */}
        <MetricCard
          label="Battery"
          value={
            metrics.battery?.levelPercent !== undefined
              ? `${Math.round(metrics.battery.levelPercent)}`
              : "—"
          }
          unit="%"
          status={getBatteryStatus(
            metrics.battery?.levelPercent,
            metrics.battery?.health
          )}
          trend={metrics.battery?.trend}
          details={
            metrics.battery
              ? {
                  Status: metrics.battery.charging ? "Charging" : "Discharging",
                  Health: metrics.battery.health,
                  "Drain Rate": `${metrics.battery.drainRate.toFixed(1)}%/h`,
                  ...(metrics.battery.timeRemaining && {
                    "Time Left": formatTimeRemaining(
                      metrics.battery.timeRemaining
                    ),
                  }),
                }
              : undefined
          }
          onSettings={() => onSettings?.("battery")}
          isLoading={isLoading}
          icon={
            <span role="img" aria-label="Battery">
              🔋
            </span>
          }
        />
      </div>

      {/* Alerts Summary */}
      {showAlerts && metrics.cpu && metrics.memory && metrics.battery && (
        <div className="mt-6 border-t pt-4">
          <AlertsSummary metrics={metrics} />
        </div>
      )}

      {/* Last Updated */}
      <div className="mt-4 text-right text-xs text-gray-500">
        Last updated: {new Date().toLocaleTimeString()}
      </div>
    </div>
  );
};

/**
 * AlertsSummary Component
 *
 * Displays summary of active alerts for critical metrics.
 *
 * @component
 */
interface AlertsSummaryProps {
  metrics: {
    cpu?: CPUMetrics;
    memory?: MemoryMetrics;
    battery?: BatteryMetrics;
  };
}

const AlertsSummary: React.FC<AlertsSummaryProps> = ({ metrics }) => {
  const alerts: string[] = [];

  if (metrics.cpu?.usage !== undefined && metrics.cpu.usage >= 85) {
    alerts.push(`CPU usage is high (${Math.round(metrics.cpu.usage)}%)`);
  }

  if (metrics.cpu?.throttled) {
    alerts.push("CPU is being throttled");
  }

  if (metrics.memory?.usagePercent !== undefined && metrics.memory.usagePercent >= 90) {
    alerts.push(
      `Memory usage is critical (${Math.round(metrics.memory.usagePercent)}%)`
    );
  }

  if (metrics.battery?.levelPercent !== undefined && metrics.battery.levelPercent <= 20) {
    alerts.push(`Battery is low (${Math.round(metrics.battery.levelPercent)}%)`);
  }

  if (metrics.battery?.health === "poor") {
    alerts.push("Battery health is poor");
  }

  if (alerts.length === 0) {
    return (
      <div className="text-sm text-green-700" data-testid="alerts-all-clear">
        ✓ All systems operating normally
      </div>
    );
  }

  return (
    <div className="space-y-2" data-testid="alerts-summary">
      <h4 className="text-sm font-semibold text-amber-900">⚠️ Active Alerts</h4>
      <ul className="space-y-1">
        {alerts.map((alert, idx) => (
          <li key={idx} className="text-sm text-amber-700">
            • {alert}
          </li>
        ))}
      </ul>
    </div>
  );
};

export default PluginMetricsPanel;
export { AlertsSummary };
