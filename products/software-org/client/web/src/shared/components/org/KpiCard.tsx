/**
 * KpiCard Component
 *
 * Key Performance Indicator card for software-org dashboards.
 * Displays metric name, value, trend, and optional status indicator.
 *
 * @example
 * <KpiCard label="Deployment Frequency" value="12" unit="/day" trend={{ value: 2.5, direction: 'up' }} />
 * <KpiCard label="MTTR" value="45" unit="min" status="warning" />
 *
 * @package @ghatana/software-org-web
 */

import React, { CSSProperties } from 'react';
import { clsx } from 'clsx';

export interface KpiTrend {
    /** Trend value (positive or negative) */
    value: number;
    /** Direction: 'up' for improvement, 'down' for decline */
    direction: 'up' | 'down' | 'neutral';
    /** Optional unit suffix (e.g., '%', 'ms') */
    unit?: string;
}

export interface KpiCardProps {
    /** KPI label/name */
    label?: string;
    /** Legacy alias for `label`. */
    title?: string;
    /** Primary KPI value */
    value: string | number;
    /** Optional unit suffix (e.g., 'requests/s', 'ms', '%') */
    unit?: string;
    /** Optional trend indicator (supports shorthand direction strings for compatibility). */
    trend?: KpiTrend | KpiTrend['direction'];
    /** Legacy shorthand value used with string `trend` (e.g., trend="up" trendValue="+2.1"). */
    trendValue?: string | number;
    /** Legacy color hint (accepted for compatibility; currently ignored). */
    color?: string;
    /** Optional status: 'healthy' | 'warning' | 'critical' | 'unknown' (legacy alias: 'error') */
    status?: 'healthy' | 'warning' | 'critical' | 'unknown' | 'error';
    /** Optional description or secondary text */
    description?: string;

    /** Optional leading icon (compatibility). */
    icon?: React.ReactNode;

    /** Legacy visual variant hint (accepted for compatibility; currently ignored). */
    variant?: string;
    /** Optional progress percentage (0-100) for visual indicator */
    progress?: number;
    /** Whether to show progress indicator (MUI-like compatibility). */
    showProgress?: boolean;
    /** Optional target value used to derive progress when `progress` is not provided. */
    target?: number;
    /** Optional comparison value (e.g., 'vs 24h ago: 8') */
    comparison?: string;
    /** Optional size variant */
    size?: 'sm' | 'md' | 'lg';
    /** Optional CSS class */
    className?: string;
    /** Optional inline styles */
    style?: CSSProperties;
    /** Optional callback when card is clicked */
    onClick?: () => void;
    /** Whether to show status indicator */
    showStatus?: boolean;
}

/**
 * Status color mapping for KPI cards.
 */
const STATUS_COLORS: Record<string, { bg: string; text: string; border: string; dot: string }> = {
    healthy: {
        bg: 'bg-emerald-50 dark:bg-emerald-950',
        text: 'text-emerald-700 dark:text-emerald-300',
        border: 'border-emerald-200 dark:border-emerald-800',
        dot: 'bg-emerald-500',
    },
    warning: {
        bg: 'bg-amber-50 dark:bg-amber-950',
        text: 'text-amber-700 dark:text-amber-300',
        border: 'border-amber-200 dark:border-amber-800',
        dot: 'bg-amber-500',
    },
    critical: {
        bg: 'bg-red-50 dark:bg-red-950',
        text: 'text-red-700 dark:text-red-300',
        border: 'border-red-200 dark:border-red-800',
        dot: 'bg-red-500',
    },
    unknown: {
        bg: 'bg-slate-50 dark:bg-slate-900',
        text: 'text-slate-600 dark:text-slate-400',
        border: 'border-slate-200 dark:border-slate-800',
        dot: 'bg-slate-400',
    },
};

/**
 * Trend color and icon mapping.
 */
const TREND_CONFIG: Record<string, { color: string; symbol: string; label: string }> = {
    up: {
        color: 'text-green-600 dark:text-green-400',
        symbol: '↑',
        label: 'increase',
    },
    down: {
        color: 'text-red-600 dark:text-red-400',
        symbol: '↓',
        label: 'decrease',
    },
    neutral: {
        color: 'text-gray-600 dark:text-gray-400',
        symbol: '→',
        label: 'no change',
    },
};

/**
 * Size configuration.
 */
const SIZE_CONFIG = {
    sm: { value: 'text-2xl', label: 'text-xs', padding: 'p-3' },
    md: { value: 'text-3xl', label: 'text-sm', padding: 'p-4' },
    lg: { value: 'text-4xl', label: 'text-base', padding: 'p-6' },
};

