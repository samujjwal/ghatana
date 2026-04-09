/**
 * KPICard Component
 *
 * Displays a key performance indicator with label, value, trend, and optional icon.
 *
 * @doc.type component
 * @doc.purpose Display key performance indicator metric
 * @doc.layer product
 * @doc.pattern Component
 */
import React from 'react';

type Trend = 'up' | 'down' | 'neutral';

interface KPICardProps {
  label: string;
  value: number;
  trend?: Trend;
  changePercent?: number;
  isLoading?: boolean;
  icon?: string;
  onClick?: () => void;
}

/**
 * Format large numbers: 1234567 -> "1.2M", 12345 -> "12.3K", etc.
 */
function formatValue(value: number): string {
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1)}M`;
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(1)}K`;
  }
  return String(value);
}

/**
 * KPICard displays a metric value with trend indicator.
 */
export const KPICard: React.FC<KPICardProps> = ({
  label,
  value,
  trend = 'neutral',
  changePercent,
  isLoading = false,
  icon,
  onClick,
}) => {
  if (isLoading) {
    return (
      <div data-testid="kpi-skeleton" className="animate-pulse rounded-lg bg-gray-100 p-4">
        <div className="h-4 w-24 rounded bg-gray-300 mb-2" />
        <div className="h-8 w-16 rounded bg-gray-300" />
      </div>
    );
  }

  const trendColor =
    trend === 'up' ? 'text-green-600' : trend === 'down' ? 'text-red-600' : 'text-gray-500';

  const formattedChange =
    changePercent !== undefined
      ? changePercent >= 0
        ? `+${changePercent}%`
        : `${changePercent}%`
      : null;

  return (
    <div
      className="rounded-lg border bg-white p-4 shadow-sm"
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      {icon && (
        <span data-testid={`kpi-icon-${icon}`} className="mb-2 block text-gray-400" />
      )}
      <p className="text-sm text-gray-500">{label}</p>
      <p className="text-2xl font-bold">{formatValue(value)}</p>
      {trend !== 'neutral' && formattedChange && (
        <div className={trendColor}>
          {trend === 'up' && (
            <span data-testid="trend-up-icon" aria-hidden="true">↑</span>
          )}
          {trend === 'down' && (
            <span data-testid="trend-down-icon" aria-hidden="true">↓</span>
          )}
          <span>{formattedChange}</span>
        </div>
      )}
    </div>
  );
};

export default KPICard;
