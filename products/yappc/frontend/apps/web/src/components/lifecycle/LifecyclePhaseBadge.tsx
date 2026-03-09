/**
 * Lifecycle Phase Badge Component
 * 
 * Displays the current lifecycle phase with color coding.
 * Used in project cards, headers, and navigation.
 * 
 * @doc.type component
 * @doc.purpose Lifecycle phase visual indicator
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React from 'react';
import {
    LifecyclePhase,
    PHASE_LABELS,
    PHASE_DESCRIPTIONS,
} from '../../types/lifecycle';

/**
 * Props for LifecyclePhaseBadge component
 */
export interface LifecyclePhaseBadgeProps {
    phase: LifecyclePhase;
    size?: 'sm' | 'md' | 'lg';
    showTooltip?: boolean;
    className?: string;
}

/**
 * Color mapping for each lifecycle phase
 */
const PHASE_COLORS: Record<LifecyclePhase, string> = {
    [LifecyclePhase.INTENT]: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
    [LifecyclePhase.SHAPE]: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    [LifecyclePhase.VALIDATE]: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
    [LifecyclePhase.GENERATE]: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300',
    [LifecyclePhase.RUN]: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    [LifecyclePhase.OBSERVE]: 'bg-gray-100 text-gray-700 dark:bg-gray-900/30 dark:text-gray-300',
    [LifecyclePhase.IMPROVE]: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300',
};

/**
 * Icon mapping for each lifecycle phase
 */
const PHASE_ICONS: Record<LifecyclePhase, string> = {
    [LifecyclePhase.INTENT]: '💡',
    [LifecyclePhase.SHAPE]: '🎨',
    [LifecyclePhase.VALIDATE]: '✓',
    [LifecyclePhase.GENERATE]: '⚙️',
    [LifecyclePhase.RUN]: '🚀',
    [LifecyclePhase.OBSERVE]: '👁️',
    [LifecyclePhase.IMPROVE]: '⚡',
};

/**
 * Size classes for badge
 */
const SIZE_CLASSES = {
    sm: 'px-2 py-0.5 text-xs',
    md: 'px-3 py-1 text-sm',
    lg: 'px-4 py-1.5 text-base',
};

/**
 * Lifecycle Phase Badge Component
 * 
 * Usage:
 * ```tsx
 * <LifecyclePhaseBadge phase={LifecyclePhase.SHAPE} size="md" />
 * ```
 */
export const LifecyclePhaseBadge: React.FC<LifecyclePhaseBadgeProps> = ({
    phase,
    size = 'md',
    showTooltip = true,
    className = '',
}) => {
    const colorClasses = PHASE_COLORS[phase];
    const sizeClasses = SIZE_CLASSES[size];
    const label = PHASE_LABELS[phase];
    const icon = PHASE_ICONS[phase];
    const description = PHASE_DESCRIPTIONS[phase];

    return (
        <span
            className={`inline-flex items-center gap-1.5 rounded-full font-medium ${colorClasses} ${sizeClasses} ${className}`}
            title={showTooltip ? description : undefined}
            role="status"
            aria-label={`Lifecycle phase: ${label}`}
        >
            <span aria-hidden="true">{icon}</span>
            <span>{label}</span>
        </span>
    );
};

/**
 * Compact version showing only icon (for tight spaces)
 */
export const LifecyclePhaseIcon: React.FC<{
    phase: LifecyclePhase;
    className?: string;
}> = ({ phase, className = '' }) => {
    const icon = PHASE_ICONS[phase];
    const label = PHASE_LABELS[phase];
    const description = PHASE_DESCRIPTIONS[phase];

    return (
        <span
            className={`inline-flex items-center justify-center ${className}`}
            title={`${label}: ${description}`}
            role="img"
            aria-label={`Lifecycle phase: ${label}`}
        >
            {icon}
        </span>
    );
};

export default LifecyclePhaseBadge;