/**
 * KpiCard: Displays key performance indicator with trend and status.
 *
 * Features:
 * - Multi-status support (healthy, warning, critical)
 * - Trend indicator with direction
 * - Optional progress bar visualization
 * - Comparison values for context
 * - Multiple size variants
 * - Dark mode support
 * - Full accessibility
 *
 * @param props Component props
 * @returns JSX element
 */
export const KpiCard: React.FC<KpiCardProps> = ({
    label: labelProp,
    title,
    value,
    unit,
    trend,
    trendValue,
    status = 'unknown',
    description,
    icon,
    variant: _variant,
    progress,
    showProgress,
    target,
    comparison,
    size = 'md',
    className,
    style,
    onClick,
    showStatus = true,
}) => {
    const label = labelProp ?? title ?? '';
    const normalizedStatus = status === 'error' ? 'critical' : status;
    const statusConfig = STATUS_COLORS[normalizedStatus] || STATUS_COLORS.unknown;
    const sizeConfig = SIZE_CONFIG[size];

    const resolvedTrend: KpiTrend | undefined =
        typeof trend === 'string'
            ? {
                value:
                    typeof trendValue === 'number'
                        ? trendValue
                        : typeof trendValue === 'string'
                            ? Number.parseFloat(trendValue)
                            : 0,
                direction: trend,
            }
            : trend;

    const resolvedProgress = React.useMemo(() => {
        if (typeof progress === 'number') return progress;
        if (typeof target === 'number' && target > 0) {
            const numericValue = typeof value === 'number' ? value : Number.parseFloat(String(value));
            if (Number.isFinite(numericValue)) {
                return (numericValue / target) * 100;
            }
        }
        return undefined;
    }, [progress, target, value]);

    const shouldShowProgress = showProgress ?? resolvedProgress !== undefined;

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if ((e.key === 'Enter' || e.key === ' ') && onClick) {
            e.preventDefault();
            onClick();
        }
    };

    return (
        <div
            className={clsx(
                'rounded-lg border-2 transition-all duration-200',
                'hover:shadow-md',
                onClick && 'cursor-pointer hover:border-opacity-80',
                statusConfig.border,
                statusConfig.bg,
                sizeConfig.padding,
                className
            )}
            onClick={onClick}
            onKeyDown={handleKeyDown}
            role={onClick ? 'button' : 'region'}
            tabIndex={onClick ? 0 : -1}
            aria-label={`${label}: ${value}${unit ? ' ' + unit : ''}`}
            style={style}
        >
            {/* Header with label and status dot */}
            <div className="flex items-start justify-between mb-3">
                <span className={clsx('font-semibold truncate flex items-center gap-2', sizeConfig.label, statusConfig.text)}>
                    {icon ? <span aria-hidden="true" className="inline-flex">{icon}</span> : null}
                    {label}
                </span>
                {showStatus && (
                    <div className="flex-shrink-0 w-2 h-2 rounded-full ml-2" style={{ backgroundColor: statusConfig.dot.split('-')[1] }} />
                )}
            </div>

            {/* Main value */}
            <div className="flex items-baseline gap-1 mb-2">
                <span className={clsx('font-bold tracking-tight dark:text-gray-100', sizeConfig.value)}>
                    {value}
                </span>
                {unit && <span className="text-sm text-gray-500 dark:text-gray-400">{unit}</span>}
            </div>

            {/* Trend indicator */}
            {resolvedTrend && TREND_CONFIG[resolvedTrend.direction] && (
                <div className={clsx('flex items-center gap-1 mb-2', TREND_CONFIG[resolvedTrend.direction].color)}>
                    <span className="text-sm font-medium">{TREND_CONFIG[resolvedTrend.direction].symbol}</span>
                    <span className="text-sm font-medium">{resolvedTrend.value.toFixed(1)}</span>
                    {resolvedTrend.unit && <span className="text-xs">{resolvedTrend.unit}</span>}
                </div>
            )}

            {/* Progress bar (optional) */}
            {shouldShowProgress && resolvedProgress !== undefined && (
                <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2 mb-2 overflow-hidden">
                    <div
                        className={clsx('h-full transition-all duration-300 rounded-full', statusConfig.text)}
                        style={{ width: `${Math.min(Math.max(resolvedProgress, 0), 100)}%` }}
                        role="progressbar"
                        aria-valuenow={resolvedProgress}
                        aria-valuemin={0}
                        aria-valuemax={100}
                    />
                </div>
            )}

            {/* Comparison value */}
            {comparison && (
                <p className="text-xs text-gray-600 dark:text-gray-400 mb-1">{comparison}</p>
            )}

            {/* Description */}
            {description && (
                <p className="text-xs text-gray-600 dark:text-gray-400">{description}</p>
            )}
        </div>
    );
};

export default KpiCard;
