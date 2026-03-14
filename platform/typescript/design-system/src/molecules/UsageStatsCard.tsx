/**
 * UsageStatsCard Component - Display app usage statistics
 *
 * Shows aggregated usage data for a single app or across all apps:
 * - Current usage time (today)
 * - Average usage time
 * - Peak usage hours
 * - Trend visualization
 * - Comparison with previous period
 *
 * Used in dashboards, app detail screens, and analytics views.
 *
 * @doc.type component
 * @doc.purpose Display aggregated usage statistics with trend indicators
 * @doc.layer product
 * @doc.pattern Molecule
 */

import React, { useMemo } from 'react';
import { cn } from '@ghatana/utils';
import {
  palette,
  lightColors,
  darkColors,
  componentRadius,
  fontSize,
  fontWeight,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';

/**
 * Usage metrics data structure
 */
export interface UsageMetrics {
  /** Usage time in seconds for today/current period */
  currentUsage: number;
  /** Average usage time in seconds */
  averageUsage: number;
  /** Peak usage hour (0-23) */
  peakHour?: number;
  /** Usage comparison percentage (-100 to 100) */
  comparisonPercentage?: number;
  /** Hourly breakdown (24 values, 0-100 scale) */
  hourlyData?: number[];
  /** Previous period usage in seconds (for comparison) */
  previousUsage?: number;
}

/**
 * Props for UsageStatsCard component
 */
export interface UsageStatsCardProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Card title */
  title: string;
  /** Usage metrics data */
  metrics: UsageMetrics;
  /** Icon or avatar */
  icon?: React.ReactNode;
  /** Visual tone/color */
  tone?: 'neutral' | 'primary' | 'success' | 'warning' | 'danger';
  /** Show trend visualization */
  showTrend?: boolean;
  /** Compact mode */
  compact?: boolean;
}

/**
 * Format duration in seconds to readable string
 */
function formatDuration(seconds: number): string {
  if (seconds <= 0) return '0s';
  if (seconds < 60) return `${Math.round(seconds)}s`;
  if (seconds < 3600) return `${Math.round(seconds / 60)}m`;
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.round((seconds % 3600) / 60);
  if (minutes === 0) return `${hours}h`;
  return `${hours}h ${minutes}m`;
}

/**
 * Get tone color palette
 */
function getTonePalette(tone: string): unknown {
  const tonePalettes: Record<string, unknown> = {
    neutral: palette.gray,
    primary: palette.primary,
    success: palette.success,
    warning: palette.warning,
    danger: palette.error,
  };
  return tonePalettes[tone] || palette.gray;
}

/**
 * Get trend direction and color based on comparison
 */
function getTrendInfo(comparisonPercentage?: number): { direction: string; color: string } {
  if (!comparisonPercentage) {
    return { direction: '→', color: '#6b7280' };
  }
  if (comparisonPercentage > 0) {
    return { direction: '↑', color: '#ef4444' };
  }
  if (comparisonPercentage < 0) {
    return { direction: '↓', color: '#10b981' };
  }
  return { direction: '→', color: '#6b7280' };
}

/**
 * UsageStatsCard - Molecule for displaying usage statistics
 */
