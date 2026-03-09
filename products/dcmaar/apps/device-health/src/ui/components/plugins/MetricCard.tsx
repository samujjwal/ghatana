/**
 * @file MetricCard Component
 *
 * Reusable metric display card for CPU, Memory, and Battery metrics.
 * Shows metric value, status, trend, and optional details.
 *
 * @module src/ui/components/plugins/MetricCard
 * @version 1.0.0
 */

import clsx from "clsx";
import React from "react";

/**
 * MetricCardProps - Props for MetricCard component
 *
 * @interface MetricCardProps
 * @property {string} label - Metric name ("CPU", "Memory", "Battery")
 * @property {string | number} value - Current metric value
 * @property {string} [unit] - Unit of measurement (%, °C, GB)
 * @property {"good" | "warning" | "critical"} status - Health status for color coding
 * @property {"rising" | "stable" | "falling"} [trend] - Value trend indicator
 * @property {Record<string, string>} [details] - Additional metric details
 * @property {React.ReactNode} [icon] - Optional icon element
 * @property {() => void} [onSettings] - Callback when settings button clicked
 * @property {boolean} [isLoading] - Show loading skeleton
 */
export interface MetricCardProps {
  label: string;
  value: string | number;
  unit?: string;
  status: "good" | "warning" | "critical";
  trend?: "rising" | "stable" | "falling";
  details?: Record<string, string | number>;
  icon?: React.ReactNode;
  onSettings?: () => void;
  isLoading?: boolean;
}

/**
 * MetricCard Component
 *
 * Displays a single metric with status indicator, trend arrow, and optional details.
 * Responsive design with mobile-first approach.
 *
 * @component
 * @example
 * ```tsx
 * <MetricCard
 *   label="CPU"
 *   value={45}
 *   unit="%"
 *   status="good"
 *   trend="stable"
 *   icon={<CPUIcon />}
 *   details={{ Cores: "8", Temp: "55°C" }}
 *   onSettings={() => navigate("/settings/cpu")}
 * />
 * ```
 */
export const MetricCard: React.FC<MetricCardProps> = ({
  label,
  value,
  unit,
  status,
  trend,
  details,
  icon,
  onSettings,
  isLoading,
}) => {
  if (isLoading) {
    return <MetricCardSkeleton />;
  }

  const statusColors = {
    good: "border-green-200 bg-green-50 text-green-900",
    warning: "border-amber-200 bg-amber-50 text-amber-900",
    critical: "border-red-200 bg-red-50 text-red-900",
  };

  const statusIndicators = {
    good: "bg-green-500",
    warning: "bg-amber-500",
    critical: "bg-red-500",
  };

  const trendArrows = {
    rising: "↑ text-red-500",
    stable: "→ text-gray-400",
    falling: "↓ text-green-500",
  };

  return (
    <div
      className={clsx(
        "metric-card rounded-lg border p-4 transition-all",
        statusColors[status]
      )}
      data-testid={`metric-card-${label.toLowerCase()}`}
    >
      {/* Header: Label + Icon + Settings Button */}
      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          {icon && (
            <div
              className={clsx(
                "flex h-8 w-8 items-center justify-center rounded-full",
                status === "good" && "bg-green-200 text-green-700",
                status === "warning" && "bg-amber-200 text-amber-700",
                status === "critical" && "bg-red-200 text-red-700"
              )}
              data-testid={`metric-icon-${label.toLowerCase()}`}
            >
              {icon}
            </div>
          )}
          <h4 className="text-sm font-semibold">{label}</h4>
        </div>

        {onSettings && (
          <button
            onClick={onSettings}
            className="rounded-md p-1 opacity-60 hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-offset-1"
            aria-label={`Settings for ${label}`}
            data-testid={`metric-settings-${label.toLowerCase()}`}
          >
            ⚙️
          </button>
        )}
      </div>

      {/* Metric Value + Unit + Trend */}
      <div className="mb-3 flex items-baseline gap-1">
        <span className="text-2xl font-bold" data-testid={`metric-value`}>
          {value}
        </span>
        {unit && (
          <span className="text-sm font-medium opacity-75">{unit}</span>
        )}
        {trend && (
          <span
            className={clsx("ml-auto text-lg font-bold", trendArrows[trend])}
            data-testid={`metric-trend-${trend}`}
            title={`Trend: ${trend}`}
          >
            {trendArrows[trend].split(" ")[0]}
          </span>
        )}
      </div>

      {/* Status Indicator */}
      <div className="mb-3 flex items-center gap-2">
        <div
          className={clsx(
            "h-2 w-2 rounded-full",
            statusIndicators[status],
            status === "critical" && "animate-pulse"
          )}
          data-testid={`metric-status-${status}`}
        />
        <span className="text-xs font-medium uppercase">{status}</span>
      </div>

      {/* Details Section */}
      {details && Object.keys(details).length > 0 && (
        <div
          className="space-y-1 border-t pt-3 opacity-75"
          data-testid="metric-details"
        >
          {Object.entries(details).map(([key, val]) => (
            <div
              key={key}
              className="flex justify-between text-xs"
              data-testid={`metric-detail-${key.toLowerCase()}`}
            >
              <span className="font-medium">{key}:</span>
              <span>{val}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

/**
 * MetricCardSkeleton Component
 *
 * Skeleton loader for metric card while data is loading.
 *
 * @component
 */
const MetricCardSkeleton: React.FC = () => {
  return (
    <div
      className="metric-card rounded-lg border border-gray-200 bg-gray-50 p-4"
      data-testid="metric-card-skeleton"
    >
      {/* Header skeleton */}
      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="h-8 w-8 animate-pulse rounded-full bg-gray-300" />
          <div className="h-4 w-16 animate-pulse rounded bg-gray-300" />
        </div>
        <div className="h-6 w-6 animate-pulse rounded bg-gray-300" />
      </div>

      {/* Value skeleton */}
      <div className="mb-3 flex items-baseline gap-2">
        <div className="h-8 w-20 animate-pulse rounded bg-gray-300" />
        <div className="h-4 w-12 animate-pulse rounded bg-gray-300" />
      </div>

      {/* Status skeleton */}
      <div className="mb-3 flex items-center gap-2">
        <div className="h-2 w-2 animate-pulse rounded-full bg-gray-300" />
        <div className="h-3 w-12 animate-pulse rounded bg-gray-300" />
      </div>

      {/* Details skeleton */}
      <div className="space-y-1 border-t pt-3">
        <div className="flex justify-between">
          <div className="h-3 w-12 animate-pulse rounded bg-gray-300" />
          <div className="h-3 w-16 animate-pulse rounded bg-gray-300" />
        </div>
        <div className="flex justify-between">
          <div className="h-3 w-10 animate-pulse rounded bg-gray-300" />
          <div className="h-3 w-14 animate-pulse rounded bg-gray-300" />
        </div>
      </div>
    </div>
  );
};

/**
 * Export all components and types
 */
export default MetricCard;
export { MetricCardSkeleton };
