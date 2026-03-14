import React from 'react';
import { clsx } from 'clsx';

export interface MetricCardProps {
    /**
     * Metric label
     */
    label: string;
    /**
     * Metric value
     */
    value: string | number;
    /**
     * Optional icon
     */
    icon?: React.ReactNode;
    /**
     * Trend indicator
     */
    trend?: {
        value: number;
        direction: 'up' | 'down';
        label?: string;
    };
    /**
     * Visual variant
     * @default 'default'
     */
    variant?: 'default' | 'success' | 'warning' | 'error' | 'info';
    /**
     * Optional description
     */
    description?: string;
    /**
     * Additional CSS classes
     */
    className?: string;
}

/**
 * MetricCard component for displaying key metrics and KPIs.
 *
 * @example
 * ```tsx
 * <MetricCard
 *   label="Total Revenue"
 *   value="$45,231"
 *   trend={{ value: 12.5, direction: 'up', label: 'vs last month' }}
 *   icon={<DollarIcon />}
 *   variant="success"
 * />
 * ```
 */
export const MetricCard: React.FC<MetricCardProps> = ({
    label,
    value,
    icon,
    trend,
    variant = 'default',
    description,
    className,
}) => {
    const variantClasses = {
        default: 'border-gray-200',
        success: 'border-success-200 bg-success-50',
        warning: 'border-warning-200 bg-warning-50',
        error: 'border-error-200 bg-error-50',
        info: 'border-info-200 bg-info-50',
    };

    const trendColors = {
        up: 'text-success-600',
        down: 'text-error-600',
    };

    return (
        <div
            className={clsx(
                'rounded-lg border bg-white p-6 shadow-sm',
                variantClasses[variant],
                className
            )}
        >
            <div className="flex items-start justify-between">
                <div className="flex-1">
                    <p className="text-sm font-medium text-gray-600">{label}</p>
                    <p className="mt-2 text-3xl font-bold text-gray-900">{value}</p>
                    {description && (
                        <p className="mt-1 text-sm text-gray-500">{description}</p>
                    )}
                </div>
                {icon && (
                    <div className="ml-4 flex-shrink-0 text-gray-400">{icon}</div>
                )}
            </div>

            {trend && (
                <div className="mt-4 flex items-center text-sm">
                    <span
                        className={clsx(
                            'flex items-center font-medium',
                            trendColors[trend.direction]
                        )}
                    >
                        {trend.direction === 'up' ? '↑' : '↓'} {Math.abs(trend.value)}%
                    </span>
                    {trend.label && (
                        <span className="ml-2 text-gray-500">{trend.label}</span>
                    )}
                </div>
            )}
        </div>
    );
};

MetricCard.displayName = 'MetricCard';