export const UsageStatsCard = React.forwardRef<HTMLDivElement, UsageStatsCardProps>(
  (
    {
      title,
      metrics,
      icon,
      tone = 'neutral',
      showTrend = true,
      compact = false,
      className,
      ...props
    },
    ref
  ) => {
    const { resolvedTheme } = useTheme();
    const isDark = resolvedTheme === 'dark';
    const surface = isDark ? darkColors : lightColors;
    const tonePalette = getTonePalette(tone);
    const mainColor = tonePalette[500];

    const padding = compact ? '12px 16px' : '16px 20px';
    const heightClass = compact ? 'min-h-24' : 'min-h-32';

    const trend = useMemo(
      () => getTrendInfo(metrics.comparisonPercentage),
      [metrics.comparisonPercentage]
    );

    const trendLabel = useMemo(() => {
      const percentage = metrics.comparisonPercentage;
      if (!percentage) return '';
      return `${Math.abs(percentage)}% ${percentage > 0 ? 'more' : 'less'} than last period`;
    }, [metrics.comparisonPercentage]);

    // Calculate max hourly value for bar chart scaling
    const maxHourlyValue = useMemo(
      () => (metrics.hourlyData ? Math.max(...metrics.hourlyData) : 100),
      [metrics.hourlyData]
    );

    return (
      <div
        ref={ref}
        className={cn(
          'rounded-lg border overflow-hidden transition-colors',
          'bg-white dark:bg-gray-800',
          'border-gray-200 dark:border-gray-700',
          heightClass,
          className
        )}
        style={{
          padding,
          backgroundColor: surface.background.elevated,
          borderColor: surface.border,
        } as React.CSSProperties}
        {...props}
      >
        {/* Header with title and icon */}
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            {icon && <div className="flex-shrink-0">{icon}</div>}
            <h3
              className="font-semibold"
              style={{
                color: surface.text.primary,
                fontSize: '14px',
                fontWeight: 600,
              }}
            >
              {title}
            </h3>
          </div>
          {showTrend && metrics.comparisonPercentage !== undefined && (
            <div
              className="flex items-center gap-1 text-sm"
              style={{
                color: trend.color,
              }}
            >
              <span>{trend.direction}</span>
              <span>{Math.abs(metrics.comparisonPercentage)}%</span>
            </div>
          )}
        </div>

        {/* Main stats grid */}
        <div className="grid grid-cols-2 gap-4 mb-4">
          {/* Current Usage */}
          <div>
            <p
              className="text-xs opacity-75 mb-1"
              style={{
                color: surface.text.primary,
                fontSize: '11px',
                textTransform: 'uppercase',
                letterSpacing: '0.5px',
              }}
            >
              Current
            </p>
            <p
              className="text-lg font-bold"
              style={{
                color: mainColor,
                fontSize: '20px',
                fontWeight: 700,
              }}
            >
              {formatDuration(metrics.currentUsage)}
            </p>
          </div>

          {/* Average Usage */}
          <div>
            <p
              className="text-xs opacity-75 mb-1"
              style={{
                color: surface.text.primary,
                fontSize: '11px',
                textTransform: 'uppercase',
                letterSpacing: '0.5px',
              }}
            >
              Average
            </p>
            <p
              className="text-lg font-bold"
              style={{
                color: surface.text.primary,
                fontSize: '20px',
                fontWeight: 700,
                opacity: 0.7,
              }}
            >
              {formatDuration(metrics.averageUsage)}
            </p>
          </div>
        </div>

        {/* Hourly breakdown chart (if available) */}
        {metrics.hourlyData && metrics.hourlyData.length > 0 && (
          <div>
            <p
              className="text-xs opacity-75 mb-2"
              style={{
                color: surface.text.primary,
                fontSize: '11px',
                textTransform: 'uppercase',
                letterSpacing: '0.5px',
              }}
            >
              24h Activity
            </p>
            <div className="flex items-end justify-between gap-0.5 h-8">
              {metrics.hourlyData.map((value, idx) => (
                <div
                  key={idx}
                  className="flex-1 rounded-t transition-colors"
                  style={{
                    height: `${Math.max((value / maxHourlyValue) * 100, 4)}%`,
                    backgroundColor:
                      value > 0
                        ? mainColor
                        : isDark
                          ? 'rgba(255, 255, 255, 0.1)'
                          : 'rgba(0, 0, 0, 0.05)',
                    minHeight: '2px',
                  }}
                  title={`Hour ${idx}: ${Math.round(value)}`}
                />
              ))}
            </div>
          </div>
        )}

        {/* Trend label */}
        {showTrend && trendLabel && (
          <p
            className="text-xs mt-3 opacity-60"
            style={{
              color: surface.text.primary,
              fontSize: '11px',
            }}
          >
            {trendLabel}
          </p>
        )}
      </div>
    );
  }
);

UsageStatsCard.displayName = 'UsageStatsCard';

export default UsageStatsCard;
