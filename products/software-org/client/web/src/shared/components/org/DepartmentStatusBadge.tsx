/**
 * DepartmentStatusBadge Component
 *
 * Displays department health/status with color-coded indicators.
 * Uses design tokens for consistency across org products.
 *
 * @example
 * <DepartmentStatusBadge status="active" label="Engineering" />
 * <DepartmentStatusBadge status="warning" label="QA" count={3} />
 *
 * @package @ghatana/software-org-web
 */

import React, { CSSProperties } from 'react';
import { clsx } from 'clsx';

export interface DepartmentStatusBadgeProps {
    /** Department status: 'active' | 'warning' | 'critical' | 'idle' | 'unknown' */
    status: 'active' | 'warning' | 'critical' | 'idle' | 'unknown';
    /** Department name or label */
    label: string;
    /** Optional count indicator (alerts, tasks, etc.) */
    count?: number;
    /** Optional additional CSS class */
    className?: string;
    /** Optional inline styles */
    style?: CSSProperties;
    /** Optional callback when badge is clicked */
    onClick?: () => void;
    /** Optional aria-label for accessibility */
    ariaLabel?: string;
    /** Whether to show a pulse animation (active departments) */
    animate?: boolean;
}

/**
 * Color mapping for department status states.
 * Uses design tokens from @ghatana/tokens.
 */
const STATUS_COLORS: Record<string, { bg: string; text: string; border: string; dot: string }> = {
    active: {
        bg: 'bg-green-50 dark:bg-green-950',
        text: 'text-green-700 dark:text-green-300',
        border: 'border-green-200 dark:border-green-800',
        dot: 'bg-green-500',
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
    idle: {
        bg: 'bg-gray-50 dark:bg-gray-900',
        text: 'text-gray-600 dark:text-gray-400',
        border: 'border-gray-200 dark:border-gray-800',
        dot: 'bg-gray-400',
    },
    unknown: {
        bg: 'bg-slate-50 dark:bg-slate-950',
        text: 'text-slate-600 dark:text-slate-400',
        border: 'border-slate-200 dark:border-slate-800',
        dot: 'bg-slate-400',
    },
};

/**
 * DepartmentStatusBadge: Displays org department status with visual indicators.
 *
 * Features:
 * - Multi-status support (active, warning, critical, idle, unknown)
 * - Optional count indicator for alerts/tasks
 * - Pulsing animation for active departments
 * - Full accessibility (ARIA labels, keyboard support)
 * - Dark mode support via Tailwind
 *
 * @param props Component props
 * @returns JSX element
 */
export const DepartmentStatusBadge: React.FC<DepartmentStatusBadgeProps> = ({
    status,
    label,
    count,
    className,
    style,
    onClick,
    ariaLabel,
    animate = true,
}) => {
    const colors = STATUS_COLORS[status] || STATUS_COLORS.unknown;

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if ((e.key === 'Enter' || e.key === ' ') && onClick) {
            e.preventDefault();
            onClick();
        }
    };

    return (
        <div
            className={clsx(
                'inline-flex items-center gap-2 px-3 py-1.5 rounded-full',
                'border transition-all duration-200',
                'cursor-pointer hover:shadow-sm',
                colors.bg,
                colors.text,
                colors.border,
                className
            )}
            onClick={onClick}
            onKeyDown={handleKeyDown}
            role="button"
            tabIndex={onClick ? 0 : -1}
            aria-label={ariaLabel || `${label} status: ${status}`}
            style={style}
        >
            {/* Status dot indicator */}
            <div
                className={clsx(
                    'w-2 h-2 rounded-full',
                    colors.dot,
                    animate && status === 'active' && 'animate-pulse'
                )}
                aria-hidden="true"
            />

            {/* Label */}
            <span className="text-sm font-medium truncate">{label}</span>

            {/* Optional count badge */}
            {count !== undefined && count > 0 && (
                <span
                    className={clsx(
                        'ml-1 px-1.5 py-0.5 rounded-full',
                        'text-xs font-semibold',
                        'bg-white dark:bg-slate-800',
                        colors.text
                    )}
                >
                    {count}
                </span>
            )}
        </div>
    );
};

export default DepartmentStatusBadge;
