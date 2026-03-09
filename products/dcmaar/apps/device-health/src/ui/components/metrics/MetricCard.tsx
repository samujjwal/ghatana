/**
 * @fileoverview Metric Card Component
 *
 * Reusable metric card with status indicators, trends,
 * and interactive features for analytics dashboard.
 *
 * @module ui/components/metrics
 * @since 2.0.0
 */

import React, { useMemo } from 'react';
import { MetricTooltip } from '../common/MetricTooltip';
import { getMetricGlossaryEntry } from '../../../analytics/metrics/MetricGlossary';
import { getTopActions } from '../../../analytics/guidance/ActionPlaybooks';
import { useGuidance } from '../../context/GuidanceContext';

export interface MetricCardProps {
  title: string;
  value?: number;
  unit?: string;
  metricKey?: string;
  targetThreshold?: number;
  children?: React.ReactNode;
  status?: 'good' | 'warning' | 'poor' | 'unknown';
  trend?: {
    direction: 'up' | 'down' | 'stable';
    percentage: number;
    significance: 'high' | 'medium' | 'low';
  };
  precision?: number;
  icon?: React.ReactNode;
  onClick?: () => void;
  loading?: boolean;
  error?: string;
}

const statusColors: Record<NonNullable<MetricCardProps['status']>, string> = {
  good: 'text-emerald-600 bg-emerald-50 border-emerald-200',
  warning: 'text-amber-600 bg-amber-50 border-amber-200',
  poor: 'text-rose-600 bg-rose-50 border-rose-200',
  unknown: 'text-slate-600 bg-slate-50 border-slate-200',
};

const trendIcons: Record<'up' | 'down' | 'stable', string> = {
  up: '↗',
  down: '↘',
  stable: '→',
};

const trendColors: Record<'up' | 'down' | 'stable', string> = {
  up: 'text-emerald-600',
  down: 'text-rose-600',
  stable: 'text-slate-600',
};

/**
 * Metric Card Component
 *
 * Displays a single metric with value, status, trend, and interactive features.
 */
