/**
 * MetricCard Component
 *
 * @description Displays a single metric with value, trend, and optional sparkline
 * for the operations dashboard.
 *
 * @doc.type component
 * @doc.purpose Single metric display
 * @doc.layer presentation
 * @doc.phase 4
 */

import React, { useMemo } from 'react';
import { cn } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

export type TrendDirection = 'up' | 'down' | 'flat';
export type MetricFormat = 'number' | 'percentage' | 'bytes' | 'duration' | 'currency';

export interface SparklinePoint {
  timestamp: number;
  value: number;
}

export interface MetricCardProps {
  title: string;
  value: number | string;
  format?: MetricFormat;
  unit?: string;
  trend?: {
    direction: TrendDirection;
    value: number;
    isPositive?: boolean;
  };
  sparkline?: SparklinePoint[];
  icon?: React.ReactNode;
  threshold?: {
    warning: number;
    critical: number;
  };
  onClick?: () => void;
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const formatValue = (value: number | string, format?: MetricFormat, unit?: string): string => {
  if (typeof value === 'string') return value;

  switch (format) {
    case 'percentage':
      return `${value.toFixed(1)}%`;
    case 'bytes':
      if (value >= 1e9) return `${(value / 1e9).toFixed(2)} GB`;
      if (value >= 1e6) return `${(value / 1e6).toFixed(2)} MB`;
      if (value >= 1e3) return `${(value / 1e3).toFixed(2)} KB`;
      return `${value} B`;
    case 'duration':
      if (value >= 3600000) return `${(value / 3600000).toFixed(1)}h`;
      if (value >= 60000) return `${(value / 60000).toFixed(1)}m`;
      if (value >= 1000) return `${(value / 1000).toFixed(1)}s`;
      return `${value}ms`;
    case 'currency':
      return `$${value.toLocaleString()}`;
    default:
      if (value >= 1e6) return `${(value / 1e6).toFixed(1)}M`;
      if (value >= 1e3) return `${(value / 1e3).toFixed(1)}K`;
      return value.toLocaleString();
  }
};

const getTrendConfig = (direction: TrendDirection, isPositive?: boolean) => {
  const configs = {
    up: { icon: '↑', defaultPositive: true },
    down: { icon: '↓', defaultPositive: false },
    flat: { icon: '→', defaultPositive: true },
  };

  const config = configs[direction];
  const positive = isPositive ?? config.defaultPositive;

  return {
    icon: config.icon,
    color: positive ? '#10B981' : '#EF4444',
  };
};

const getThresholdStatus = (
  value: number | string,
  threshold?: { warning: number; critical: number }
): 'normal' | 'warning' | 'critical' => {
  if (!threshold || typeof value === 'string') return 'normal';
  if (value >= threshold.critical) return 'critical';
  if (value >= threshold.warning) return 'warning';
  return 'normal';
};

// ============================================================================
// Sparkline Sub-component
// ============================================================================

interface SparklineProps {
  data: SparklinePoint[];
  width?: number;
  height?: number;
  color?: string;
}

const Sparkline: React.FC<SparklineProps> = ({
  data,
  width = 80,
  height = 24,
  color = '#3B82F6',
}) => {
  const path = useMemo(() => {
    if (data.length < 2) return '';

    const values = data.map((d) => d.value);
    const minVal = Math.min(...values);
    const maxVal = Math.max(...values);
    const range = maxVal - minVal || 1;

    const points = data.map((d, i) => {
      const x = (i / (data.length - 1)) * width;
      const y = height - ((d.value - minVal) / range) * height;
      return `${x},${y}`;
    });

    return `M${points.join(' L')}`;
  }, [data, width, height]);

  if (data.length < 2) return null;

  return (
    <svg width={width} height={height} className="sparkline">
      <path
        d={path}
        fill="none"
        stroke={color}
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
};

// ============================================================================
// Component
// ============================================================================

export const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  format,
  unit,
  trend,
  sparkline,
  icon,
  threshold,
  onClick,
  className,
}) => {
  const formattedValue = formatValue(value, format, unit);
  const thresholdStatus = getThresholdStatus(value, threshold);
  const trendConfig = trend ? getTrendConfig(trend.direction, trend.isPositive) : null;

  return (
    <div
      className={cn(
        'metric-card',
        onClick && 'metric-card--clickable',
        `metric-card--${thresholdStatus}`,
        className
      )}
      onClick={onClick}
      onKeyDown={(e) => {
        if (onClick && (e.key === 'Enter' || e.key === ' ')) {
          e.preventDefault();
          onClick();
        }
      }}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      {/* Header */}
      <div className="metric-header">
        {icon && <span className="metric-icon">{icon}</span>}
        <span className="metric-title">{title}</span>
      </div>

      {/* Value */}
      <div className="metric-value-row">
        <span className="metric-value">
          {formattedValue}
          {unit && <span className="metric-unit">{unit}</span>}
        </span>
        {sparkline && sparkline.length > 0 && (
          <Sparkline
            data={sparkline}
            color={thresholdStatus === 'critical' ? '#EF4444' : thresholdStatus === 'warning' ? '#F59E0B' : '#3B82F6'}
          />
        )}
      </div>

      {/* Trend */}
      {trend && (
        <div className="metric-trend" style={{ color: trendConfig?.color }}>
          <span className="trend-icon">{trendConfig?.icon}</span>
          <span className="trend-value">{trend.value}%</span>
          <span className="trend-label">vs last period</span>
        </div>
      )}

      <style>{`
        .metric-card {
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 12px;
          padding: 1rem;
          transition: all 0.2s ease;
        }

        .metric-card--clickable {
          cursor: pointer;
        }

        .metric-card--clickable:hover {
          border-color: #3B82F6;
          box-shadow: 0 2px 8px rgba(59, 130, 246, 0.1);
        }

        .metric-card--warning {
          border-left: 4px solid #F59E0B;
        }

        .metric-card--critical {
          border-left: 4px solid #EF4444;
        }

        .metric-header {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          margin-bottom: 0.5rem;
        }

        .metric-icon {
          font-size: 1rem;
          opacity: 0.7;
        }

        .metric-title {
          font-size: 0.75rem;
          font-weight: 500;
          color: #6B7280;
          text-transform: uppercase;
          letter-spacing: 0.025em;
        }

        .metric-value-row {
          display: flex;
          align-items: flex-end;
          justify-content: space-between;
          gap: 0.5rem;
        }

        .metric-value {
          font-size: 1.5rem;
          font-weight: 700;
          color: #111827;
          line-height: 1.2;
        }

        .metric-unit {
          font-size: 0.875rem;
          font-weight: 500;
          color: #6B7280;
          margin-left: 0.25rem;
        }

        .metric-trend {
          display: flex;
          align-items: center;
          gap: 0.25rem;
          margin-top: 0.5rem;
          font-size: 0.75rem;
        }

        .trend-icon {
          font-weight: 600;
        }

        .trend-value {
          font-weight: 600;
        }

        .trend-label {
          color: #9CA3AF;
          margin-left: 0.25rem;
        }

        .sparkline {
          flex-shrink: 0;
        }

        @media (prefers-color-scheme: dark) {
          .metric-card {
            background: #1F2937;
            border-color: #374151;
          }

          .metric-card--clickable:hover {
            border-color: #60A5FA;
            box-shadow: 0 2px 8px rgba(96, 165, 250, 0.15);
          }

          .metric-title {
            color: #9CA3AF;
          }

          .metric-value {
            color: #F9FAFB;
          }

          .metric-unit {
            color: #9CA3AF;
          }

          .trend-label {
            color: #6B7280;
          }
        }
      `}</style>
    </div>
  );
};

MetricCard.displayName = 'MetricCard';

export default MetricCard;
