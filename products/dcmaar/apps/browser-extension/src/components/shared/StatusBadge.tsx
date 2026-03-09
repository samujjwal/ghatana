/**
 * @fileoverview Status Badge Component
 *
 * Reusable badge for displaying access status (allowed/blocked/limited)
 * and content risk levels. Used across Popup, Dashboard, and Settings.
 */

import React from 'react';
import type { AccessStatus, ContentRiskLevel } from '../../types';

// ============================================================================
// Access Status Badge
// ============================================================================

export interface StatusBadgeProps {
    status: AccessStatus;
    size?: 'sm' | 'md';
    showIcon?: boolean;
}

const STATUS_STYLES: Record<AccessStatus, { bg: string; text: string; icon: string; label: string }> = {
    allowed: {
        bg: 'bg-green-100',
        text: 'text-green-700',
        icon: '✓',
        label: 'Allowed',
    },
    blocked: {
        bg: 'bg-red-100',
        text: 'text-red-700',
        icon: '✕',
        label: 'Blocked',
    },
    limited: {
        bg: 'bg-yellow-100',
        text: 'text-yellow-700',
        icon: '⏱',
        label: 'Limited',
    },
};

export function StatusBadge({ status, size = 'sm', showIcon = true }: StatusBadgeProps) {
    const style = STATUS_STYLES[status];
    const sizeClasses = size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-3 py-1 text-sm';

    return (
        <span className={`inline-flex items-center gap-1 rounded-full font-medium ${style.bg} ${style.text} ${sizeClasses}`}>
            {showIcon && <span>{style.icon}</span>}
            <span>{style.label}</span>
        </span>
    );
}

// ============================================================================
// Content Risk Badge
// ============================================================================

export interface ContentRiskBadgeProps {
    risk: ContentRiskLevel;
    size?: 'sm' | 'md';
}

const RISK_STYLES: Record<ContentRiskLevel, { bg: string; text: string; label: string }> = {
    none: {
        bg: 'bg-gray-100',
        text: 'text-gray-600',
        label: 'None',
    },
    some: {
        bg: 'bg-orange-100',
        text: 'text-orange-700',
        label: 'Some',
    },
    high: {
        bg: 'bg-red-100',
        text: 'text-red-700',
        label: 'High',
    },
};

export function ContentRiskBadge({ risk, size = 'sm' }: ContentRiskBadgeProps) {
    const style = RISK_STYLES[risk];
    const sizeClasses = size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-3 py-1 text-sm';

    return (
        <span className={`inline-flex items-center rounded-full font-medium ${style.bg} ${style.text} ${sizeClasses}`}>
            {style.label}
        </span>
    );
}

// ============================================================================
// Monitoring Status Indicator
// ============================================================================

export interface MonitoringStatusProps {
    active: boolean;
    size?: 'sm' | 'md';
    variant?: 'default' | 'onDark';
}

export function MonitoringStatus({ active, size = 'sm', variant = 'default' }: MonitoringStatusProps) {
    const dotSize = size === 'sm' ? 'h-2 w-2' : 'h-3 w-3';
    const textSize = size === 'sm' ? 'text-xs' : 'text-sm';
    const activeDot = variant === 'onDark' ? 'bg-green-300' : 'bg-green-500';
    const inactiveDot = variant === 'onDark' ? 'bg-gray-300' : 'bg-gray-400';
    const textColor = variant === 'onDark' ? 'text-blue-100' : 'text-gray-700';

    return (
        <div className="flex items-center gap-2">
            <div className={`rounded-full ${dotSize} ${active ? activeDot : inactiveDot}`} />
            <span className={`${textSize} ${textColor}`}>
                {active ? 'Monitoring Active' : 'Monitoring Inactive'}
            </span>
        </div>
    );
}
