/**
 * AgentAvatar Component
 *
 * Specialized Avatar component for AI agents with role-based styling.
 * Displays agent type, status, and optional capabilities badges.
 *
 * @example
 * <AgentAvatar role="orchestrator" agentId="agent-123" name="Orchestrator" />
 * <AgentAvatar role="task" status="active" name="Task Runner" size="lg" />
 *
 * @package @ghatana/ui
 */

import React, { CSSProperties } from 'react';
import { clsx } from 'clsx';

export interface AgentAvatarProps {
    /** Agent role: 'orchestrator' | 'task' | 'analyzer' | 'planner' | 'reviewer' | 'generic' */
    role?: 'orchestrator' | 'task' | 'analyzer' | 'planner' | 'reviewer' | 'generic';
    /** Agent status indicator: 'active' | 'idle' | 'error' | 'unknown' */
    status?: 'active' | 'idle' | 'error' | 'unknown';
    /** Agent name or identifier */
    name: string;
    /** Optional agent ID (shown in tooltip) */
    agentId?: string;
    /** Avatar size */
    size?: 'sm' | 'md' | 'lg' | 'xl';
    /** Optional image URL for custom avatar */
    imageUrl?: string;
    /** Optional background color override */
    backgroundColor?: string;
    /** Optional CSS class */
    className?: string;
    /** Optional inline styles */
    style?: CSSProperties;
    /** Show status indicator dot */
    showStatus?: boolean;
}

/**
 * Role-based avatar color scheme.
 * Uses design tokens for consistency.
 */
const ROLE_COLORS: Record<string, { bg: string; text: string; initial: string }> = {
    orchestrator: {
        bg: 'bg-blue-100 dark:bg-blue-900',
        text: 'text-blue-700 dark:text-blue-300',
        initial: 'O',
    },
    task: {
        bg: 'bg-green-100 dark:bg-green-900',
        text: 'text-green-700 dark:text-green-300',
        initial: 'T',
    },
    analyzer: {
        bg: 'bg-purple-100 dark:bg-purple-900',
        text: 'text-purple-700 dark:text-purple-300',
        initial: 'A',
    },
    planner: {
        bg: 'bg-amber-100 dark:bg-amber-900',
        text: 'text-amber-700 dark:text-amber-300',
        initial: 'P',
    },
    reviewer: {
        bg: 'bg-rose-100 dark:bg-rose-900',
        text: 'text-rose-700 dark:text-rose-300',
        initial: 'R',
    },
    generic: {
        bg: 'bg-slate-100 dark:bg-slate-900',
        text: 'text-slate-700 dark:text-slate-300',
        initial: 'A',
    },
};

/**
 * Status indicator colors.
 */
const STATUS_COLORS: Record<string, string> = {
    active: 'bg-green-500 border-green-600',
    idle: 'bg-gray-400 border-gray-500',
    error: 'bg-red-500 border-red-600',
    unknown: 'bg-slate-400 border-slate-500',
};

/**
 * Size configuration mapping.
 */
const SIZE_CONFIG = {
    sm: { container: 'h-8 w-8', text: 'text-xs', badge: 'h-1.5 w-1.5' },
    md: { container: 'h-10 w-10', text: 'text-sm', badge: 'h-2 w-2' },
    lg: { container: 'h-12 w-12', text: 'text-base', badge: 'h-2.5 w-2.5' },
    xl: { container: 'h-16 w-16', text: 'text-lg', badge: 'h-3 w-3' },
};

/**
 * AgentAvatar: Displays AI agent with role-based styling and status indicators.
 *
 * Features:
 * - Role-based color coding (Orchestrator, Task, Analyzer, etc.)
 * - Status indicator (active, idle, error)
 * - Optional custom image support
 * - Multiple sizes (sm, md, lg, xl)
 * - Full accessibility (ARIA labels)
 * - Dark mode support
 *
 * @param props Component props
 * @returns JSX element
 */
export const AgentAvatar: React.FC<AgentAvatarProps> = ({
    role = 'generic',
    status = 'unknown',
    name,
    agentId,
    size = 'md',
    imageUrl,
    backgroundColor,
    className,
    style,
    showStatus = true,
}) => {
    const roleConfig = ROLE_COLORS[role] || ROLE_COLORS.generic;
    const sizeConfig = SIZE_CONFIG[size];
    const initial = name.charAt(0).toUpperCase();

    const ariaLabel = agentId
        ? `${name} (${role} agent, ID: ${agentId})`
        : `${name} (${role} agent)`;

    return (
        <div
            className={clsx('relative inline-flex items-center justify-center', className)}
            style={style}
        >
            {/* Avatar container */}
            <div
                className={clsx(
                    'flex items-center justify-center rounded-full font-semibold',
                    'border-2 border-current transition-transform duration-200',
                    'hover:scale-110 focus-within:ring-2 focus-within:ring-offset-2',
                    sizeConfig.container,
                    imageUrl ? '' : roleConfig.bg,
                    imageUrl ? '' : roleConfig.text,
                    backgroundColor || ''
                )}
                role="img"
                aria-label={ariaLabel}
            >
                {imageUrl ? (
                    <img
                        src={imageUrl}
                        alt={name}
                        className="w-full h-full rounded-full object-cover"
                    />
                ) : (
                    <span className={sizeConfig.text}>{initial}</span>
                )}
            </div>

            {/* Status indicator dot */}
            {showStatus && (
                <div
                    className={clsx(
                        'absolute bottom-0 right-0 rounded-full border-2',
                        'transition-colors duration-200',
                        sizeConfig.badge,
                        STATUS_COLORS[status] || STATUS_COLORS.unknown
                    )}
                    aria-label={`Status: ${status}`}
                />
            )}
        </div>
    );
};

export default AgentAvatar;