export const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  unit,
  status = 'unknown',
  trend,
  precision = 1,
  icon,
  metricKey,
  targetThreshold,
  children,
  onClick,
  loading = false,
  error,
}) => {
  const { openGuidance } = useGuidance();
  const glossaryEntry = metricKey ? getMetricGlossaryEntry(metricKey) : undefined;
  const resolvedUnit = unit ?? glossaryEntry?.unit ?? '';
  const severityForQuickFix: 'warning' | 'critical' | undefined = useMemo(() => {
    if (!metricKey) return undefined;
    if (status === 'poor') return 'critical';
    if (status === 'warning') return 'warning';
    return undefined;
  }, [metricKey, status]);

  const quickFixActions = useMemo(() => {
    if (!metricKey || !severityForQuickFix) return [];
    return getTopActions(metricKey, severityForQuickFix, 2);
  }, [metricKey, severityForQuickFix]);

  const formatValue = (val?: number): string => {
    if (typeof val !== 'number' || !Number.isFinite(val)) return '—';
    return val.toFixed(precision);
  };

  const formatThresholdValue = (val?: number): string => {
    if (typeof val !== 'number' || !Number.isFinite(val)) return '—';
    if (Number.isInteger(val)) {
      return val.toLocaleString();
    }
    if (Math.abs(val) < 1) {
      return val.toFixed(2);
    }
    if (Math.abs(val) < 10) {
      return val.toFixed(2);
    }
    if (Math.abs(val) < 100) {
      return val.toFixed(1);
    }
    return val.toFixed(0);
  };

  const titleContent = glossaryEntry ? (
    <MetricTooltip metricKey={metricKey!}>
      <span className="flex items-center gap-1">
        <span>{title}</span>
        <span aria-hidden className="text-xs text-slate-400">ℹ️</span>
        <span className="sr-only">Learn about {glossaryEntry.fullName}</span>
      </span>
    </MetricTooltip>
  ) : (
    <span>{title}</span>
  );

  const targetValue =
    typeof targetThreshold === 'number'
      ? targetThreshold
      : glossaryEntry?.goodThreshold;

  const direction = glossaryEntry?.direction ?? 'lower-is-better';
  const comparisonSymbol = direction === 'higher-is-better' ? '≥' : '≤';

  const getTrendColor = (direction: 'up' | 'down' | 'stable'): string => {
    // For some metrics, up is good (e.g., engagement), for others, down is good (e.g., load time)
    // This could be made configurable based on metric type
    return trendColors[direction];
  };

  const content = (
    <div className={`relative rounded-lg border p-4 transition-all duration-200 ${
      statusColors[status]
    } ${onClick ? 'cursor-pointer hover:shadow-md' : ''} ${
      loading ? 'opacity-50' : ''
    }`}
    onClick={onClick}
    >
      {/* Loading overlay */}
      {loading && (
        <div className="absolute inset-0 flex items-center justify-center bg-white bg-opacity-75 rounded-lg">
          <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-slate-400"></div>
        </div>
      )}

      {/* Header */}
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          {icon && <span className="text-slate-400">{icon}</span>}
          <h3 className="text-sm font-medium text-slate-700">{titleContent}</h3>
        </div>
        
        {trend && (
          <div className={`flex items-center gap-1 text-xs font-medium ${getTrendColor(trend.direction)}`}>
            <span>{trendIcons[trend.direction]}</span>
            <span>{Math.abs(trend.percentage).toFixed(1)}%</span>
          </div>
        )}
      </div>

      {/* Value */}
      <div className="flex items-baseline gap-1">
        <span className="text-2xl font-bold text-slate-900">
          {formatValue(value)}
        </span>
        {resolvedUnit && (
          <span className="text-sm text-slate-500">{resolvedUnit}</span>
        )}
      </div>

      {targetValue !== undefined && Number.isFinite(targetValue) && (
        <div className="mt-2 text-xs text-slate-500">
          Target: {comparisonSymbol} {formatThresholdValue(targetValue)}
          {resolvedUnit && <span className="ml-1">{resolvedUnit}</span>}
        </div>
      )}

      {/* Status indicator */}
      <div className="mt-2 flex items-center gap-2">
        <div className={`h-2 w-2 rounded-full ${
          status === 'good' ? 'bg-emerald-500' :
          status === 'warning' ? 'bg-amber-500' :
          status === 'poor' ? 'bg-rose-500' :
          'bg-slate-400'
        }`}></div>
        <span className="text-xs text-slate-500 capitalize">{status}</span>
      </div>

      {/* Error message */}
      {error && (
        <div className="mt-2 text-xs text-rose-600">
          {error}
        </div>
      )}

      {/* Quick fixes */}
      {metricKey && severityForQuickFix && quickFixActions.length > 0 && (
        <div className="mt-3">
          <details className="group rounded-lg border border-blue-100 bg-blue-50/60 text-sm text-blue-900 transition">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-2 rounded-lg px-3 py-2 font-medium text-blue-700 hover:bg-blue-100 group-open:bg-blue-100">
              <span>Quick fixes</span>
              <span className="text-xs uppercase tracking-wide text-blue-500">
                {severityForQuickFix === 'critical' ? 'Critical' : 'Warning'}
              </span>
            </summary>
            <div className="space-y-3 border-t border-blue-100 px-3 py-3">
              {quickFixActions.map((action) => (
                <div key={action.id} className="space-y-1 rounded-md bg-white/70 p-2 shadow-sm">
                  <div className="text-sm font-semibold text-blue-900">{action.title}</div>
                  <div className="text-xs text-blue-700">
                    {action.expectedImprovement} • {action.timeToImplement}
                  </div>
                  <p className="text-xs text-blue-800">{action.description}</p>
                </div>
              ))}
              <button
                type="button"
                className="text-sm font-medium text-blue-600 hover:text-blue-700"
                onClick={() =>
                  openGuidance({
                    metric: metricKey,
                    severity: severityForQuickFix,
                    currentValue: typeof value === 'number' ? value : undefined,
                    source: 'metric-card',
                  })
                }
              >
                View all fixes →
              </button>
            </div>
          </details>
        </div>
      )}

      {children && <div className="mt-3">{children}</div>}
    </div>
  );

  return content;
};

export default MetricCard;
