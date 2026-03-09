/**
 * @fileoverview Stats Card Component
 * 
 * Reusable card for displaying statistics with icons and trends.
 */

import React from 'react';

export interface StatsCardProps {
    /** Card title */
    title: string;
    /** Main value to display */
    value: string | number;
    /** Optional subtitle or description */
    subtitle?: string;
    /** Icon (emoji or component) */
    icon?: React.ReactNode;
    /** Trend percentage (positive = up, negative = down) */
    trend?: number;
    /** Trend label */
    trendLabel?: string;
    /** Card color variant */
    variant?: 'default' | 'success' | 'warning' | 'danger' | 'info';
    /** Click handler */
    onClick?: () => void;
}

/**
 * Variant styles
 */
const VARIANT_STYLES = {
    default: {
        bg: 'bg-white dark:bg-gray-800',
        iconBg: 'bg-gray-100 dark:bg-gray-700',
        text: 'text-gray-900 dark:text-white',
    },
    success: {
        bg: 'bg-green-50 dark:bg-green-900/20',
        iconBg: 'bg-green-100 dark:bg-green-800/40',
        text: 'text-green-900 dark:text-green-100',
    },
    warning: {
        bg: 'bg-yellow-50 dark:bg-yellow-900/20',
        iconBg: 'bg-yellow-100 dark:bg-yellow-800/40',
        text: 'text-yellow-900 dark:text-yellow-100',
    },
    danger: {
        bg: 'bg-red-50 dark:bg-red-900/20',
        iconBg: 'bg-red-100 dark:bg-red-800/40',
        text: 'text-red-900 dark:text-red-100',
    },
    info: {
        bg: 'bg-blue-50 dark:bg-blue-900/20',
        iconBg: 'bg-blue-100 dark:bg-blue-800/40',
        text: 'text-blue-900 dark:text-blue-100',
    },
};

/**
 * StatsCard
 * 
 * Displays a statistic with optional icon and trend indicator.
 */
export function StatsCard({
    title,
    value,
    subtitle,
    icon,
    trend,
    trendLabel,
    variant = 'default',
    onClick,
}: StatsCardProps) {
    const styles = VARIANT_STYLES[variant];
    const isClickable = !!onClick;

    return (
        <div
            className={`
        ${styles.bg} rounded-xl p-4 border border-gray-200 dark:border-gray-700
        ${isClickable ? 'cursor-pointer hover:shadow-md transition-shadow' : ''}
      `}
            onClick={onClick}
            role={isClickable ? 'button' : undefined}
            tabIndex={isClickable ? 0 : undefined}
        >
            <div className="flex items-start justify-between">
                <div className="flex-1">
                    <p className="text-sm font-medium text-gray-500 dark:text-gray-400">
                        {title}
                    </p>
                    <p className={`text-2xl font-bold mt-1 ${styles.text}`}>
                        {value}
                    </p>
                    {subtitle && (
                        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                            {subtitle}
                        </p>
                    )}
                </div>
                {icon && (
                    <div className={`${styles.iconBg} p-3 rounded-lg`}>
                        <span className="text-2xl">{icon}</span>
                    </div>
                )}
            </div>

            {/* Trend indicator */}
            {trend !== undefined && (
                <div className="mt-3 flex items-center gap-1">
                    {trend > 0 ? (
                        <span className="text-green-500">↑</span>
                    ) : trend < 0 ? (
                        <span className="text-red-500">↓</span>
                    ) : (
                        <span className="text-gray-400">→</span>
                    )}
                    <span
                        className={`text-sm font-medium ${trend > 0
                                ? 'text-green-600 dark:text-green-400'
                                : trend < 0
                                    ? 'text-red-600 dark:text-red-400'
                                    : 'text-gray-500 dark:text-gray-400'
                            }`}
                    >
                        {Math.abs(trend)}%
                    </span>
                    {trendLabel && (
                        <span className="text-sm text-gray-500 dark:text-gray-400">
                            {trendLabel}
                        </span>
                    )}
                </div>
            )}
        </div>
    );
}

/**
 * StatsCardGrid
 * 
 * Grid container for stats cards.
 */
export function StatsCardGrid({ children }: { children: React.ReactNode }) {
    return (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {children}
        </div>
    );
}
